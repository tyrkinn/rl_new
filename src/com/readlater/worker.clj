(ns com.readlater.worker
  (:require [com.biffweb :as biff]
            [com.readlater.url :as url]
            [com.readlater.llm :as llm]
            [com.readlater.search :as search]
            [xtdb.api :as xt]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hato.client :as hc])
  (:import [java.util UUID]
           [java.time Instant LocalDate ZoneOffset]))

(defn- now [] (Instant/now))

;; ---------------------------------------------------------------------------
;; Pending drain — runs once at startup

(def ^:private pending-path
  (io/file (System/getProperty "user.home") ".readlater" "pending.txt"))

(defn use-pending-drain
  "Biff component: reads ~/.readlater/pending.txt and queues any new URLs."
  [system]
  (when (.exists pending-path)
    (let [node (:biff.xtdb/node system)
          db   (xt/db node)
          lines (->> (slurp pending-path)
                     str/split-lines
                     (map str/trim)
                     (remove str/blank?))]
      (when (seq lines)
        (log/info "pending-drain: processing" (count lines) "URLs")
        (doseq [u lines]
          (try
            (let [norm     (url/normalize u)
                  existing (ffirst (xt/q db '{:find [?e] :in [u]
                                              :where [[?e :article/url-normalized u]]}
                                         norm))]
              (when-not existing
                (let [id (UUID/randomUUID)]
                  (biff/submit-tx system
                                  [{:db/doc-type         :article
                                    :xt/id               id
                                    :article/url         u
                                    :article/url-normalized norm
                                    :article/source      :cli
                                    :article/status      :queued
                                    :article/added-at    (now)
                                    :article/retry-count 0}])
                  (log/info "pending-drain: queued" u))))
            (catch Exception e
              (log/warn "pending-drain: failed" u (.getMessage e)))))
        (spit pending-path ""))))
  system)

;; ---------------------------------------------------------------------------
;; Kind detection from URL pattern

