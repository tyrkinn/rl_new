(ns com.readlater.search
  "Meilisearch HTTP API wrapper + Biff component."
  (:require [hato.client :as http]
            [cheshire.core :as json]
            [rum.core :as rum]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [xtdb.api :as xt]))

;; ---------------------------------------------------------------------------
;; Config

(defn- base-url [ctx]
  (or (get-in ctx [:biff/secret :meili-url])
      (System/getenv "MEILI_URL")
      "http://127.0.0.1:7700"))

(defn- index-name [_ctx] "articles")

(defn- index-url [ctx & parts]
  (apply str (base-url ctx) "/indexes/" (index-name ctx) parts))

(defn- api-key [ctx]
  (or (get-in ctx [:biff/secret :meili-master-key])
      (System/getenv "MEILI_MASTER_KEY")))

(defn- req-opts [ctx]
  (cond-> {:content-type :json :as :string}
    (api-key ctx) (assoc :oauth-token (api-key ctx))))

;; ---------------------------------------------------------------------------
;; Low-level HTTP helpers

(defn- patch-settings [ctx data]
  (http/patch (index-url ctx "/settings")
              (assoc (req-opts ctx)
                     :body (json/generate-string data))))

(defn- ensure-index [ctx]
  (try
    (http/post (str (base-url ctx) "/indexes")
               (assoc (req-opts ctx)
                      :body (json/generate-string {:uid (index-name ctx) :primaryKey "id"})))
    (catch Exception _ nil)))

;; ---------------------------------------------------------------------------
;; Article document

(defn- article->doc [{:keys [xt/id article/title article/byline article/url
                              article/url-normalized article/tags article/topic
                              article/tldr article/why-interesting article/keywords
                              article/status article/reading-time-min article/quality-score
                              article/lang article/added-at]}]
  (cond-> {:id     (str id)
           :url    (or url "")
           :status (name (or status :ready))}
    title            (assoc :title title)
    byline           (assoc :byline byline)
    (seq tags)       (assoc :tags (vec tags))
    topic            (assoc :topic topic)
    (seq tldr)       (assoc :tldr (str/join " " tldr))
    why-interesting  (assoc :why_interesting why-interesting)
    (seq keywords)   (assoc :keywords (vec keywords))
    reading-time-min (assoc :reading_time_min reading-time-min)
    quality-score    (assoc :quality_score quality-score)
    lang             (assoc :lang lang)
    added-at         (assoc :added_at (.toEpochMilli added-at))))

;; ---------------------------------------------------------------------------
;; Public API

(defn index-doc
  "Index a single article document. Swallows errors so enrichment never fails because of Meili."
  [ctx article]
  (try
    (http/put (index-url ctx "/documents")
              (assoc (req-opts ctx)
                     :body (json/generate-string [(article->doc article)])))
    nil
    (catch Exception e
      (log/warn "search/index-doc failed:" (.getMessage e)))))

(defn delete-doc
  "Remove a document from the index by article UUID."
  [ctx id]
  (try
    (http/delete (index-url ctx (str "/documents/" id)) (req-opts ctx))
    nil
    (catch Exception e
      (log/warn "search/delete-doc failed:" (.getMessage e)))))

(defn put-synonyms
  "Push synonym dictionary (map of string → [string]) to Meilisearch."
  [ctx dict]
  (try
    (http/put (index-url ctx "/settings/synonyms")
              (assoc (req-opts ctx)
                     :body (json/generate-string dict)))
    nil
    (catch Exception e
      (log/warn "search/put-synonyms failed:" (.getMessage e)))))

;; ---------------------------------------------------------------------------
;; JSON search

(defn search
  "Ring handler: GET /api/search?q=…&filter=…  Returns JSON."
  [{:keys [params] :as ctx}]
  (let [q      (or (:q params) (get params "q") "")
        filter (or (:filter params) (get params "filter"))
        body   (cond-> {:q q :limit 20 :attributesToHighlight ["title" "tldr" "why_interesting"]
                        :highlightPreTag "<mark>" :highlightPostTag "</mark>"}
                 filter (assoc :filter filter))]
    (try
      (let [res  (http/post (index-url ctx "/search")
                            (assoc (req-opts ctx) :body (json/generate-string body)))
            data (json/parse-string (:body res) true)]
        {:status 200 :body data})
      (catch Exception e
        (log/warn "search/search failed:" (.getMessage e))
        {:status 200 :body {:hits [] :estimatedTotalHits 0}}))))

;; ---------------------------------------------------------------------------
;; HTML search fragments

(defn- host [url]
  (try (-> url java.net.URI. .getHost (or url))
       (catch Exception _ url)))

(defn- hit-card [{:keys [id title url byline tags reading_time_min tldr] :as hit}]
  (let [display-title     (or title url)
        highlighted-title (get-in hit [:_formatted :title] display-title)
        highlighted-tldr  (get-in hit [:_formatted :tldr] tldr)]
    [:a {:href (str "/article/" id)
         :class "block group card-art p-4 hover:no-underline"}
     [:div {:class "flex items-start justify-between gap-2 mb-1"}
      [:h3 {:class "text-sm font-semibold text-stone-800 leading-snug"
            :dangerouslySetInnerHTML {:__html (or highlighted-title display-title)}}]
      (when reading_time_min
        [:span {:class "shrink-0 text-xs text-stone-400 font-mono"} (str reading_time_min "m")])]
     (when byline
       [:p {:class "text-xs text-stone-400 mb-1"} byline])
     (when highlighted-tldr
       [:p {:class "text-xs text-stone-500 leading-relaxed line-clamp-2 mb-2"
            :dangerouslySetInnerHTML {:__html highlighted-tldr}}])
     [:div {:class "flex items-center gap-1.5 flex-wrap"}
      [:span {:class "chip chip-mono text-xs"} (host url)]
      (for [t (take 3 tags)]
        [:span {:class "chip" :key t} t])]]))

