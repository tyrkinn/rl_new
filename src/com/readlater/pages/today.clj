(ns com.readlater.pages.today
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [com.readlater.worker :as worker]
            [xtdb.api :as xt]))

(defn- today-date-str []
  (.format (java.time.LocalDate/now)
           (java.time.format.DateTimeFormatter/ofPattern "EEEE, d MMMM")))

(defn- articles-by-ids [db ids]
  (->> ids
       (keep #(ffirst (xt/q db '{:find  [(pull ?e [:xt/id :article/title :article/url])]
                                 :in    [id]
                                 :where [[?e :xt/id id]]}
                            %)))
       (map (juxt :xt/id identity))
       (into {})))

(defn- collection-card
  [{:keys [collection/title collection/description collection/vibe collection/items]}
   arts-by-id]
  [:div {:class "card-art p-6 flex flex-col w-[82vw] sm:w-72 md:w-80 shrink-0 snap-start"}
   (when (seq vibe)
     [:span {:class "chip self-start mb-3"} vibe])
   [:h2 {:class "font-serif text-xl font-semibold leading-snug mb-2"} (or title "Collection")]
   [:p {:class "text-sm text-stone-500 leading-relaxed"} description]
   [:div {:class "mt-4 pt-4 border-t border-stone-100 space-y-3"}
    (for [{:keys [item/article-id item/micro-blurb]} items
          :let  [art (get arts-by-id article-id)]
          :when art]
      [:a {:href  (str "/article/" article-id)
           :class "group flex flex-col gap-0.5"}
       [:span {:class "text-[13px] font-medium leading-snug group-hover:underline text-stone-800 line-clamp-2"}
        (:article/title art)]
       (when (seq micro-blurb)
         [:span {:class "text-xs text-stone-400 italic line-clamp-1"} micro-blurb])])]])

(defn- rec-section-content [db]
  (let [batch (worker/today-batch db)
        state @worker/generation-state]
    (cond
      batch
      (let [all-ids      (->> (:rec/collections batch)
                              (mapcat :collection/items)
                              (map :item/article-id)
                              distinct)
            arts-by-id   (articles-by-ids db all-ids)
            external-cols (seq (:rec/external-collections batch))]
        [:div {:id "rec-section"}
         ;; Library collection section
         (when (seq (:rec/collections batch))
           [:<>
            [:h3 {:class "px-4 sm:px-6 lg:px-10 text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
             [:i {:data-lucide "library" :class "icon-sm"}]
             "From Your Library"]
            [:div {:class "flex gap-3 overflow-x-auto pb-4 snap-x snap-mandatory scroll-pl-4 sm:scroll-pl-6 lg:scroll-pl-10 px-4 sm:px-6 lg:px-10 mb-6"}
             (for [col (:rec/collections batch)]
               (collection-card col arts-by-id))
             [:div {:class "shrink-0 w-px"}]]])
         ;; External discovery sections
         (let [themed-cols  (remove #(#{:trending-repos :video-recs} (:type %)) external-cols)
               github-col   (first (filter #(= :trending-repos (:type %)) external-cols))
               video-col    (first (filter #(= :video-recs (:type %)) external-cols))]
           (when (or (seq themed-cols) github-col video-col)
             [:<>
              [:h3 {:class "px-4 sm:px-6 lg:px-10 text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2 mt-2"}
               [:i {:data-lucide "globe" :class "icon-sm"}]
               "Discover"]
              [:div {:class "px-4 sm:px-6 lg:px-10 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3"}
               (for [ext themed-cols]
                 (c/external-collection-card ext))
               (when github-col
                 (c/github-trending-card github-col))
               (when video-col
                 (c/video-recs-card video-col))]]))])

    (= :running (:status state))
    [:div {:id         "rec-section"
           :hx-get     "/api/recommendations/fragment"
           :hx-trigger "every 4s"
           :hx-target  "#rec-section"
           :hx-swap    "outerHTML"
           :class      "px-4 sm:px-6 lg:px-10"}
     [:div {:class "flex items-center gap-3 py-10 text-stone-400 text-sm"}
      [:span {:class "loading loading-spinner loading-sm"}]
      "Claude составляет подборки…"]]

    :else
    [:div {:id "rec-section" :class "px-4 sm:px-6 lg:px-10 py-6"}
     [:p {:class "text-sm text-stone-400"}
      "Добавь хотя бы 3 статьи в Inbox — Claude составит персональную подборку."]])))

(defn today-page [{:keys [biff/db] :as ctx}]
  (worker/start-generation-if-needed! ctx)
  (let [fresh   (take 10 (db/inbox-articles db))
        batch   (worker/today-batch db)
        inbox-n (db/count-inbox db)]
    (ui/page (merge (db/base-page-opts db) {:active :today :title "Today" :crumbs "Today"})
             [:div {:class "py-6 sm:py-8"}
              [:div {:class "px-4 sm:px-6 lg:px-10 mb-8 flex items-start justify-between gap-3"}
               [:div
                [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-1"} (today-date-str)]
                [:p {:class "text-sm text-stone-400"}
                 (if batch
                   (let [lib-n (count (:rec/collections batch))
                         ext-n (count (:rec/external-collections batch))]
                     (str lib-n (if (= lib-n 1) " library collection" " library collections")
                          (when (pos? ext-n)
                            (str " · " ext-n " discovery " (if (= ext-n 1) "section" "sections")))))
                   "Generating today's picks…")]]
               [:button {:class               "btn btn-sm btn-ghost text-stone-400 gap-1.5 shrink-0 mt-1"
                         :title               "Regenerate"
                         :hx-post             "/api/recommendations/regenerate"
                         :hx-swap             "none"
                         :hx-on--after-request "window.location.reload()"}
                [:i {:data-lucide "refresh-cw" :class "icon-sm"}]
                [:span {:class "hidden sm:inline text-xs"} "Regenerate"]]]
              [:section {:class "mb-10"}
               [:h2 {:class "px-4 sm:px-6 lg:px-10 text-xs font-semibold uppercase tracking-wider text-stone-400 mb-4 flex items-center gap-2"}
                [:i {:data-lucide "sparkles" :class "icon-sm"}]
                "Recommended Today"]
               (rec-section-content db)]
              (when (seq fresh)
                [:<>
                 [:div {:class "border-t border-stone-200 mb-8"}]
                 [:section {:class "px-4 sm:px-6 lg:px-10"}
                  [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
                   [:i {:data-lucide "inbox" :class "icon-sm"}]
                   "Fresh in Inbox"]
                  [:div (map c/week-row fresh)]
                  (when (> inbox-n 10)
                    [:a {:href  "/inbox"
                         :class "mt-4 inline-flex items-center gap-1.5 text-sm text-stone-500 hover:text-stone-700"}
                     (str "View all " inbox-n " articles")
                     [:i {:data-lucide "arrow-right" :class "icon-sm"}]])]])])))

(defn today-history-page [{:keys [biff/db]}]
  (ui/page (merge (db/base-page-opts db) {:active :today :title "Today History" :crumbs "Today / History"})
           [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
            [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Today History"]]))

(defn rec-fragment [{:keys [biff/db]}]
  (c/html-frag (rec-section-content db)))

(def routes
  [["/"              {:get #'today-page}]
   ["/today/history" {:get #'today-history-page}]])

(def api-routes
  [["/api/recommendations/fragment" {:get #'rec-fragment}]])
