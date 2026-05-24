(ns com.readlater.pages.week
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [xtdb.api :as xt])
  (:import [java.time Instant]))

(defn- week-day-label [^java.time.LocalDate ld ^java.time.LocalDate today]
  (let [days-ago (.between java.time.temporal.ChronoUnit/DAYS ld today)]
    (cond
      (zero? days-ago) "Today"
      (= 1 days-ago)   "Yesterday"
      :else            (.format ld (java.time.format.DateTimeFormatter/ofPattern "EEEE, MMM d")))))

(defn week-page [{:keys [biff/db]}]
  (let [zone     (java.time.ZoneId/systemDefault)
        today-ld (.toLocalDate (.atZone (Instant/now) zone))
        week-ago (.toInstant (.atStartOfDay (.minusDays today-ld 6) zone))
        articles (->> (xt/q db '{:find  [(pull ?e [*])]
                                 :where [[?e :article/added-at _]]})
                      (map first)
                      (remove :article/deleted-at)
                      (remove #(= :archived (:article/status %)))
                      (filter #(some-> (:article/added-at %) (.isAfter week-ago)))
                      (sort-by :article/added-at #(compare %2 %1)))
        total    (count articles)
        read-n   (count (filter #(= :read (:article/status %)) articles))
        ready-n  (count (filter #(= :ready (:article/status %)) articles))
        queued-n (count (filter #(#{:queued :enriching} (:article/status %)) articles))
        by-day   (->> articles
                      (group-by #(-> (:article/added-at %) (.atZone zone) .toLocalDate))
                      (sort-by first #(compare %2 %1)))]
    (ui/page (merge (db/base-page-opts db) {:active :week :title "This Week" :crumbs "This Week"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
              [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-6"} "This Week"]
              (if (empty? articles)
                [:div {:class "mt-20 flex flex-col items-center text-center gap-3"}
                 [:i {:data-lucide "calendar-days" :class "text-stone-300" :style {:width "44px" :height "44px"}}]
                 [:p {:class "font-medium text-stone-400"} "Nothing added this week"]
                 [:p {:class "text-sm text-stone-400"} "Save a URL and it'll appear here."]]
                [:<>
                 [:div {:class "flex rounded-xl overflow-hidden border border-stone-200 mb-8"}
                  (for [[n label color] [[total   "articles"  "text-stone-700"]
                                         [read-n   "read"      "text-emerald-700"]
                                         [ready-n  "in inbox"  "text-amber-700"]
                                         [queued-n "queued"    "text-blue-600"]]]
                    [:<>
                     [:div {:class "flex-1 flex flex-col items-center py-3 bg-stone-50 gap-0.5"}
                      [:span {:class (str "text-xl font-semibold tabular-nums " color)} (str n)]
                      [:span {:class "text-xs text-stone-400"} label]]
                     (when (not= n queued-n)
                       [:div {:class "w-px self-stretch bg-stone-200"}])])]
                 [:div {:class "space-y-6"}
                  (for [[ld day-arts] by-day]
                    [:section
                     [:div {:class "flex items-center gap-2 mb-1"}
                      [:span {:class "text-xs font-semibold uppercase tracking-wider text-stone-400"}
                       (week-day-label ld today-ld)]
                      [:span {:class "chip chip-mono"} (str (count day-arts))]
                      [:div {:class "flex-1 h-px bg-stone-200"}]]
                     [:div (map c/week-row day-arts)]])]])])))

(def routes    [["/week" {:get #'week-page}]])
(def api-routes [])
