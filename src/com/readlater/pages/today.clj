(ns com.readlater.pages.today
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [com.readlater.worker :as worker]
            [rum.core :as rum])
  (:import [java.time ZoneOffset LocalDate]))

(defn- today-date-str []
  (.format (java.time.LocalDate/now)
           (java.time.format.DateTimeFormatter/ofPattern "EEEE, d MMMM")))

(defn- articles-by-ids [db ids]
  (db/articles-by-ids db ids))

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

(defn- parse-today-filters [query-params]
  (let [quick? (= "true" (get query-params "quick-reads"))
        max-min (or (some-> (get query-params "max-min") parse-long) 5)]
    {:quick-reads? quick?
     :max-min      max-min}))

(defn- quick-reads-chip [quick? qr-count max-min]
  [:div {:id "inbox-filter-bar" :class "flex flex-wrap gap-2 mb-4"}
   (if (pos? qr-count)
     [:a {:href  (if quick? "/" (str "/?quick-reads=true" (when (not= max-min 5) (str "&max-min=" max-min))))
          :hx-push-url "true"
          :class (str "chip chip-sm cursor-pointer transition-colors "
                      (if quick?
                        "bg-stone-800 text-white border-stone-800"
                        "chip-outline text-stone-500 hover:border-stone-400"))}
      [:i {:data-lucide "zap" :class "icon-sm"}]
      "Quick Reads"
      (when-not quick?
        [:span {:class "ml-1 text-xs font-mono text-stone-400"} (str qr-count)])]
     [:span {:class "chip chip-sm cursor-not-allowed text-stone-300 border-stone-200"}
      [:i {:data-lucide "zap" :class "icon-sm"}]
      "Quick Reads"
      [:span {:class "ml-1 text-xs font-mono text-stone-300"} "0"]])])

(defn- surprise-empty-state []
  [:div {:id          "surprise-result"
         :hx-get      "/api/articles/random"
         :hx-trigger  "load delay:4000ms"
         :hx-target   "#surprise-result"
         :hx-swap     "outerHTML"
         :class       "flex items-center gap-2 text-sm text-stone-400"}
   [:i {:data-lucide "frown" :class "icon-sm"}]
   "No archived articles found. Read some first!"])

(defn- surprise-me-row []
  [:div {:class "px-4 sm:px-6 lg:px-10 mb-6"}
   [:div {:id "surprise-result"}
    [:button {:class      "btn btn-sm btn-outline gap-2 text-stone-600 border-stone-300 hover:bg-stone-50"
              :hx-get     "/api/articles/random"
              :hx-target  "#surprise-result"
              :hx-swap    "outerHTML"}
     [:i {:data-lucide "shuffle" :class "icon-sm"}]
     "Surprise Me"]]])

(defn- fresh-inbox-section [fresh inbox-n quick? qr-count max-min]
  (let [display-fresh (if quick?
                        (filter #(when-let [t (:article/reading-time-min %)]
                                   (<= t max-min))
                                fresh)
                        (take 10 fresh))]
    [:<>
     [:div {:class "border-t border-stone-200 mb-8"}]
     [:section {:class "px-4 sm:px-6 lg:px-10"}
      [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
       [:i {:data-lucide "inbox" :class "icon-sm"}]
       "Fresh in Inbox"]
      (quick-reads-chip quick? qr-count max-min)
      (when quick?
        [:p {:class "text-xs text-stone-400 mb-3"}
         (str "Showing " (count display-fresh) " quick read" (when (not= 1 (count display-fresh)) "s") " (≤ " max-min " min)")])
      [:div (map c/week-row display-fresh)]
      (when (and (not quick?) (> inbox-n 10))
        [:a {:href  "/inbox"
             :class "mt-4 inline-flex items-center gap-1.5 text-sm text-stone-500 hover:text-stone-700"}
         (str "View all " inbox-n " articles")
         [:i {:data-lucide "arrow-right" :class "icon-sm"}]])]]))