(defn detect-kind
  "Infer content kind from URL pattern. Returns nil when ambiguous (article/bookmark)."
  [^String url]
  (cond
    (re-find #"(?i)(youtube\.com/watch|youtu\.be/|vimeo\.com/\d|dailymotion\.com/video|twitch\.tv/videos)" url) :video
    (re-find #"(?i)(arxiv\.org/abs|arxiv\.org/pdf|doi\.org/|researchgate\.net/publication|semanticscholar\.org/paper|ncbi\.nlm\.nih\.gov/pmc)" url) :paper
    (re-find #"(?i)(twitter\.com/[^/?]+/status/|x\.com/[^/?]+/status/|reddit\.com/r/[^/]+/comments/|news\.ycombinator\.com/item|lobste\.rs/s/)" url) :thread
    :else nil))

;; ---------------------------------------------------------------------------
;; Kind migration — runs once at startup, classifies nil-kind articles

(defn use-kind-migration [system]
  (let [db            (xt/db (:biff.xtdb/node system))
        unclassified  (->> (xt/q db '{:find [(pull ?e [:xt/id :article/url :article/kind])]
                                      :where [[?e :article/url _]]})
                           (map first)
                           (filter #(nil? (:article/kind %))))]
    (when (seq unclassified)
      (log/info "kind-migration: classifying" (count unclassified) "articles")
      (doseq [{:keys [xt/id article/url]} unclassified]
        (biff/submit-tx system
                        [{:db/op        :update
                          :db/doc-type  :article
                          :xt/id        id
                          :article/kind (or (detect-kind url) :article)}]))))
  system)

;; ---------------------------------------------------------------------------
;; Enrichment prompts per kind

(defn- load-prompt [name]
  (delay
    (if-let [r (io/resource (str "prompts/" name ".md"))]
      (slurp r)
      (throw (ex-info (str "prompts/" name ".md not found") {})))))

(def ^:private prompts
  {:article  (load-prompt "enrich-article")
   :video    (load-prompt "enrich-video")
   :bookmark (load-prompt "enrich-bookmark")
   :thread   (load-prompt "enrich-thread")
   :paper    (load-prompt "enrich-paper")})

(defn- build-enrich-prompt [url kind]
  (let [k       (or kind :article)
        tmpl    (get prompts k (get prompts :article))]
    (str/replace @tmpl "<URL>" url)))

;; ---------------------------------------------------------------------------
;; Field mapping: Claude JSON → XTDB article attrs

(defn- safe-str-vec [v]
  (when (and (sequential? v) (seq v))
    (vec (filter string? v))))

(defn- parse-instant [s]
  (when (string? s)
    (try (Instant/parse s)
         (catch Exception _
           (try (-> (LocalDate/parse (subs s 0 10))
                    (.atStartOfDay ZoneOffset/UTC)
                    .toInstant)
                (catch Exception _ nil))))))

(defn- kind-kw [s]
  (when (string? s)
    (get {"article" :article "video" :video "bookmark" :bookmark
          "thread"  :thread  "paper" :paper} s nil)))

(defn- map-claude-fields [{:keys [kind title byline lang topic why_interesting
                                   tldr tags keywords synonyms
                                   reading_time_min quality_score published_at
                                   ;; video
                                   channel platform duration_min
                                   ;; bookmark
                                   description category
                                   ;; thread
                                   author_handle
                                   ;; paper
                                   paper_authors field abstract_summary key_findings]}]
  (cond-> {}
    (kind-kw kind)             (assoc :article/kind (kind-kw kind))
    (seq title)                (assoc :article/title title)
    (seq byline)               (assoc :article/byline byline)
    (seq lang)                 (assoc :article/lang lang)
    (seq topic)                (assoc :article/topic topic)
    (seq why_interesting)      (assoc :article/why-interesting why_interesting)
    (seq description)          (assoc :article/why-interesting description)
    (safe-str-vec tldr)        (assoc :article/tldr (safe-str-vec tldr))
    (safe-str-vec tags)        (assoc :article/tags (safe-str-vec tags))
    (safe-str-vec keywords)    (assoc :article/keywords (safe-str-vec keywords))
    (safe-str-vec synonyms)    (assoc :article/synonyms (safe-str-vec synonyms))
    (number? reading_time_min) (assoc :article/reading-time-min (int reading_time_min))
    (number? quality_score)    (assoc :article/quality-score (int quality_score))
    (parse-instant published_at) (assoc :article/published-at (parse-instant published_at))
    ;; video fields
    (seq channel)              (assoc :article/channel channel)
    (seq platform)             (assoc :article/platform platform)
    (number? duration_min)     (assoc :article/duration-min (int duration_min))
    ;; bookmark fields
    (seq category)             (assoc :article/category category)
    ;; thread fields
    (seq author_handle)        (assoc :article/author-handle author_handle)
    ;; paper fields
    (safe-str-vec paper_authors) (assoc :article/paper-authors (safe-str-vec paper_authors))
    (seq field)                (assoc :article/field field)
    (seq abstract_summary)     (assoc :article/why-interesting abstract_summary)
    (safe-str-vec key_findings) (assoc :article/key-findings (safe-str-vec key_findings))))

;; ---------------------------------------------------------------------------
;; Error classification

(defn- reason->status [^String r]
  (case r
    "paywall"        :paywall
    "notfound"       :notfound
    "login_required" :login-required
    :failed))

(defn- reason->kw [^String r]
  (case r
    "paywall"        :paywall
    "notfound"       :notfound
    "login_required" :login-required
    :other))

;; ---------------------------------------------------------------------------
;; Backoff: 1m → 5m → 30m

(def ^:private backoff-secs [60 300 1800])

(defn- next-attempt-at [attempt-idx]
  (.plusSeconds (now) (long (get backoff-secs attempt-idx 1800))))

;; ---------------------------------------------------------------------------
;; Core enrichment — called in a future, article is already :enriching

(defn- enrich-one! [sys {:keys [xt/id article/url article/kind article/retry-count]
                         :or   {retry-count 0}
                         :as   article}]
  (log/info "enrich: processing" url "kind:" kind)
  (let [{:keys [ok? data error]} (llm/invoke (build-enrich-prompt url kind)
                                             {:allowed-tools "WebFetch"})]
    (cond
      ;; Claude process failed / JSON parse error → retry with backoff
      (not ok?)
      (let [n (inc retry-count)]
        (log/warn "enrich: invocation failed (attempt" n "):" error)
        (if (>= n 3)
          (do (log/warn "enrich: max retries reached, marking :failed:" url)
              (biff/submit-tx sys [{:db/op :update :db/doc-type :article
                                    :xt/id id :article/status :failed
                                    :article/error error :article/retry-count n}])
              (biff/submit-tx sys [{:db/doc-type    :notification
                                    :xt/id          (UUID/randomUUID)
                                    :notif/type     :error
                                    :notif/title    (or (:article/title article) url)
                                    :notif/body     (str "Enrichment failed after " n " attempts")
                                    :notif/link     (str "/article/" id)
                                    :notif/read     false
                                    :notif/created-at (now)}]))
          (biff/submit-tx sys [{:db/op :update :db/doc-type :article
                                :xt/id id :article/status :failed
                                :article/error error :article/retry-count n
                                :article/next-attempt-at (next-attempt-at retry-count)}])))

      ;; Claude returned {:error "..." :reason "..."} — terminal or retryable
      (contains? data :error)
      (let [reason (or (some-> (:reason data) str) "other")
            status (reason->status reason)]
        (log/info "enrich: terminal status" status "for" url)
        (biff/submit-tx sys [{:db/op :update :db/doc-type :article
                              :xt/id id :article/status status
                              :article/error (str (:error data))
                              :article/error-reason (reason->kw reason)}]))

      ;; Success — map fields and mark :ready
      :else
      (let [fields (map-claude-fields data)
            title  (or (:article/title fields) (:article/title article) url)]
        (log/info "enrich: ready" url (str "(tags:" (str/join "," (:article/tags fields)) ")"))
        (biff/submit-tx sys [(merge {:db/op :update :db/doc-type :article
                                     :xt/id id :article/status :ready
                                     :article/enriched-at (now)}
                                    fields)])
        (let [final-kind (or (:article/kind fields) kind :article)
              notif-body (case final-kind
                           :video    "Video saved — ready to watch"
                           :bookmark "Bookmark enriched"
                           :thread   "Thread saved"
                           :paper    "Paper saved"
                           "Article enriched and ready to read")]
          (biff/submit-tx sys [{:db/doc-type    :notification
                                :xt/id          (UUID/randomUUID)
                                :notif/type     :success
                                :notif/title    title
                                :notif/body     notif-body
                                :notif/link     (str "/article/" id)
                                :notif/read     false
                                :notif/created-at (now)}]))
        (search/index-doc sys (merge article {:article/status :ready} fields))))))

;; ---------------------------------------------------------------------------
;; Polling helpers

(defn- eligible-queued [db]
  (->> (xt/q db '{:find  [(pull ?e [:xt/id :article/url :article/kind :article/retry-count])]
                  :where [[?e :article/status :queued]]})
       (map first)))

(defn- eligible-failed [db]
  (let [now-inst (now)]
    (->> (xt/q db '{:find  [(pull ?e [:xt/id :article/url :article/kind :article/retry-count
                                      :article/next-attempt-at])]
                    :where [[?e :article/status :failed]]})
         (map first)
         (filter (fn [{:keys [article/retry-count article/next-attempt-at]}]
                   (and (< (or retry-count 0) 3)
                        next-attempt-at
                        (.isBefore ^Instant next-attempt-at now-inst)))))))

