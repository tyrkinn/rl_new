(ns com.readlater.pages.archive
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [xtdb.api :as xt])
  (:import [java.time LocalDate ZoneOffset]))

(defn- activity-heatmap [db]
  (let [zone    ZoneOffset/UTC
        today   (LocalDate/now zone)
        start   (.minusDays today 364)
        dates   (->> (xt/q db '{:find [(pull ?e [:article/read-at])]
                                 :where [[?e :article/status :read]
                                         [?e :article/read-at _]]})
                     (map first)
                     (keep :article/read-at)
                     (map #(.toLocalDate (.atOffset % zone)))
                     (filter #(and (not (.isBefore % start)) (not (.isAfter % today))))
                     frequencies)
        max-ct  (apply max 1 (vals dates))
        dow     (.getValue (.getDayOfWeek start))
        g-start (.minusDays start (dec dow))
        all-days (take 371 (iterate #(.plusDays % 1) g-start))
        weeks   (partition 7 7 nil all-days)]
    [:div {:class "mb-10"}
     [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
      [:i {:data-lucide "activity" :class "icon-sm"}]
      "Reading activity · last year"]
     [:div {:class "overflow-x-auto pb-1"}
      [:div {:class "inline-flex" :style {:gap "3px"}}
       (for [week weeks]
         [:div {:class "flex flex-col" :style {:gap "3px"}}
          (for [day week]
            (let [valid? (and day (not (.isBefore day start)) (not (.isAfter day today)))
                  cnt    (if valid? (get dates day 0) -1)
                  color  (cond
                           (neg? cnt)   "transparent"
                           (zero? cnt)  "#EDE7DD"
                           :else        (let [r (/ cnt max-ct)]
                                          (cond
                                            (< r 0.25) "#C9A87C"
                                            (< r 0.5)  "#B47B53"
                                            (< r 0.75) "#8B5A3C"
                                            :else      "#553523")))]
              [:div {:class "w-3 h-3 rounded-sm"
                     :style {:background color}
                     :title (when valid? (str day ": " cnt " article" (when (not= cnt 1) "s")))}]))])]]]))

(defn archive-page [{:keys [biff/db]}]
  (let [articles (->> (xt/q db '{:find  [(pull ?e [*])]
                                 :where [[?e :article/status :read]]})
                      (map first)
                      (remove :article/deleted-at)
                      (sort-by :article/read-at #(compare %2 %1)))]
    (ui/page (merge (db/base-page-opts db) {:active :archive :title "Archive" :crumbs "Archive"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
              [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Archive"]
              [:p {:class "text-sm text-stone-500 mb-6"} "Articles you've read."]
              (activity-heatmap db)
              (if (empty? articles)
                [:p {:class "text-sm text-stone-400 mt-10 text-center"}
                 "No archived articles yet — mark articles as read to see them here."]
                [:div {:class "space-y-3"}
                 (for [{:keys [xt/id article/title article/url article/read-at
                               article/tldr article/tags article/reading-time-min]} articles]
                   [:div {:class "card-art p-5"}
                    [:div {:class "flex items-start justify-between gap-3"}
                     [:a {:href  (str "/article/" id)
                          :class "font-serif text-lg font-medium leading-snug hover:underline"}
                      (or title url)]
                     [:div {:class "flex items-center gap-2 shrink-0"}
                      (when read-at
                        [:span {:class "text-xs text-stone-400"} (c/fmt-read-date read-at)])
                      [:a {:href url :target "_blank" :rel "noopener noreferrer"
                           :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"}
                       [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
                    (when-let [summary (first tldr)]
                      [:p {:class "text-sm text-stone-600 mt-2 line-clamp-2"} summary])
                    (when (or reading-time-min (seq tags))
                      [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
                       (when reading-time-min
                         [:span {:class "chip"} (str reading-time-min " min")])
                       (for [tag (take 4 tags)]
                         [:span {:class "chip"} tag])])])])])))

(defn archive-article   [_ctx] {:status 501 :body {:error "not implemented"}})
(defn unarchive-article [_ctx] {:status 501 :body {:error "not implemented"}})

(def routes    [["/archive" {:get #'archive-page}]])
(def api-routes [["/api/articles/:id/archive"   {:post #'archive-article}]
                 ["/api/articles/:id/unarchive" {:post #'unarchive-article}]])
