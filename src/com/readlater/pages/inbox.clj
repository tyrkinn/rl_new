(ns com.readlater.pages.inbox
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [com.readlater.url :as url]
            [com.readlater.search :as search]
            [com.readlater.worker :as worker]
            [com.biffweb :as biff]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [clojure.string :as str])
  (:import [java.util UUID]))

(defn inbox-page [{:keys [biff/db]}]
  (let [items (db/inbox-articles db)]
    (ui/page (merge (db/base-page-opts db) {:active :inbox :title "Inbox" :crumbs "Inbox"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
              [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Inbox"]
              [:p {:class "text-sm text-stone-500 mb-6"} "Articles and videos to read or watch."]
              (if (empty? items)
                [:p {:class "text-sm text-stone-400 mt-10 text-center"} "No items yet — add a URL to get started."]
                [:div {:class "space-y-3"}
                 (map c/link-card items)])])))

;; Folder chip selector — rendered server-side and swapped via htmx
(defn folder-chips [article-id current-folder-id folders]
  [:div {:id "folder-section" :class "mt-6 pt-5 border-t border-stone-100"}
   [:p {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-2.5"} "Folder"]
   (if (empty? folders)
     [:p {:class "text-xs text-stone-400"}
      "No folders yet. "
      [:button {:class   "underline hover:text-stone-600"
                :onclick "document.getElementById('create-folder-modal').showModal()"}
       "Create one"]]
     [:div {:class "flex flex-wrap gap-1.5"}
      (for [{:keys [xt/id folder/name folder/color]} folders]
        (let [active? (= id current-folder-id)]
          [:button {:class (str "flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border transition-all "
                                (if active?
                                  "border-transparent text-white shadow-sm"
                                  "border-stone-200 text-stone-600 bg-white hover:border-stone-300"))
                    :style (when active? {:background color})
                    :hx-post   (str "/api/articles/" article-id "/folder")
                    :hx-vals   (str "{\"folder-id\":\"" (if active? "" (str id)) "\"}")
                    :hx-target "#folder-section"
                    :hx-swap   "outerHTML"}
           [:span {:class "w-1.5 h-1.5 rounded-full flex-shrink-0"
                   :style {:background (if active? "rgba(255,255,255,.75)" color)}}]
           name]))])])

(defn article-page [{:keys [biff/db path-params]}]
  (let [id  (some-> (:id path-params) parse-uuid)
        art (when id
              (ffirst (xt/q db '{:find  [(pull ?e [*])]
                                 :in    [id]
                                 :where [[?e :xt/id id]
                                         [?e :article/url _]]}
                            id)))]
    (cond
      (nil? art)                (ui/on-error {:status 404})
      (:article/deleted-at art) (ui/on-error {:status 404})
      :else
      (let [{:keys [xt/id article/title article/url article/byline article/lang
                    article/status article/tldr article/tags article/topic
                    article/why-interesting article/reading-time-min
                    article/quality-score article/published-at article/error
                    article/retry-count article/comments article/folder-id]} art
            display-title (or title url)
            folders       (db/all-folders db)]
        (ui/page (merge (db/base-page-opts db)
                        {:active :inbox
                         :title  (or title "Article")
                         :crumbs (str "Inbox / " (or title "Article"))})
                 [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-5xl mx-auto w-full"}
                  (case status
                    :paywall        [:div {:class "mb-5 p-3 rounded-lg bg-amber-50 border border-amber-200 text-amber-700 text-sm"} "This article is behind a paywall."]
                    :notfound       [:div {:class "mb-5 p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm"} "Article not found (404)."]
                    :login-required [:div {:class "mb-5 p-3 rounded-lg bg-blue-50 border border-blue-200 text-blue-700 text-sm"} "Login required to access this article."]
                    :failed         [:div {:class "mb-5 p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm flex items-center justify-between"}
                                     [:span (str "Enrichment failed" (when error (str ": " error)) " (attempt " (or retry-count 0) ")")]
                                     [:button {:hx-post              (str "/api/articles/" id "/reenrich")
                                               :hx-swap              "none"
                                               :hx-on--after-request "window.location.reload()"
                                               :class                "btn btn-xs btn-outline btn-error ml-3 shrink-0"}
                                      "Retry"]]
                    nil)
                  [:div {:class "flex flex-col xl:flex-row gap-8 xl:items-start"}
                   ;; Left — article content
                   [:div {:class "flex-1 min-w-0 max-w-2xl mx-auto xl:mx-0 w-full"}
                    [:div {:class "flex items-start justify-between gap-3 mb-3"}
                     [:div {:class "flex-1 min-w-0"}
                      [:h1 {:id              "article-title"
                            :class           "serif-h1 text-2xl sm:text-3xl leading-tight cursor-text px-2 -mx-2 rounded-lg hover:bg-stone-100 focus:outline-none focus:ring-2 focus:ring-accent-200 transition-colors"
                            :contenteditable "true"
                            :data-original   display-title
                            :data-patch-url  (str "/api/articles/" id)
                            :onkeydown       "if(event.key==='Enter'){event.preventDefault();this.blur()} if(event.key==='Escape'){this.innerText=this.dataset.original;this.blur()}"
                            :onblur          "(function(el){var t=el.innerText.trim();if(!t||t===el.dataset.original)return;fetch(el.dataset.patchUrl,{method:'PATCH',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'title='+encodeURIComponent(t)}).then(function(r){if(r.ok){el.dataset.original=t;showSavedToast()}else el.innerText=el.dataset.original}).catch(function(){el.innerText=el.dataset.original})})(this)"}
                       display-title]]
                     [:div {:class "flex items-center gap-1 shrink-0"}
                      [:a {:href url :target "_blank" :rel "noopener noreferrer" :class "btn btn-sm btn-ghost gap-1.5"}
                       [:i {:data-lucide "external-link" :class "icon-sm"}]
                       [:span {:class "hidden sm:inline"} "Open"]]
                      [:button {:hx-post              (str "/api/articles/" id "/read")
                                :hx-swap              "none"
                                :hx-on--after-request "window.location='/inbox'"
                                :class                "btn btn-sm btn-ghost gap-1.5 text-emerald-700"}
                       [:i {:data-lucide "check" :class "icon-sm"}]
                       [:span {:class "hidden sm:inline"} "Mark read"]]
                      [:button {:hx-delete            (str "/api/articles/" id)
                                :hx-confirm           "Delete this article?"
                                :hx-swap              "none"
                                :hx-on--after-request "window.location='/inbox'"
                                :class                "btn btn-sm btn-ghost text-red-500"}
                       [:i {:data-lucide "trash-2" :class "icon-sm"}]
                       [:span {:class "hidden sm:inline"} "Delete"]]]]
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
                       (for [t tags] [:span {:class "chip"} t])])]
                   ;; Right — Notes + Folder
                   [:aside {:class "xl:w-72 xl:shrink-0 xl:sticky xl:top-20 flex flex-col border-t xl:border-t-0 border-stone-200 pt-6 xl:pt-0"}
                    [:p {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3"} "Notes"]
                    [:div {:id    "comments-list"
                           :class "flex flex-col gap-3 mb-4 overflow-y-auto max-h-64 xl:max-h-[58vh] pr-0.5"}
                     (map c/comment-bubble (or comments []))]
                    [:form {:hx-post              (str "/api/articles/" id "/comments")
                            :hx-target            "#comments-list"
                            :hx-swap              "beforeend"
                            :hx-on--after-request "this.reset()"}
                     [:textarea {:name        "text"
                                 :rows        3
                                 :placeholder "Add a note…"
                                 :class       "textarea textarea-bordered w-full bg-stone-50 text-sm resize-none focus:outline-none focus:border-accent-400"
                                 :onkeydown   "if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();this.form.requestSubmit()}"}]
                     [:p {:class "text-xs text-stone-400 mt-1.5 select-none"} "↵ send  ·  ⇧↵ newline"]]
                    (folder-chips id folder-id folders)]]
                  [:script
                   "(function(){var h1=document.getElementById('article-title');if(!h1)return;h1.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();h1.blur();}if(e.key==='Escape'){h1.innerText=h1.dataset.original;h1.blur();}});})();"]])))))

;; ---------------------------------------------------------------------------
;; API handlers

(defn add-url [{:keys [biff/db params] :as ctx}]
  (let [u        (or (:url params) (get params "url"))
        src      (keyword (or (:source params) (get params "source") "service"))
        norm     (url/normalize u)
        existing (when (seq norm)
                   (ffirst (xt/q db '{:find  [(pull ?e [*])]
                                      :in    [u]
                                      :where [[?e :article/url-normalized u]]}
                                 norm)))]
    (cond
      (str/blank? u)
      (c/html-frag [:p {:class "text-red-500"} "Enter a URL"])

      existing
      (c/html-frag [:p {:class "text-amber-600"}
                    "Already in library — "
                    [:a {:href (str "/article/" (:xt/id existing)) :class "underline"} "open"]])

      :else
      (let [id         (UUID/randomUUID)
            kind-param (some-> (or (:kind params) (get params "kind")) keyword)
            kind       (or (when (#{:article :video :bookmark :thread :paper} kind-param) kind-param)
                           (worker/detect-kind u)
                           :article)
            kind-label (case kind
                         :video "Video" :bookmark "Bookmark"
                         :thread "Thread" :paper "Paper" "Article")]
        (biff/submit-tx ctx [{:db/doc-type            :article
                              :xt/id                  id
                              :article/url            u
                              :article/url-normalized norm
                              :article/source         src
                              :article/kind           kind
                              :article/status         :queued
                              :article/added-at       (db/now)
                              :article/retry-count    0}])
        {:status  200
         :headers {"content-type" "text/html; charset=UTF-8"
                   "HX-Trigger"   (str "{\"showToast\":{\"type\":\"info\",\"title\":\""
                                       kind-label " saved\",\"body\":\"Enriching in the background…\"}}")}
         :body    (rum/render-static-markup
                   [:p {:class "text-emerald-600"} "Saved! Will appear in Inbox shortly."])}))))

(defn list-articles [_ctx] {:status 200 :body []})
(defn get-article   [_ctx] {:status 501 :body {:error "not implemented"}})

(defn patch-article [{:keys [path-params params] :as ctx}]
  (let [id    (some-> (:id path-params) parse-uuid)
        title (some-> (or (get params :title) (get params "title")) str/trim)]
    (if (or (nil? id) (str/blank? title))
      (c/html-frag [:span {:class "text-red-400"} "Failed to save"])
      (do
        (biff/submit-tx ctx [{:db/op :update :db/doc-type :article :xt/id id :article/title title}])
        (c/html-frag [:span "✓ Saved"])))))

(defn mark-read [{:keys [path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (if-not id
      {:status 400 :body {:error "invalid id"}}
      (do
        (biff/submit-tx ctx [{:db/op          :update
                              :db/doc-type    :article
                              :xt/id          id
                              :article/status  :read
                              :article/read-at (db/now)}])
        {:status 200 :body {:ok true}}))))

(defn add-comment [{:keys [biff/db path-params params] :as ctx}]
  (let [id      (some-> (:id path-params) parse-uuid)
        text    (str/trim (or (get params :text) (get params "text") ""))]
    (if (or (nil? id) (str/blank? text))
      {:status 400 :body "invalid"}
      (let [art     (ffirst (xt/q db '{:find  [(pull ?e [:xt/id :article/comments])]
                                       :in    [id]
                                       :where [[?e :xt/id id]]}
                                  id))
            comment {:text text :created-at (db/now)}
            updated (conj (or (:article/comments art) []) comment)]
        (biff/submit-tx ctx [{:db/op :update :db/doc-type :article :xt/id id :article/comments updated}])
        (c/html-frag (c/comment-bubble comment))))))

(defn delete-article [{:keys [path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (if-not id
      {:status 400 :body {:error "invalid id"}}
      (do
        (biff/submit-tx ctx [{:db/op           :update
                              :db/doc-type     :article
                              :xt/id           id
                              :article/deleted-at (db/now)}])
        (search/delete-doc ctx (str id))
        {:status 200 :body {:ok true}}))))

(defn set-article-folder [{:keys [biff/db path-params params] :as ctx}]
  (let [article-id    (some-> (:id path-params) parse-uuid)
        folder-id-str (str/trim (or (get params :folder-id) (get params "folder-id") ""))
        folder-id     (when (seq folder-id-str) (parse-uuid folder-id-str))
        art           (when article-id
                        (ffirst (xt/q db '{:find  [(pull ?e [:article/folder-id])]
                                          :in    [id]
                                          :where [[?e :xt/id id]]}
                                     article-id)))
        current-fid   (:article/folder-id art)
        new-fid       (when (not= folder-id current-fid) folder-id)
        folders       (db/all-folders db)]
    (when article-id
      (biff/submit-tx ctx [{:db/op             :update
                            :db/doc-type       :article
                            :xt/id             article-id
                            :article/folder-id new-fid}]))
    (c/html-frag (folder-chips article-id new-fid folders))))

(def routes
  [["/inbox"       {:get #'inbox-page}]
   ["/article/:id" {:get #'article-page}]])

(def api-routes
  [["/api/add"                    {:post   #'add-url}]
   ["/api/articles"               {:get    #'list-articles}]
   ["/api/articles/:id"           {:get    #'get-article
                                   :patch  #'patch-article
                                   :delete #'delete-article}]
   ["/api/articles/:id/comments"  {:post   #'add-comment}]
   ["/api/articles/:id/read"      {:post   #'mark-read}]
   ["/api/articles/:id/folder"    {:post   #'set-article-folder}]])