(defn- reset-stale-enriching! [sys]
  (let [db  (xt/db (:biff.xtdb/node sys))
        ids (->> (xt/q db '{:find  [(pull ?e [:xt/id])]
                            :where [[?e :article/status :enriching]]})
                 (map (comp :xt/id first)))]
    (when (seq ids)
      (log/info "enrich-worker: resetting" (count ids) "stale :enriching articles")
      (biff/submit-tx sys
                      (for [id ids]
                        {:db/op :update :db/doc-type :article
                         :xt/id id :article/status :queued})))))

;; ---------------------------------------------------------------------------
;; Enrich worker component

(defn use-enrich-worker [system]
  (reset-stale-enriching! system)
  (let [running (atom true)
        thread  (doto
                 (Thread.
                  (fn []
                    (log/info "enrich-worker: started (polling every 5s, parallelism=2)")
                    (loop []
                      (when @running
                        (try
                          (let [db         (xt/db (:biff.xtdb/node system))
                                candidates (take 2 (concat (eligible-queued db)
                                                           (eligible-failed db)))]
                            (doseq [article candidates]
                                ;; Mark :enriching synchronously to prevent double-pickup
                              (biff/submit-tx system
                                              [{:db/op :update :db/doc-type :article
                                                :xt/id (:xt/id article) :article/status :enriching}])
                              (future
                                (try
                                  (enrich-one! system article)
                                  (catch Exception e
                                    (log/error e "enrich: unhandled exception for"
                                               (:article/url article))
                                      ;; Ensure article doesn't stay stuck as :enriching
                                    (try
                                      (biff/submit-tx system
                                                      [{:db/op :update :db/doc-type :article
                                                        :xt/id (:xt/id article) :article/status :failed
                                                        :article/error (.getMessage e)
                                                        :article/retry-count
                                                        (inc (or (:article/retry-count article) 0))}])
                                      (catch Exception _ nil)))))))
                          (catch Exception e
                            (log/error e "enrich-worker: poll error")))
                        (Thread/sleep 5000)
                        (recur))))
                  "readlater-enrich-worker")
                  (.setDaemon true)
                  .start)]
    (update system :biff/stop conj #(reset! running false))))

;; ---------------------------------------------------------------------------
;; API handlers

(defn force-reenrich [{:keys [path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (if-not id
      {:status 400 :body {:error "invalid id"}}
      (do
        (biff/submit-tx ctx
                        [{:db/op          :update
                          :db/doc-type    :article
                          :xt/id          id
                          :article/status :queued
                          :article/retry-count 0
                          :article/error  nil}])
        {:status 200 :body {:ok true}}))))

(defn synonyms-current [_ctx]
  {:status 200 :body {:dictionary {}}})

(defn synonyms-rebuild [_ctx]
  {:status 501 :body {:error "not implemented"}})

;; ---------------------------------------------------------------------------
;; Recommendations

(def generation-state
  "Tracks background recommendation generation: {:status :idle/:running/:error, ...}"
  (atom {:status :idle}))

(def ^:private recommend-prompt-template
  (delay
    (if-let [r (io/resource "prompts/recommend.md")]
      (slurp r)
      (throw (ex-info "resources/prompts/recommend.md not found" {})))))

(def ^:private theme-external-prompt-template
  (delay
    (if-let [r (io/resource "prompts/theme-external.md")]
      (slurp r)
      (throw (ex-info "resources/prompts/theme-external.md not found" {})))))

;; ---------------------------------------------------------------------------
;; External source fetchers

(defn- fetch-hn-top []
  (try
    (let [resp (hc/get "https://hn.algolia.com/api/v1/search"
                       {:query-params {"tags"          "story"
                                       "hitsPerPage"   "30"
                                       "numericFilters" "points>50"}
                        :as           :json})
          hits (get-in resp [:body :hits])]
      (->> hits
           (filter #(seq (:url %)))
           (mapv #(hash-map :title  (or (:title %) "")
                            :url    (:url %)
                            :points (or (:points %) 0)
                            :author (or (:author %) "")))))
    (catch Exception e
      (log/warn "fetch-hn-top failed:" (.getMessage e))
      nil)))

(defn- fetch-lobsters-top []
  (try
    (let [resp (hc/get "https://lobste.rs/hottest.json"
                       {:as :json})
          stories (:body resp)]
      (->> stories
           (take 20)
           (mapv #(hash-map :title (or (:title %) "")
                            :url   (or (:url %) "")
                            :score (or (:score %) 0)
                            :tags  (vec (or (:tags %) []))))))
    (catch Exception e
      (log/warn "fetch-lobsters-top failed:" (.getMessage e))
      nil)))

(defn- theme-group-external [items source-label]
  (when (seq items)
    (try
      (let [prompt (-> @theme-external-prompt-template
                       (str/replace "<SOURCE>"      source-label)
                       (str/replace "<STORIES_JSON>" (json/generate-string items)))
            {:keys [ok? data error]} (llm/invoke prompt {})]
        (if ok?
          (assoc data :source-label source-label)
          (do
            (log/warn "theme-group-external failed for" source-label ":" error)
            nil)))
      (catch Exception e
        (log/warn "theme-group-external exception for" source-label ":" (.getMessage e))
        nil))))

(defn today-batch
  "Returns today's recommendation-batch document or nil."
  [db]
  (ffirst (xt/q db '{:find  [(pull ?e [*])]
                     :in    [d]
                     :where [[?e :rec/date d]]}
                (.toString (LocalDate/now)))))

(defn- recs-articles [db]
  (->> (xt/q db '{:find  [(pull ?e [:xt/id :article/title :article/tldr :article/tags
                                    :article/topic :article/why-interesting
                                    :article/quality-score :article/reading-time-min])]
                  :where [[?e :article/status :ready]]})
       (map first)
       (remove :article/deleted-at)))