(defn- palette-hit [{:keys [id title url tags reading_time_min] :as hit}]
  (let [display-title     (or title url)
        highlighted-title (get-in hit [:_formatted :title] display-title)]
    [:a {:href  (str "/article/" id)
         :class "cmd-result flex items-center gap-3 px-4 py-2.5 rounded-lg cursor-pointer hover:bg-stone-100 focus:bg-stone-100 focus:outline-none"}
     [:svg {:xmlns "http://www.w3.org/2000/svg" :width "15" :height "15" :viewBox "0 0 24 24"
            :fill "none" :stroke "#A89880" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"
            :style {:flex-shrink 0}}
      [:path {:d "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"}]
      [:polyline {:points "14 2 14 8 20 8"}]]
     [:div {:class "flex-1 min-w-0"}
      [:p {:class "text-sm font-medium text-stone-800 truncate"
           :dangerouslySetInnerHTML {:__html display-title}}]
      [:p {:class "text-xs text-stone-400 truncate"}
       (str (host url)
            (when reading_time_min (str " · " reading_time_min "m"))
            (when (seq tags) (str " · " (first tags))))]]]))

(defn- html-response [hiccup]
  {:status  200
   :headers {"content-type" "text/html; charset=UTF-8"}
   :body    (rum/render-static-markup hiccup)})

(defn- do-search [ctx q palette?]
  (let [body {:q     q
              :limit (if palette? 8 30)
              :attributesToHighlight ["title"]
              :highlightPreTag  "<mark>"
              :highlightPostTag "</mark>"}
        res  (http/post (index-url ctx "/search")
                        (assoc (req-opts ctx) :body (json/generate-string body)))
        data (json/parse-string (:body res) true)
        hits (:hits data)]
    (if palette?
      (html-response
        (if (seq hits)
          [:div {:class "py-1"}
           (map palette-hit hits)]
          [:p {:class "text-sm text-stone-400 text-center py-6"} "No results"]))
      (html-response
        (if (seq hits)
          [:<>
           [:p {:class "text-xs text-stone-400 mb-3"}
            (str (:estimatedTotalHits data) " results")]
           [:div {:class "flex flex-col gap-3"}
            (map hit-card hits)]]
          [:p {:class "text-sm text-stone-400 text-center py-8"} "No results found"])))))

(defn search-html
  "Ring handler: GET /api/search/html?q=…[&style=palette]  Returns HTML fragment."
  [{:keys [params] :as ctx}]
  (let [q       (str/trim (or (:q params) (get params "q") ""))
        palette? (= "palette" (or (:style params) (get params "style")))]
    (if (< (count q) 2)
      (html-response
        [:p {:class "text-sm text-stone-400 text-center py-6"}
         "Type at least 2 characters…"])
      (try
        (do-search ctx q palette?)
        (catch Exception e
          (log/warn "search/search-html failed:" (.getMessage e))
          (html-response
            [:p {:class "text-sm text-red-400 text-center py-6"}
             "Search unavailable"]))))))

;; ---------------------------------------------------------------------------
;; Biff startup component

(def ^:private index-settings
  {:searchableAttributes ["title" "byline" "tldr" "why_interesting" "keywords" "tags" "topic"]
   :filterableAttributes ["status" "tags" "topic" "lang"]
   :sortableAttributes   ["quality_score" "reading_time_min" "added_at"]
   :typoTolerance        {:enabled true
                          :minWordSizeForTypos {:oneTypo 4 :twoTypos 8}}
   :rankingRules         ["words" "typo" "proximity" "attribute" "sort" "exactness"
                          "quality_score:desc"]})

(def ^:private default-synonyms
  {"signal"     ["signal" "appsignal"]
   "appsignal"  ["signal" "appsignal"]
   "js"         ["js" "javascript"]
   "javascript" ["js" "javascript"]
   "ts"         ["ts" "typescript"]
   "typescript" ["ts" "typescript"]
   "k8s"        ["k8s" "kubernetes"]
   "kubernetes" ["k8s" "kubernetes"]
   "ml"         ["ml" "machine learning"]
   "ai"         ["ai" "artificial intelligence" "machine learning"]})

(defn use-meili-index
  "Biff component: ensures the Meilisearch index exists, applies settings,
   and bulk-syncs all :ready articles."
  [system]
  (future
    (try
      (log/info "meili: configuring index...")
      (ensure-index system)
      (patch-settings system index-settings)
      (put-synonyms system default-synonyms)
      (let [db       (xt/db (:biff.xtdb/node system))
            articles (->> (xt/q db '{:find  [(pull ?e [*])]
                                     :where [[?e :article/status :ready]]})
                          (map first)
                          (remove :article/deleted-at))]
        (when (seq articles)
          (log/info "meili: syncing" (count articles) "articles...")
          (doseq [batch (partition-all 50 articles)]
            (http/put (index-url system "/documents")
                      (assoc (req-opts system)
                             :body (json/generate-string (map article->doc batch)))))
          (log/info "meili: sync complete")))
      (catch Exception e
        (log/warn "meili: startup sync failed (is Meilisearch running?):" (.getMessage e)))))
  system)
