(ns com.readlater.pages.queue
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [xtdb.api :as xt])
  (:import [java.time Instant]))

(defn- queue-body [db]
  (let [all        (->> (xt/q db '{:find  [(pull ?e [*])]
                                   :where [[?e :article/status _]]})
                        (map first)
                        (filter #(#{:queued :enriching :failed} (:article/status %)))
                        (remove :article/deleted-at)
                        (sort-by :article/added-at #(compare %2 %1)))
        by-st      (group-by :article/status all)
        enriching  (get by-st :enriching [])
        queued     (get by-st :queued [])
        failed     (get by-st :failed [])
        summarizing (->> (xt/q db '{:find  [(pull ?e [:xt/id :article/url :article/title])]
                                    :where [[?e :article/summary-status :pending]]})
                         (map first))]
    [:div {:id "queue-body" :hx-get "/api/queue/fragment" :hx-trigger "every 4s" :hx-swap "outerHTML"}
     (when (seq enriching)
       [:section {:class "mb-8"}
        [:h2 {:class "text-sm font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
         [:span {:class "w-2 h-2 rounded-full bg-blue-400 animate-pulse"}]
         (str "Enriching now · " (count enriching))]
        [:div {:class "space-y-2"}
         (for [{:keys [xt/id article/url article/title]} enriching]
           [:div {:class "card-art px-4 py-3 flex items-center gap-3"}
            [:span {:class "loading loading-spinner loading-xs text-blue-500"}]
            [:a {:href (str "/article/" id) :class "text-sm hover:underline truncate"} (or title url)]])]])
     (when (seq summarizing)
       [:section {:class "mb-8"}
        [:h2 {:class "text-sm font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
         [:span {:class "w-2 h-2 rounded-full bg-violet-400 animate-pulse"}]
         (str "Summarizing · " (count summarizing))]
        [:div {:class "space-y-2"}
         (for [{:keys [xt/id article/url article/title]} summarizing]
           [:div {:class "card-art px-4 py-3 flex items-center gap-3"}
            [:span {:class "loading loading-spinner loading-xs text-violet-500"}]
            [:a {:href (str "/article/" id) :class "text-sm hover:underline truncate"} (or title url)]])]])
     (when (seq queued)
       [:section {:class "mb-8"}
        [:h2 {:class "text-sm font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
         [:span {:class "w-2 h-2 rounded-full bg-amber-400"}]
         (str "Pending · " (count queued))]
        [:div {:class "space-y-2"}
         (for [{:keys [xt/id article/url article/title]} queued]
           [:div {:class "card-art px-4 py-3 flex items-center gap-3"}
            [:span {:class "w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"}]
            [:a {:href (str "/article/" id) :class "text-sm hover:underline truncate"} (or title url)]])]])
     (when (seq failed)
       [:section {:class "mb-8"}
        [:h2 {:class "text-sm font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
         [:span {:class "w-2 h-2 rounded-full bg-red-400"}]
         (str "Failed · " (count failed))]
        [:div {:class "space-y-2"}
         (for [{:keys [xt/id article/url article/title article/error article/retry-count]} failed]
           [:div {:class "card-art px-4 py-3 flex items-center gap-3"}
            [:span {:class "w-1.5 h-1.5 rounded-full bg-red-400"}]
            [:div {:class "flex-1 min-w-0"}
             [:a {:href (str "/article/" id) :class "text-sm hover:underline truncate block"} (or title url)]
             (when error [:p {:class "text-xs text-stone-400 truncate"} error])]
            [:span {:class "chip chip-mono text-xs shrink-0"} (str "attempt " (or retry-count 0))]
            [:button {:hx-post              (str "/api/articles/" id "/reenrich")
                      :hx-swap              "none"
                      :hx-on--after-request "window.location.reload()"
                      :class                "btn btn-xs btn-ghost text-stone-500 shrink-0"}
             "Retry"]])]])
     (when (and (empty? enriching) (empty? summarizing) (empty? queued) (empty? failed))
       [:p {:class "text-sm text-stone-400 mt-10 text-center"}
        "Queue is empty — all articles are processed."])]))

(defn queue-page [{:keys [biff/db]}]
  (ui/page (merge (db/base-page-opts db) {:active :queue :title "Queue" :crumbs "Queue"})
           [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
            [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Queue"]
            [:p {:class "text-sm text-stone-500 mb-6"} "Enrichment pipeline status."]
            (queue-body db)]))

(defn queue-fragment [{:keys [biff/db]}]
  (c/html-frag (queue-body db)))

(defn queue-stats [{:keys [biff/db]}]
  (let [counts (->> (xt/q db '{:find  [?s (count ?e)]
                               :where [[?e :article/status ?s]
                                       [(contains? #{:queued :enriching :failed} ?s)]]})
                    (into {}))]
    {:status 200
     :body   {:queued    (get counts :queued 0)
              :enriching (get counts :enriching 0)
              :failed    (get counts :failed 0)}}))

(defn recent-ready [{:keys [biff/db params]}]
  (let [since-ms (some-> (:since params) (#(try (Long/parseLong %) (catch Exception _ nil))))
        cutoff   (if since-ms
                   (Instant/ofEpochMilli since-ms)
                   (.minusSeconds (db/now) 10))
        articles (->> (xt/q db '{:find  [(pull ?e [:xt/id :article/title :article/url :article/enriched-at])]
                                 :where [[?e :article/enriched-at _]]})
                      (map first)
                      (filter #(some-> (:article/enriched-at %) (.isAfter cutoff)))
                      (sort-by :article/enriched-at #(compare %2 %1))
                      (take 5))]
    {:status 200
     :body   {:articles (mapv (fn [a] {:id    (str (:xt/id a))
                                       :title (or (:article/title a) (:article/url a))})
                              articles)}}))

(def routes    [["/queue" {:get #'queue-page}]])
(def api-routes [["/api/queue"               {:get #'queue-stats}]
                 ["/api/queue/fragment"      {:get #'queue-fragment}]
                 ["/api/events/recent-ready" {:get #'recent-ready}]])