(defn- recent-rec-ids [db]
  (let [cutoff (.toString (.minusDays (LocalDate/now) 7))]
    (->> (xt/q db '{:find  [(pull ?e [:rec/date :rec/collections])]
                    :where [[?e :rec/date _]]})
         (map first)
         (filter #(pos? (compare (:rec/date %) cutoff)))
         (mapcat :rec/collections)
         (mapcat :collection/items)
         (map :item/article-id)
         distinct)))

(defn- build-rec-prompt [articles recent-ids interests]
  (-> @recommend-prompt-template
      (str/replace "<INTERESTS>"    (or (not-empty interests) "не указано"))
      (str/replace "<RECENT_IDS>"   (if (seq recent-ids)
                                      (str/join ", " (map str recent-ids))
                                      "нет"))
      (str/replace "<ARTICLES_JSON>"
                   (json/generate-string
                    (mapv (fn [a] {:id               (str (:xt/id a))
                                   :title            (or (:article/title a) "")
                                   :tldr             (vec (or (:article/tldr a) []))
                                   :tags             (vec (or (:article/tags a) []))
                                   :topic            (:article/topic a)
                                   :why_interesting  (:article/why-interesting a)
                                   :quality_score    (:article/quality-score a)
                                   :reading_time_min (:article/reading-time-min a)})
                          articles)))))

