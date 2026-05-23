(ns com.readlater.app
  (:require [com.biffweb :as biff]
            [com.readlater.ui :as ui]
            [com.readlater.url :as url]
            [com.readlater.search :as search]
            [com.readlater.worker :as worker]
            [xtdb.api :as xt]
            [rum.core :as rum]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]
           [java.time Instant]))

(defn- now [] (Instant/now))

(defn- html-frag [hiccup]
  {:status  200
   :headers {"content-type" "text/html; charset=UTF-8"}
   :body    (rum/render-static-markup hiccup)})

(defn- fmt-comment-time [^java.time.Instant t]
  (when t
    (.format (.atOffset t java.time.ZoneOffset/UTC)
             (java.time.format.DateTimeFormatter/ofPattern "MMM d, HH:mm"))))

(defn- comment-bubble [{:keys [text created-at]}]
  [:div {:class "group flex flex-col gap-1"}
   [:div {:class "bg-white border border-stone-200 rounded-2xl rounded-tl-sm px-4 py-3 shadow-soft"}
    [:p {:class "text-sm text-stone-800 leading-relaxed whitespace-pre-wrap"} text]]
   [:span {:class "text-xs text-stone-400 pl-1"} (fmt-comment-time created-at)]])

;; ---------------------------------------------------------------------------
;; Inbox components

(defn- status-badge [status]
  (case status
    :queued    [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200"}
                [:span {:class "w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"}]
                "pending"]
    :enriching [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200"}
                [:span {:class "loading loading-spinner loading-xs"}]
                "enriching"]
    nil))