(defn today-page [{:keys [biff/db query-params] :as ctx}]
  (worker/start-generation-if-needed! ctx)
  (let [{:keys [quick-reads? max-min]} (parse-today-filters (or query-params {}))
        fresh-all (db/inbox-articles db)
        qr-count  (db/count-quick-reads db max-min)
        batch     (worker/today-batch db)
        inbox-n   (db/count-inbox db)]
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
               [:div {:class "flex items-center gap-1"}
                [:a {:href  "/today/history"
                     :class "btn btn-sm btn-ghost text-stone-400 gap-1.5 shrink-0 mt-1"}
                 [:i {:data-lucide "clock" :class "icon-sm"}]
                 [:span {:class "hidden sm:inline text-xs"} "History"]]
                [:button {:class               "btn btn-sm btn-ghost text-stone-400 gap-1.5 shrink-0 mt-1"
                          :title               "Regenerate"
                          :hx-post             "/api/recommendations/regenerate"
                          :hx-swap             "none"
                          :hx-on--after-request "window.location.reload()"}
                 [:i {:data-lucide "refresh-cw" :class "icon-sm"}]
                 [:span {:class "hidden sm:inline text-xs"} "Regenerate"]]]]
              [:section {:class "mb-10"}
               [:h2 {:class "px-4 sm:px-6 lg:px-10 text-xs font-semibold uppercase tracking-wider text-stone-400 mb-4 flex items-center gap-2"}
                [:i {:data-lucide "sparkles" :class "icon-sm"}]
                "Recommended Today"]
               (rec-section-content db)]
              (surprise-me-row)
              (when (seq fresh-all)
                (fresh-inbox-section fresh-all inbox-n quick-reads? qr-count max-min))]))))

;; ---------------------------------------------------------------------------
;; Today History page — Recommendation timeline with CTR per day