(defn- map-collection [{:keys [title description vibe items]}]
  {:collection/title       (str title)
   :collection/description (str description)
   :collection/vibe        (str vibe)
   :collection/items       (mapv (fn [{:keys [article_id micro_blurb]}]
                                   {:item/article-id  (parse-uuid (str article_id))
                                    :item/micro-blurb (str micro_blurb)})
                                 (or items []))})

(defn- do-generate! [sys]
  (let [db       (xt/db (:biff.xtdb/node sys))
        articles (recs-articles db)]
    (if (< (count articles) 3)
      (do (log/info "recommend: fewer than 3 ready articles, skipping")
          (reset! generation-state {:status :idle}))
      (let [recent  (recent-rec-ids db)
            prompt  (build-rec-prompt articles recent nil)
            ;; Start external fetches in parallel while Claude works on library
            hn-future      (future (fetch-hn-top))
            lobsters-future (future (fetch-lobsters-top))
            {:keys [ok? data error]} (llm/invoke prompt {})]
        (if-not ok?
          (do (log/warn "recommend: claude failed:" error)
              (reset! generation-state {:status :error :error error}))
          (let [;; Library collections — take only the first one
                collections      (vec (take 1 (mapv map-collection (get data :collections []))))
                ;; Deref external fetches
                hn-items         (try @hn-future
                                   (catch Exception e
                                     (log/warn "recommend: hn fetch exception:" (.getMessage e))
                                     nil))
                lobsters-items   (try @lobsters-future
                                   (catch Exception e
                                     (log/warn "recommend: lobsters fetch exception:" (.getMessage e))
                                     nil))
                ;; Theme-group external sources
                hn-collection      (when (seq hn-items)
                                     (theme-group-external hn-items "HackerNews"))
                lobsters-collection (when (seq lobsters-items)
                                      (theme-group-external lobsters-items "Lobsters"))
                external-cols    (vec (remove nil? [hn-collection lobsters-collection]))
                batch-id         (UUID/randomUUID)
                tx-doc           (cond-> {:db/doc-type         :rec
                                          :xt/id               batch-id
                                          :rec/date            (.toString (LocalDate/now))
                                          :rec/generated-at    (now)
                                          :rec/prompt-snapshot prompt
                                          :rec/collections     collections}
                                   (seq external-cols)
                                   (assoc :rec/external-collections external-cols))]
            (biff/submit-tx sys [tx-doc])
            (log/info "recommend: saved" (count collections) "library collection(s) and"
                      (count external-cols) "external collection(s) for"
                      (.toString (LocalDate/now)))
            (reset! generation-state {:status :idle})))))))