(defn- article-card [{:keys [xt/id article/title article/url article/status
                             article/added-at article/tldr article/tags
                             article/reading-time-min]}]
  (let [pending?      (#{:queued :enriching} status)
        display-title (or title url)]
    [:div {:class "card-art p-5"}
     [:div {:class "flex items-start justify-between gap-3"}
      [:div {:class "flex-1 min-w-0"}
       [:a {:href  (str "/article/" id)
            :class (str "font-serif text-lg font-medium leading-snug hover:underline"
                        (when pending? " text-stone-400"))}
        display-title]
       (when (and pending? (nil? title))
         [:p {:class "text-xs text-stone-400 mt-0.5 truncate"} url])]
      [:div {:class "flex items-center gap-1.5 shrink-0"}
       (status-badge status)
       [:a {:href url :target "_blank" :rel "noopener noreferrer"
            :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"
            :title "Open original"}
        [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
     (when-let [summary (first tldr)]
       [:p {:class "text-sm text-stone-600 mt-2 line-clamp-2"} summary])
     (when (or reading-time-min (seq tags))
       [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
        (when reading-time-min
          [:span {:class "chip"} (str reading-time-min " min")])
        (for [tag (take 4 tags)]
          [:span {:class "chip"} tag])])]))

;; ---------------------------------------------------------------------------
;; Pages

;; forward-declare functions used before their definition
(declare week-row inbox-articles count-inbox)

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
  [:div {:class "card-art p-6 flex flex-col w-72 md:w-80 shrink-0 snap-start"}
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
      (let [all-ids    (->> (:rec/collections batch)
                            (mapcat :collection/items)
                            (map :item/article-id)
                            distinct)
            arts-by-id (articles-by-ids db all-ids)]
        [:div {:id "rec-section"}
         [:div {:class "flex gap-4 overflow-x-auto pb-4 snap-x snap-mandatory"}
          (for [col (:rec/collections batch)]
            (collection-card col arts-by-id))]])

      (= :running (:status state))
      [:div {:id          "rec-section"
             :hx-get      "/api/recommendations/fragment"
             :hx-trigger  "every 4s"
             :hx-target   "#rec-section"
             :hx-swap     "outerHTML"
             :class       "flex items-center gap-3 py-10 text-stone-400 text-sm"}
       [:span {:class "loading loading-spinner loading-sm"}]
       "Claude составляет подборки…"]

      :else
      [:div {:id "rec-section" :class "py-6"}
       [:p {:class "text-sm text-stone-400"}
        "Добавь хотя бы 3 статьи в Inbox — Claude составит персональную подборку."]])))

(defn today-page [{:keys [biff/db] :as ctx}]
  (worker/start-generation-if-needed! ctx)
  (let [inbox-n (count-inbox db)
        fresh   (take 10 (inbox-articles db))
        batch   (worker/today-batch db)]
    (ui/page {:active :today :title "Today" :crumbs "Today" :inbox-count inbox-n}
             [:div {:class "px-6 lg:px-10 py-8 max-w-5xl"}
              [:div {:class "mb-8"}
               [:h1 {:class "serif-h1 text-4xl mb-1"} (today-date-str)]
               [:p {:class "text-sm text-stone-400"}
                (if batch
                  (let [n (count (:rec/collections batch))]
                    (str n (if (= n 1) " collection" " collections") " for today"))
                  "Generating today's picks…")]]
              [:section {:class "mb-10"}
               [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-4 flex items-center gap-2"}
                [:i {:data-lucide "sparkles" :class "icon-sm"}]
                "Recommended Today"]
               (rec-section-content db)]
              (when (seq fresh)
                [:<>
                 [:div {:class "border-t border-stone-200 mb-8"}]
                 [:section
                  [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
                   [:i {:data-lucide "inbox" :class "icon-sm"}]
                   "Fresh in Inbox"]
                  [:div (map week-row fresh)]
                  (when (> inbox-n 10)
                    [:a {:href  "/inbox"
                         :class "mt-4 inline-flex items-center gap-1.5 text-sm text-stone-500 hover:text-stone-700"}
                     (str "View all " inbox-n " articles")
                     [:i {:data-lucide "arrow-right" :class "icon-sm"}]])]])])))

(def ^:private inbox-statuses #{:ready})

(defn- inbox-articles [db]
  (->> (xt/q db '{:find  [(pull ?e [*])]
                  :where [[?e :article/status _]]})
       (map first)
       (filter #(inbox-statuses (:article/status %)))
       (remove :article/deleted-at)
       (sort-by :article/added-at #(compare %2 %1))))

(defn- count-inbox [db]
  (count (inbox-articles db)))

(defn inbox-page [{:keys [biff/db]}]
  (let [articles (inbox-articles db)]
    (ui/page {:active :inbox :title "Inbox" :crumbs "Inbox" :inbox-count (count articles)}
             [:div {:class "px-6 lg:px-10 py-8 max-w-3xl"}
              [:h1 {:class "serif-h1 text-4xl mb-2"} "Inbox"]
              [:p {:class "text-sm text-stone-500 mb-6"} "Articles awaiting review."]
              (if (empty? articles)
                [:p {:class "text-sm text-stone-400 mt-10 text-center"} "No articles yet — add a URL to get started."]
                [:div {:class "space-y-3"}
                 (map article-card articles)])])))

;; ---------------------------------------------------------------------------
;; Week page helpers

(defn- week-day-label [^java.time.LocalDate ld ^java.time.LocalDate today]
  (let [days-ago (.between java.time.temporal.ChronoUnit/DAYS ld today)]
    (cond
      (zero? days-ago) "Today"
      (= 1 days-ago)   "Yesterday"
      :else            (.format ld (java.time.format.DateTimeFormatter/ofPattern "EEEE, MMM d")))))

(defn- week-status-dot [status]
  [:span {:class (str "w-2 h-2 rounded-full shrink-0 mt-1 "
                      (case status
                        :ready              "bg-emerald-400"
                        :read               "bg-stone-300"
                        :queued             "bg-amber-400 animate-pulse"
                        :enriching          "bg-blue-400 animate-pulse"
                        :failed             "bg-red-400"
                        (:paywall
                         :notfound
                         :login-required)   "bg-orange-400"
                        "bg-stone-300"))}])

(defn- week-row [{:keys [xt/id article/title article/url article/status
                         article/tags article/reading-time-min]}]
  (let [row-id (str "wr-" id)]
    [:div {:id    row-id
           :class "group flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-stone-50 -mx-3 transition-colors"}
     (week-status-dot status)
     [:a {:href  (str "/article/" id)
          :class "flex-1 min-w-0 font-serif text-[15px] leading-snug truncate hover:underline text-stone-800"}
      (or title url)]
     (when (seq tags)
       [:div {:class "hidden sm:flex items-center gap-1 shrink-0"}
        (for [tag (take 2 tags)]
          [:span {:class "chip"} tag])])
     (when reading-time-min
       [:span {:class "chip chip-mono shrink-0"} (str reading-time-min "m")])
     (when (not= status :read)
       [:button {:class              "opacity-0 group-hover:opacity-100 btn btn-xs btn-ghost text-emerald-600 gap-1 shrink-0 transition-opacity"
                 :title              "Mark as read"
                 :hx-post            (str "/api/articles/" id "/read")
                 :hx-swap            "none"
                 :hx-on--after-request (str "var e=document.getElementById('" row-id "');"
                                            "e.style.transition='opacity .25s';"
                                            "e.style.opacity='0';"
                                            "setTimeout(function(){e.remove()},260)")}
        [:i {:data-lucide "check" :class "icon-sm"}]])
     [:a {:href  url :target "_blank" :rel "noopener noreferrer"
          :title "Open original"
          :class "opacity-0 group-hover:opacity-100 btn btn-xs btn-ghost text-stone-400 px-1 shrink-0 transition-opacity"}
      [:i {:data-lucide "external-link" :class "icon-sm"}]]]))

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
        read-n   (count (filter #(= :read  (:article/status %)) articles))
        ready-n  (count (filter #(= :ready (:article/status %)) articles))
        queue-n  (count (filter #(#{:queued :enriching} (:article/status %)) articles))
        inbox-n  (count-inbox db)
        by-day   (->> articles
                      (group-by #(-> (:article/added-at %)
                                     (.atZone zone)
                                     .toLocalDate))
                      (sort-by first #(compare %2 %1)))]
    (ui/page {:active :week :title "This Week" :crumbs "This Week" :inbox-count inbox-n}
             [:div {:class "px-6 lg:px-10 py-8 max-w-3xl"}
              [:h1 {:class "serif-h1 text-4xl mb-6"} "This Week"]
              (if (empty? articles)
                [:div {:class "mt-20 flex flex-col items-center text-center gap-3"}
                 [:i {:data-lucide "calendar-days"
                      :class "text-stone-300"
                      :style {:width "44px" :height "44px"}}]
                 [:p {:class "font-medium text-stone-400"} "Nothing added this week"]
                 [:p {:class "text-sm text-stone-400"} "Save a URL and it'll appear here."]]
                [:<>
                 ;; Stats strip
                 [:div {:class "flex rounded-xl overflow-hidden border border-stone-200 mb-8"}
                  (for [[n label color] [[total  "articles"  "text-stone-700"]
                                         [read-n  "read"      "text-emerald-700"]
                                         [ready-n "in inbox"  "text-amber-700"]
                                         [queue-n "queued"    "text-blue-600"]]]
                    [:<>
                     [:div {:class "flex-1 flex flex-col items-center py-3 bg-stone-50 gap-0.5"}
                      [:span {:class (str "text-xl font-semibold tabular-nums " color)} (str n)]
                      [:span {:class "text-xs text-stone-400"} label]]
                     (when (not= n queue-n)
                       [:div {:class "w-px self-stretch bg-stone-200"}])])]
                 ;; Day groups
                 [:div {:class "space-y-6"}
                  (for [[ld day-arts] by-day]
                    [:section
                     [:div {:class "flex items-center gap-2 mb-1"}
                      [:span {:class "text-xs font-semibold uppercase tracking-wider text-stone-400"}
                       (week-day-label ld today-ld)]
                      [:span {:class "chip chip-mono"} (str (count day-arts))]
                      [:div {:class "flex-1 h-px bg-stone-200"}]]
                     [:div (map week-row day-arts)]])]])])))

(defn tags-page [{:keys [biff/db]}]
  (let [inbox-n    (count-inbox db)
        tag-groups (->> (xt/q db '{:find  [(pull ?e [:xt/id :article/status :article/deleted-at :article/tags])]
                                   :where [[?e :article/status _]]})
                        (map first)
                        (remove :article/deleted-at)
                        (filter #(seq (:article/tags %)))
                        (mapcat (fn [art] (map (fn [tag] [tag art]) (:article/tags art))))
                        (group-by first)
                        (map (fn [[tag pairs]] {:tag tag :count (count pairs)}))
                        (sort-by :count >))
        max-n      (apply max 1 (map :count tag-groups))
        min-n      (apply min max-n (map :count tag-groups))]
    (ui/page {:active :tags :title "Tags" :crumbs "Tags" :inbox-count inbox-n}
             [:div {:class "px-6 lg:px-10 py-8 max-w-4xl"}
              [:div {:class "flex items-baseline gap-3 mb-8"}
               [:h1 {:class "serif-h1 text-4xl"} "Tags"]
               (when (seq tag-groups)
                 [:span {:class "text-sm text-stone-400"} (str (count tag-groups) " tags")])]
              (if (empty? tag-groups)
                [:p {:class "text-sm text-stone-400 mt-10 text-center"}
                 "No tags yet — articles get tagged automatically after enrichment."]
                [:<>
                 [:div {:class "flex flex-wrap gap-3 p-6 rounded-2xl bg-stone-50 border border-stone-200"}
                  (for [{:keys [tag count]} tag-groups]
                    (let [ratio   (if (= max-n min-n) 0.5 (/ (double (- count min-n)) (- max-n min-n)))
                          font-px (int (+ 13 (* ratio 16)))
                          enc     (java.net.URLEncoder/encode ^String tag "UTF-8")]
                      [:button {:class   "tag-pill"
                                :hx-get  (str "/api/tags/" enc)
                                :hx-target "#tag-articles"
                                :hx-swap "innerHTML"
                                :onclick "document.querySelectorAll('.tag-pill').forEach(function(e){e.classList.remove('active')});this.classList.add('active')"
                                :style   {:font-size (str font-px "px")}}
                       tag
                       [:span {:class "tag-badge"} (str count)]]))]
                 [:div {:id "tag-articles" :class "mt-8"}]])])))

(defn tag-articles [{:keys [biff/db path-params]}]
  (let [tag      (some-> (:tag path-params)
                         (java.net.URLDecoder/decode "UTF-8"))
        articles (->> (xt/q db '{:find  [(pull ?e [*])]
                                 :where [[?e :article/status _]]})
                      (map first)
                      (remove :article/deleted-at)
                      (filter #(some #{tag} (:article/tags %)))
                      (sort-by :article/added-at #(compare %2 %1)))]
    (html-frag
     [:div
      [:div {:class "flex items-center gap-2 mb-5"}
       [:h2 {:class "font-serif text-2xl font-semibold"} tag]
       [:span {:class "chip chip-mono"} (str (count articles) " articles")]]
      (if (empty? articles)
        [:p {:class "text-sm text-stone-400"} "No articles with this tag."]
        [:div {:class "space-y-3"}
         (map article-card articles)])])))

(defn- fmt-read-date [^java.time.Instant t]
  (when t
    (.format (.atOffset t java.time.ZoneOffset/UTC)
             (java.time.format.DateTimeFormatter/ofPattern "MMM d, yyyy"))))

(defn archive-page [{:keys [biff/db]}]
  (let [articles (->> (xt/q db '{:find  [(pull ?e [*])]
                                 :where [[?e :article/read-at _]]})
                      (map first)
                      (remove :article/deleted-at)
                      (sort-by :article/read-at #(compare %2 %1)))]
    (ui/page {:active :archive :title "Archive" :crumbs "Archive" :inbox-count (count-inbox db)}
             [:div {:class "px-6 lg:px-10 py-8 max-w-3xl"}
              [:h1 {:class "serif-h1 text-4xl mb-2"} "Archive"]
              [:p {:class "text-sm text-stone-500 mb-6"} "Articles you've read."]
              (if (empty? articles)
                [:p {:class "text-sm text-stone-400 mt-10 text-center"} "No archived articles yet — mark articles as read to see them here."]
                [:div {:class "space-y-3"}
                 (for [{:keys [xt/id article/title article/url article/read-at
                               article/tldr article/tags article/reading-time-min] :as art} articles]
                   [:div {:class "card-art p-5"}
                    [:div {:class "flex items-start justify-between gap-3"}
                     [:a {:href  (str "/article/" id)
                          :class "font-serif text-lg font-medium leading-snug hover:underline"}
                      (or title url)]
                     [:div {:class "flex items-center gap-2 shrink-0"}
                      (when read-at [:span {:class "text-xs text-stone-400"} (fmt-read-date read-at)])
                      [:a {:href url :target "_blank" :rel "noopener noreferrer"
                           :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"}
                       [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
                    (when-let [summary (first tldr)]
                      [:p {:class "text-sm text-stone-600 mt-2 line-clamp-2"} summary])
                    (when (or reading-time-min (seq tags))
                      [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
                       (when reading-time-min [:span {:class "chip"} (str reading-time-min " min")])
                       (for [tag (take 4 tags)]
                         [:span {:class "chip"} tag])])])])])))

(defn queue-page [{:keys [biff/db]}]
  (let [all      (->> (xt/q db '{:find  [(pull ?e [*])]
                                 :where [[?e :article/status _]]})
                      (map first)
                      (filter #(#{:queued :enriching :failed} (:article/status %)))
                      (remove :article/deleted-at)
                      (sort-by :article/added-at #(compare %2 %1)))
        by-st    (group-by :article/status all)
        enriching (get by-st :enriching [])
        queued    (get by-st :queued [])
        failed    (get by-st :failed [])]
    (ui/page {:active :queue :title "Queue" :crumbs "Queue"
              :queue-count (count all) :inbox-count (count-inbox db)}
             [:div {:class "px-6 lg:px-10 py-8 max-w-3xl"}
              [:h1 {:class "serif-h1 text-4xl mb-2"} "Queue"]
              [:p {:class "text-sm text-stone-500 mb-6"} "Enrichment pipeline status."]
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
                      (when error
                        [:p {:class "text-xs text-stone-400 truncate"} error])]
                     [:span {:class "chip chip-mono text-xs shrink-0"} (str "attempt " (or retry-count 0))]
                     [:button {:hx-post (str "/api/articles/" id "/reenrich")
                               :hx-swap "none"
                               :hx-on--after-request "window.location.reload()"
                               :class "btn btn-xs btn-ghost text-stone-500 shrink-0"}
                      "Retry"]])]])
              (when (and (empty? enriching) (empty? queued) (empty? failed))
                [:p {:class "text-sm text-stone-400 mt-10 text-center"} "Queue is empty — all articles are processed."])])))

(defn article-page [{:keys [biff/db path-params]}]
  (let [id  (some-> (:id path-params) parse-uuid)
        art (when id
              (ffirst (xt/q db '{:find  [(pull ?e [*])]
                                 :in    [id]
                                 :where [[?e :xt/id id]
                                         [?e :article/url _]]}
                            id)))]
    (cond
      (nil? art)
      (ui/on-error {:status 404})

      (:article/deleted-at art)
      (ui/on-error {:status 404})

      :else
      (let [{:keys [xt/id article/title article/url article/byline article/lang
                    article/status article/tldr article/tags article/topic
                    article/why-interesting article/reading-time-min
                    article/quality-score article/published-at article/error
                    article/retry-count article/comments]} art
            display-title (or title url)]
        (ui/page {:active :inbox :title (or title "Article")
                  :crumbs (str "Inbox / " (or title "Article"))
                  :inbox-count (count-inbox db)}
                 [:div {:class "px-6 lg:px-10 py-8"}
                  (case status
                    :paywall
                    [:div {:class "mb-5 p-3 rounded-lg bg-amber-50 border border-amber-200 text-amber-700 text-sm max-w-5xl"}
                     "This article is behind a paywall."]
                    :notfound
                    [:div {:class "mb-5 p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm max-w-5xl"}
                     "Article not found (404)."]
                    :login-required
                    [:div {:class "mb-5 p-3 rounded-lg bg-blue-50 border border-blue-200 text-blue-700 text-sm max-w-5xl"}
                     "Login required to access this article."]
                    :failed
                    [:div {:class "mb-5 p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm flex items-center justify-between max-w-5xl"}
                     [:span (str "Enrichment failed" (when error (str ": " error))
                                 " (attempt " (or retry-count 0) ")")]
                     [:button {:hx-post (str "/api/articles/" id "/reenrich")
                               :hx-swap "none"
                               :hx-on--after-request "window.location.reload()"
                               :class "btn btn-xs btn-outline btn-error ml-3 shrink-0"}
                      "Retry"]]
                    nil)
                  [:div {:class "flex gap-10 items-start"}
            ;; Left — article content
                   [:div {:class "flex-1 min-w-0 max-w-2xl"}
                    [:div {:class "flex items-start justify-between gap-4 mb-3"}
                     [:div {:class "flex-1 min-w-0"}
                      [:h1 {:id              "article-title"
                            :class           "serif-h1 text-3xl leading-tight cursor-text px-2 -mx-2 rounded-lg hover:bg-stone-100 focus:outline-none focus:ring-2 focus:ring-accent-200 transition-colors"
                            :contenteditable "true"
                            :data-original   display-title
                            :data-patch-url  (str "/api/articles/" id)
                            :onkeydown       "if(event.key==='Enter'){event.preventDefault();this.blur()} if(event.key==='Escape'){this.innerText=this.dataset.original;this.blur()}"
                            :onblur          "(function(el){var t=el.innerText.trim();if(!t||t===el.dataset.original)return;fetch(el.dataset.patchUrl,{method:'PATCH',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'title='+encodeURIComponent(t)}).then(function(r){if(r.ok){el.dataset.original=t;showSavedToast()}else el.innerText=el.dataset.original}).catch(function(){el.innerText=el.dataset.original})})(this)"}
                       display-title]]
                     [:div {:class "flex items-center gap-1 shrink-0"}
                      [:a {:href url :target "_blank" :rel "noopener noreferrer"
                           :class "btn btn-sm btn-ghost gap-1.5"}
                       [:i {:data-lucide "external-link" :class "icon-sm"}]
                       "Open"]
                      [:button {:hx-post (str "/api/articles/" id "/read")
                                :hx-swap "none"
                                :hx-on--after-request "window.location='/inbox'"
                                :class "btn btn-sm btn-ghost gap-1.5 text-emerald-700"}
                       [:i {:data-lucide "check" :class "icon-sm"}]
                       "Mark read"]
                      [:button {:hx-delete (str "/api/articles/" id)
                                :hx-confirm "Delete this article?"
                                :hx-swap "none"
                                :hx-on--after-request "window.location='/inbox'"
                                :class "btn btn-sm btn-ghost text-red-500"}
                       [:i {:data-lucide "trash-2" :class "icon-sm"}]
                       "Delete"]]]
                    [:div {:class "flex items-center gap-2 text-sm text-stone-500 mb-6 flex-wrap"}
                     (when byline [:span byline])
                     (when published-at [:span (str (.toString published-at))])
                     (when lang [:span {:class "chip"} lang])
                     (when reading-time-min [:span {:class "chip"} (str reading-time-min " min")])
                     (when quality-score [:span {:class "chip"} (str "Q:" quality-score)])
                     [:a {:href url :target "_blank" :class "chip hover:underline truncate max-w-xs"} url]]
                    (when (seq tldr)
                      [:div {:class "mb-6"}
                       [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-2"} "Summary"]
                       [:ul {:class "space-y-1.5"}
                        (for [point tldr]
                          [:li {:class "text-stone-700 text-sm flex gap-2"}
                           [:span {:class "text-stone-300 mt-0.5"} "•"]
                           point])]])
                    (when why-interesting
                      [:div {:class "mb-6 p-4 rounded-lg bg-stone-50 border border-stone-200"}
                       [:p {:class "text-sm text-stone-600 italic"} why-interesting]])
                    (when (or topic (seq tags))
                      [:div {:class "flex flex-wrap gap-1.5"}
                       (when topic [:span {:class "chip font-medium"} topic])
                       (for [t tags]
                         [:span {:class "chip"} t])])]
            ;; Right — Notes / comments
                   [:aside {:class "w-72 shrink-0 sticky top-20 flex flex-col"}
                    [:p {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3"} "Notes"]
             ;; Bubbles
                    [:div {:id    "comments-list"
                           :class "flex flex-col gap-3 mb-4 overflow-y-auto max-h-[58vh] pr-0.5"}
                     (map comment-bubble (or comments []))]
             ;; Input
                    [:form {:hx-post              (str "/api/articles/" id "/comments")
                            :hx-target            "#comments-list"
                            :hx-swap              "beforeend"
                            :hx-on--after-request "this.reset()"}
                     [:textarea {:name      "text"
                                 :rows      3
                                 :placeholder "Add a note…"
                                 :class     "textarea textarea-bordered w-full bg-stone-50 text-sm resize-none focus:outline-none focus:border-accent-400"
                                 :onkeydown "if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();this.form.requestSubmit()}"}]
                     [:p {:class "text-xs text-stone-400 mt-1.5 select-none"} "↵ send  ·  ⇧↵ newline"]]]]
                  [:script
                   "(function(){
            var h1=document.getElementById('article-title');
            var st=document.getElementById('title-status');
            if(!h1)return;
            function save(){
              var title=h1.innerText.trim();
              if(!title||title===h1.dataset.original)return;
              fetch(h1.dataset.patchUrl,{
                method:'PATCH',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'title='+encodeURIComponent(title)
              }).then(function(r){
                if(r.ok){
                  h1.dataset.original=title;
                  st.textContent='✓ Saved';
                  setTimeout(function(){st.textContent='';},2000);
                }else{
                  h1.innerText=h1.dataset.original;
                  st.textContent='Failed to save';
                  st.className=st.className+' !text-red-400';
                  setTimeout(function(){st.textContent='';},3000);
                }
              }).catch(function(){
                h1.innerText=h1.dataset.original;
                st.textContent='Error';
              });
            }
            h1.addEventListener('blur',save);
            h1.addEventListener('keydown',function(e){
              if(e.key==='Enter'){e.preventDefault();h1.blur();}
              if(e.key==='Escape'){h1.innerText=h1.dataset.original;h1.blur();}
            });
          })();"]])))))

(defn search-page [{:keys [biff/db params]}]
  (let [q (str/trim (or (:q params) ""))]
    (ui/page {:active :search :title "Search" :crumbs "Search" :inbox-count (count-inbox db)}
             [:div {:class "px-6 lg:px-10 py-8 max-w-3xl"}
              [:h1 {:class "serif-h1 text-4xl mb-2"} "Search"]
              [:p {:class "text-sm text-stone-400 mb-6"} "Full-text search across titles, summaries, tags and keywords."]
              [:div {:class "relative mb-6"}
               [:i {:data-lucide "search" :class "absolute left-3 top-1/2 -translate-y-1/2 icon-md text-stone-400 pointer-events-none"}]
               [:input {:id          "search-input"
                        :name        "q"
                        :type        "text"
                        :value       q
                        :placeholder "Search articles…"
                        :autofocus   true
                        :class       "input input-bordered w-full pl-9 bg-stone-50 focus:bg-white"
                        :hx-get      "/api/search/html"
                        :hx-trigger  "input changed delay:300ms, search"
                        :hx-target   "#search-results"
                        :hx-swap     "innerHTML"
                        :hx-include  "#search-input"}]]
              [:div {:id "search-results"}
               (if (>= (count q) 2)
                 [:p {:class "text-sm text-stone-400 text-center py-8"} "Searching…"]
                 [:p {:class "text-sm text-stone-400 text-center py-8"}
                  "Type at least 2 characters to search"])]])))

(def ^:private quick-action-script
  "for url in \"$@\"; do\n  curl -s -X POST http://localhost:7777/api/add \\\n    -d \"url=$url&source=service\" > /dev/null\ndone")

(defn settings-page [{:keys [biff/db]}]
  (ui/page {:active :settings :title "Settings" :crumbs "Settings" :inbox-count (count-inbox db)}
           [:div {:class "px-6 lg:px-10 py-8 max-w-3xl"}
            [:h1 {:class "serif-h1 text-4xl mb-2"} "Settings"]
            [:section {:class "mt-8"}
             [:h2 {:class "text-xl font-semibold mb-1"} "macOS Quick Action"]
             [:p {:class "text-sm text-stone-500 mb-6"}
              "Сохраняй любую ссылку правой кнопкой мыши или горячей клавишей."]
             [:ol {:class "space-y-5"}
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "1"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Открой Automator"]
                [:p {:class "text-sm text-stone-500"} "Spotlight → Automator, создай новый документ типа " [:strong "Quick Action"] "."]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "2"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Настрой входные данные"]
                [:p {:class "text-sm text-stone-500"}
                 "Вверху: «Workflow receives current» → " [:strong "URLs"] " → «in any application»."]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "3"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700 mb-2"} "Добавь «Run Shell Script»"]
                [:p {:class "text-sm text-stone-500 mb-3"}
                 "В библиотеке: Utilities → " [:strong "Run Shell Script"] ". Установи «Pass input» → " [:strong "as arguments"] ". Вставь:"]
                [:div {:class "relative group"}
                 [:pre {:class "font-mono text-xs bg-stone-900 text-stone-100 rounded-lg px-4 py-3 overflow-x-auto leading-relaxed"}
                  quick-action-script]
                 [:button {:class "absolute top-2 right-2 opacity-0 group-hover:opacity-100 btn btn-xs bg-stone-700 text-stone-200 border-none transition-opacity"
                           :onclick "navigator.clipboard.writeText(this.previousElementSibling.textContent);this.textContent='Copied!';setTimeout(()=>this.textContent='Copy',1500)"}
                  "Copy"]]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "4"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Сохрани как «Save to Readlater»"]
                [:p {:class "text-sm text-stone-500"} "File → Save, имя: «Save to Readlater»."]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "5"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Назначь горячую клавишу (опционально)"]
                [:p {:class "text-sm text-stone-500"}
                 "System Settings → Keyboard → Keyboard Shortcuts → Services → найди «Save to Readlater» → назначь " [:kbd {:class "font-mono text-xs bg-stone-100 border border-stone-300 rounded px-1.5 py-0.5"} "⌘⇧S"] "."]]]]
             [:div {:class "mt-6 p-4 rounded-lg bg-stone-50 border border-stone-200 flex gap-3"}
              [:i {:data-lucide "mouse-pointer-2" :class "icon-md text-stone-400 shrink-0 mt-0.5"}]
              [:p {:class "text-sm text-stone-500"}
               "После настройки: выдели URL на любой странице → правая кнопка → Services → "
               [:strong "Save to Readlater"] ". Или просто нажми "
               [:kbd {:class "font-mono text-xs bg-stone-100 border border-stone-300 rounded px-1.5 py-0.5"} "⌘⇧S"]
               " когда ссылка выделена."]]]]))

(defn today-history-page [{:keys [biff/db]}]
  (ui/page {:active :today :title "Today History" :crumbs "Today / History" :inbox-count (count-inbox db)}
           [:div {:class "px-6 lg:px-10 py-8 max-w-3xl"}
            [:h1 {:class "serif-h1 text-4xl mb-2"} "Today History"]]))

;; ---------------------------------------------------------------------------
;; API handlers

(defn add-url [{:keys [biff/db params] :as ctx}]
  (let [u    (or (:url params) (get params "url"))
        src  (keyword (or (:source params) (get params "source") "service"))
        norm (url/normalize u)
        existing (when (seq norm)
                   (ffirst (xt/q db '{:find  [(pull ?e [*])]
                                      :in    [u]
                                      :where [[?e :article/url-normalized u]]}
                                 norm)))]
    (cond
      (str/blank? u)
      (html-frag [:p {:class "text-red-500"} "Enter a URL"])

      existing
      (html-frag [:p {:class "text-amber-600"}
                  "Already in library — "
                  [:a {:href  (str "/article/" (:xt/id existing))
                       :class "underline"} "open"]])

      :else
      (let [id (UUID/randomUUID)]
        (biff/submit-tx ctx
                        [{:db/doc-type          :article
                          :xt/id                id
                          :article/url          u
                          :article/url-normalized norm
                          :article/source       src
                          :article/status       :queued
                          :article/added-at     (now)
                          :article/retry-count  0}])
        (html-frag [:p {:class "text-emerald-600"} "Saved! Article will appear in Inbox shortly."])))))

(defn list-articles     [_ctx] {:status 200 :body []})
(defn get-article       [_ctx] {:status 501 :body {:error "not implemented"}})
(defn patch-article [{:keys [path-params params] :as ctx}]
  (let [id    (some-> (:id path-params) parse-uuid)
        title (some-> (or (get params :title) (get params "title")) str/trim)]
    (if (or (nil? id) (str/blank? title))
      (html-frag [:span {:class "text-red-400"} "Failed to save"])
      (do
        (biff/submit-tx ctx
                        [{:db/op :update :db/doc-type :article :xt/id id :article/title title}])
        (html-frag [:span "✓ Saved"])))))
(defn mark-read [{:keys [path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (if-not id
      {:status 400 :body {:error "invalid id"}}
      (do (biff/submit-tx ctx [{:db/op        :update
                                :db/doc-type  :article
                                :xt/id        id
                                :article/status  :read
                                :article/read-at (now)}])
          {:status 200 :body {:ok true}}))))

(defn archive-article   [_ctx] {:status 501 :body {:error "not implemented"}})
(defn unarchive-article [_ctx] {:status 501 :body {:error "not implemented"}})

(defn add-comment [{:keys [biff/db path-params params] :as ctx}]
  (let [id   (some-> (:id path-params) parse-uuid)
        text (str/trim (or (get params :text) (get params "text") ""))]
    (if (or (nil? id) (str/blank? text))
      {:status 400 :body "invalid"}
      (let [art      (ffirst (xt/q db '{:find  [(pull ?e [:xt/id :article/comments])]
                                        :in    [id]
                                        :where [[?e :xt/id id]]}
                                   id))
            comment  {:text text :created-at (now)}
            updated  (conj (or (:article/comments art) []) comment)]
        (biff/submit-tx ctx
                        [{:db/op :update :db/doc-type :article :xt/id id :article/comments updated}])
        (html-frag (comment-bubble comment))))))

(defn delete-article [{:keys [path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (if-not id
      {:status 400 :body {:error "invalid id"}}
      (do
        (biff/submit-tx ctx
                        [{:db/op          :update
                          :db/doc-type    :article
                          :xt/id          id
                          :article/deleted-at (now)}])
        (search/delete-doc ctx (str id))
        {:status 200 :body {:ok true}}))))

(defn queue-stats [{:keys [biff/db]}]
  (let [counts (->> (xt/q db '{:find  [?s (count ?e)]
                               :where [[?e :article/status ?s]
                                       [(contains? #{:queued :enriching :failed} ?s)]]})
                    (into {}))]
    {:status 200
     :body   {:queued    (get counts :queued 0)
              :enriching (get counts :enriching 0)
              :failed    (get counts :failed 0)}}))

(defn tag-counts [_ctx]
  {:status 200 :body []})

(defn rec-fragment [{:keys [biff/db]}]
  (html-frag (rec-section-content db)))

(defn health [{:keys [biff/db]}]
  {:status 200
   :body   {:ok true :db_ok (boolean db) :version "0.1.0"}})

;; ---------------------------------------------------------------------------
;; Module

(def module
  {:routes
   [["/"              {:get #'today-page}]
    ["/inbox"         {:get #'inbox-page}]
    ["/week"          {:get #'week-page}]
    ["/tags"          {:get #'tags-page}]
    ["/archive"       {:get #'archive-page}]
    ["/queue"         {:get #'queue-page}]
    ["/article/:id"   {:get #'article-page}]
    ["/search"        {:get #'search-page}]
    ["/settings"      {:get #'settings-page}]
    ["/today/history" {:get #'today-history-page}]]

   :api-routes
   [["/api/add"                    {:post #'add-url}]
    ["/api/articles"               {:get  #'list-articles}]
    ["/api/articles/:id"           {:get    #'get-article
                                    :patch  #'patch-article
                                    :delete #'delete-article}]
    ["/api/articles/:id/comments"  {:post   #'add-comment}]
    ["/api/articles/:id/read"      {:post #'mark-read}]
    ["/api/articles/:id/archive"   {:post #'archive-article}]
    ["/api/articles/:id/unarchive" {:post #'unarchive-article}]
    ["/api/search"                 {:get  #'search/search}]
    ["/api/search/html"            {:get  #'search/search-html}]
    ["/api/queue"                  {:get  #'queue-stats}]
    ["/api/tags"                   {:get  #'tag-counts}]
    ["/api/tags/:tag"              {:get  #'tag-articles}]
    ["/api/recommendations/fragment" {:get #'rec-fragment}]
    ["/health"                     {:get  #'health}]]})