(defn- ctr-heatmap
  "Renders a 26-week CTR heatmap for the Recommendation history page.
   batches-with-ctr: seq of Rec batch maps annotated with :ctr, :ctr-read-count, :ctr-total-count"
  [batches-with-ctr]
  (let [zone    ZoneOffset/UTC
        today   (LocalDate/now zone)
        start   (.minusDays today 181)
        by-date (into {} (map (fn [b]
                                [(LocalDate/parse (:rec/date b)) b])
                              batches-with-ctr))
        dow     (.getValue (.getDayOfWeek start))
        g-start (.minusDays start (dec dow))
        all-days (take 196 (iterate #(.plusDays % 1) g-start))
        weeks   (partition 7 7 nil all-days)]
    [:div {:class "mb-10"}
     [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
      [:i {:data-lucide "bar-chart-2" :class "icon-sm"}]
      "Recommendation CTR · last 6 months"]
     [:div {:class "heat-grid"}
      (for [week weeks]
        [:div {:class "heat-week"}
         (for [day week]
           (let [valid?  (and day
                              (not (.isBefore day start))
                              (not (.isAfter day today)))
                 batch   (when valid? (get by-date day))
                 ctr     (:ctr batch)
                 read-n  (:ctr-read-count batch 0)
                 total-n (:ctr-total-count batch 0)
                 level   (cond
                           (not valid?)   "x"
                           (nil? batch)   "x"
                           (nil? ctr)     "x"
                           (zero? ctr)    "0"
                           :else          (let [r ctr]
                                            (cond
                                              (< r 0.25) "1"
                                              (< r 0.5)  "2"
                                              (< r 0.75) "3"
                                              :else      "4")))
                 tooltip (when valid?
                           (if batch
                             (str day " · " read-n "/" total-n " read"
                                  (when ctr (str " (" (int (* ctr 100)) "%)")))
                             (str day " · no batch")))]
             [:div {:class        "heat-cell"
                    :data-level  level
                    :data-tooltip tooltip
                    :title        tooltip}]))])]]))

(defn- ctr-pill
  "Renders a styled CTR percentage chip."
  [{:keys [ctr ctr-read-count ctr-total-count]}]
  (let [pct (when ctr (int (* ctr 100)))]
    [:span {:class (str "chip chip-mono "
                        (cond
                          (nil? ctr)    "text-stone-400"
                          (>= ctr 0.75) "text-emerald-700"
                          (>= ctr 0.5)  "text-amber-700"
                          (> ctr 0)     "text-orange-700"
                          :else         "text-stone-400"))}
     (if (nil? ctr)
       "—"
       (str pct "% · " ctr-read-count "/" ctr-total-count))]))

(defn- history-batch-row
  "Renders one Recommendation batch row in the history list."
  [{:keys [rec/date rec/collections] :as batch} arts-by-id]
  (let [lib-count (count (db/rec-batch-article-ids batch))
        ext-count (count (:rec/external-collections batch))]
    [:details {:class "card-art p-5 group"}
     [:summary {:class "flex items-center justify-between gap-3 cursor-pointer list-none"}
      [:div {:class "flex items-center gap-3"}
       [:i {:data-lucide "chevron-right"
            :class "icon-sm text-stone-400 transition-transform group-open:rotate-90"}]
       [:div
        [:span {:class "font-medium text-stone-800"} date]
        [:span {:class "text-xs text-stone-400 ml-2"}
         (str lib-count " library · " ext-count " discovery")]]]
      (ctr-pill batch)]
     [:div {:class "mt-4 pt-4 border-t border-stone-100 space-y-2"}
      (for [col collections
            {:keys [item/article-id item/micro-blurb]} (:collection/items col)
            :let [art (get arts-by-id article-id)]]
        [:div {:class "flex items-start gap-2"}
         (if (= :read (:article/status art))
           [:i {:data-lucide "check-circle-2" :class "icon-sm text-emerald-500 mt-0.5 shrink-0"}]
           [:i {:data-lucide "circle"         :class "icon-sm text-stone-300 mt-0.5 shrink-0"}])
         [:div
          [:a {:href  (str "/article/" article-id)
               :class "text-[13px] font-medium text-stone-800 hover:underline line-clamp-2"}
           (or (:article/title art) (str article-id))]
          (when (seq micro-blurb)
            [:p {:class "text-xs text-stone-400 italic mt-0.5"} micro-blurb])]])]]))

(defn today-history-page [{:keys [biff/db]}]
  (let [batches    (db/rec-batches-with-ctr db)
        all-ids    (->> batches
                        (mapcat db/rec-batch-article-ids)
                        distinct)
        arts-by-id (db/articles-by-ids db all-ids
                                       '[:xt/id :article/title :article/url :article/status])]
    (ui/page (merge (db/base-page-opts db)
                    {:active :today :title "Today History" :crumbs "Today / History"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
              ;; Header
              [:div {:class "flex items-center justify-between mb-6"}
               [:div
                [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-1"} "Recommendation History"]
                [:p {:class "text-sm text-stone-400"}
                 (str (count batches) " batches · CTR = reads within 7 days")]]
               [:a {:href "/" :class "btn btn-sm btn-ghost text-stone-400 gap-1.5"}
                [:i {:data-lucide "arrow-left" :class "icon-sm"}]
                "Today"]]
              ;; Heatmap
              (when (seq batches)
                (ctr-heatmap batches))
              ;; Summary stats
              (when (seq batches)
                (let [batches-with-ctr (filter #(some? (:ctr %)) batches)
                      avg-ctr          (when (seq batches-with-ctr)
                                         (/ (reduce + (map :ctr batches-with-ctr))
                                            (count batches-with-ctr)))]
                  [:div {:class "flex gap-4 mb-8 flex-wrap"}
                   [:div {:class "card-art px-4 py-3 flex-1 min-w-[120px]"}
                    [:p {:class "text-xs text-stone-400 mb-0.5"} "Total batches"]
                    [:p {:class "text-xl font-semibold"} (count batches)]]
                   [:div {:class "card-art px-4 py-3 flex-1 min-w-[120px]"}
                    [:p {:class "text-xs text-stone-400 mb-0.5"} "Avg CTR"]
                    [:p {:class "text-xl font-semibold font-mono"}
                     (if avg-ctr (str (int (* avg-ctr 100)) "%") "—")]]
                   [:div {:class "card-art px-4 py-3 flex-1 min-w-[120px]"}
                    [:p {:class "text-xs text-stone-400 mb-0.5"} "Total recommended"]
                    [:p {:class "text-xl font-semibold"}
                     (->> batches (map :ctr-total-count) (reduce + 0))]]]))
              ;; Batch list
              (if (empty? batches)
                [:p {:class "text-sm text-stone-400 mt-10 text-center"}
                 "No Recommendation history yet — batches appear here after your first daily generation."]
                [:div {:class "space-y-3"}
                 (for [batch batches]
                   (history-batch-row batch arts-by-id))])])))

(defn rec-fragment [{:keys [biff/db]}]
  (c/html-frag (rec-section-content db)))

(defn surprise-me-handler [{:keys [biff/db]}]
  (if-let [art (db/random-surprise db)]
    {:status  200
     :headers {"HX-Redirect" (str "/article/" (:xt/id art))}}
    {:status  200
     :headers {"Content-Type" "text/html; charset=UTF-8"}
     :body    (rum/render-static-markup (surprise-empty-state))}))

(def routes
  [["/"              {:get #'today-page}]
   ["/today/history" {:get #'today-history-page}]])

(def api-routes
  [["/api/recommendations/fragment" {:get #'rec-fragment}]
   ["/api/articles/random"          {:get #'surprise-me-handler}]])