(defn start-generation-if-needed!
  "Non-blocking: starts a future to generate today's recs if not yet generated."
  [sys]
  (when (= :idle (:status @generation-state))
    (let [db (xt/db (:biff.xtdb/node sys))]
      (when (and (not (today-batch db))
                 (>= (count (recs-articles db)) 3))
        (reset! generation-state {:status :running :started-at (now)})
        (future
          (try
            (do-generate! sys)
            (catch Exception e
              (log/error e "recommend: unhandled generation error")
              (reset! generation-state {:status :idle}))))))))

(defn recommendations-today [{:keys [biff/db]}]
  (if-let [batch (today-batch db)]
    {:status 200 :body {:collections (:rec/collections batch)}}
    {:status 200 :body {:collections []
                        :generating  (= :running (:status @generation-state))}}))

(defn recommendations-regenerate [{:keys [biff/db] :as ctx}]
  (if (= :running (:status @generation-state))
    {:status 200 :body {:status "already-running"}}
    (do
      (reset! generation-state {:status :running :started-at (now)})
      (future
        (try
          (do-generate! ctx)
          (catch Exception e
            (log/error e "recommend: manual regen error")
            (reset! generation-state {:status :idle}))))
      {:status 202 :body {:status "started"}})))

;; ---------------------------------------------------------------------------
;; Module

(def module
  {:api-routes
   [["/api/articles/:id/reenrich"      {:post #'force-reenrich}]
    ["/api/search/synonyms"            {:get  #'synonyms-current}]
    ["/api/search/rebuild-synonyms"    {:post #'synonyms-rebuild}]
    ["/api/recommendations/today"      {:get  #'recommendations-today}]
    ["/api/recommendations/regenerate" {:post #'recommendations-regenerate}]]})
