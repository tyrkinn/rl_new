(ns com.readlater.pages.notifications
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.biffweb :as biff]
            [xtdb.api :as xt]))

(defn- notif-icon [type]
  (case type
    :success [:i {:data-lucide "check-circle-2" :class "flex-shrink-0 mt-0.5" :style {:width "17px" :height "17px" :color "#10b981"}}]
    :error   [:i {:data-lucide "x-circle"       :class "flex-shrink-0 mt-0.5" :style {:width "17px" :height "17px" :color "#ef4444"}}]
    :warning [:i {:data-lucide "alert-triangle"  :class "flex-shrink-0 mt-0.5" :style {:width "17px" :height "17px" :color "#f59e0b"}}]
    :info    [:i {:data-lucide "info"             :class "flex-shrink-0 mt-0.5" :style {:width "17px" :height "17px" :color "#3b82f6"}}]
    [:i {:data-lucide "bell" :class "flex-shrink-0 mt-0.5" :style {:width "17px" :height "17px" :color "#9ca3af"}}]))

(defn- notif-age [inst]
  (when inst
    (let [diff-s (/ (- (System/currentTimeMillis) (.toEpochMilli inst)) 1000)]
      (cond
        (< diff-s 60)    "just now"
        (< diff-s 3600)  (str (int (/ diff-s 60)) "m ago")
        (< diff-s 86400) (str (int (/ diff-s 3600)) "h ago")
        :else            (str (int (/ diff-s 86400)) "d ago")))))

(defn- notif-dot-class [type]
  (case type
    :success "bg-emerald-500"
    :error   "bg-red-500"
    :warning "bg-amber-500"
    :info    "bg-blue-500"
    "bg-stone-400"))

(defn- notif-item [{:keys [xt/id notif/type notif/title notif/body notif/link notif/read notif/created-at]}]
  [:div {:class (str "flex gap-3 px-4 py-3.5 hover:bg-stone-50 transition-colors cursor-default border-b border-stone-100/80 last:border-0"
                     (when-not read " bg-[#FDFAF7]"))}
   (notif-icon type)
   [:div {:class "flex-1 min-w-0"}
    [:div {:class "flex items-start justify-between gap-2 mb-0.5"}
     [:p {:class (str "text-[13px] leading-snug text-stone-800 truncate" (when-not read " font-medium"))} title]
     [:span {:class "text-[11px] text-stone-400 whitespace-nowrap shrink-0 mt-px"} (notif-age created-at)]]
    (when body [:p {:class "text-[12px] text-stone-500 leading-snug line-clamp-2"} body])
    (when link
      [:a {:href link :class "text-[12px] text-accent-500 hover:underline mt-1 inline-block font-medium"
           :onclick "notifClose()"}
       "Open article →"])]
   [:div {:class "flex items-start gap-0.5 shrink-0"}
    (when-not read [:span {:class (str "w-1.5 h-1.5 rounded-full mt-1.5 " (notif-dot-class type))}])
    [:button {:class "w-5 h-5 rounded flex items-center justify-center text-stone-300 hover:text-stone-500 hover:bg-stone-100 transition-colors"
              :hx-delete (str "/api/notifications/item/" id)
              :hx-swap   "outerHTML"
              :hx-target "closest .notif-item-wrap"}
     [:i {:data-lucide "x" :class "icon-sm"}]]]])

(defn- drawer-content [notifs]
  [:div {:id "notif-drawer-inner" :class "flex flex-col h-full"}
   [:div {:class "flex items-center justify-between px-4 py-3.5 border-b border-stone-100"}
    [:div {:class "flex items-center gap-2"}
     [:i {:data-lucide "bell" :class "icon-md text-stone-600"}]
     [:h2 {:class "font-semibold text-stone-900 text-[15px]"} "Notifications"]
     (when-let [n (seq (filter #(not (:notif/read %)) notifs))]
       [:span {:class "chip chip-mono text-[10px]"} (count n)])]
    [:div {:class "flex items-center gap-1"}
     (when (some #(not (:notif/read %)) notifs)
       [:button {:class    "btn btn-xs btn-ghost text-stone-500 gap-1 text-[11px]"
                 :hx-post  "/api/notifications/read-all"
                 :hx-target "#notif-drawer-inner"
                 :hx-swap  "outerHTML"}
        [:i {:data-lucide "check-check" :class "icon-sm"}]
        "Mark all read"])
     [:button {:class "btn btn-sm btn-ghost btn-square text-stone-400 ml-0.5"
               :onclick "notifClose()"}
      [:i {:data-lucide "x" :class "icon-md"}]]]]
   (if (empty? notifs)
     [:div {:class "flex flex-col items-center justify-center flex-1 px-6 text-center"}
      [:div {:class "w-16 h-16 rounded-2xl bg-stone-100 flex items-center justify-center mb-4"}
       [:i {:data-lucide "bell-off" :style {:width "28px" :height "28px" :color "#a8a29e"}}]]
      [:p {:class "text-sm font-medium text-stone-700 mb-1"} "All clear"]
      [:p {:class "text-xs text-stone-400 leading-relaxed"} "Notifications about enriched articles and errors will appear here."]]
     [:div {:class "overflow-y-auto flex-1"}
      (map (fn [n] [:div {:class "notif-item-wrap"} (notif-item n)]) notifs)])])

(defn drawer-fragment [{:keys [biff/db]}]
  (c/html-frag (drawer-content (db/recent-notifications db))))

(defn mark-all-read [{:keys [biff/db] :as ctx}]
  (let [unread (->> (xt/q db '{:find [(pull ?e [:xt/id])]
                               :where [[?e :notif/type _]
                                       [?e :notif/read false]]})
                    (map first))]
    (doseq [{:keys [xt/id]} unread]
      (biff/submit-tx ctx [{:db/op :update :db/doc-type :notification
                            :xt/id id :notif/read true}])))
  (c/html-frag (drawer-content (db/recent-notifications (:biff/db ctx)))))

(defn dismiss-notif [{:keys [path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (when id
      (biff/submit-tx ctx [[:xtdb.api/delete id]])))
  {:status 200 :body ""})

(def routes    [])
(def api-routes
  [["/api/notifications/drawer"    {:get    #'drawer-fragment}]
   ["/api/notifications/read-all"  {:post   #'mark-all-read}]
   ["/api/notifications/item/:id"  {:delete #'dismiss-notif}]])
