(ns com.readlater.pages.inbox
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [com.readlater.url :as url]
            [com.readlater.search :as search]
            [com.readlater.worker :as worker]
            [com.readlater.llm :as llm]
            [com.biffweb :as biff]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]))

(def ^:private summary-prompt-tpl
  (delay
    (if-let [r (io/resource "prompts/article-summary.md")]
      (slurp r)
      (throw (ex-info "prompts/article-summary.md not found" {})))))

(def ^:private kind-options
  [[:article  "article"  "file"]
   [:video    "video"    "play-circle"]
   [:bookmark "bookmark" "bookmark"]
   [:thread   "thread"   "message-square"]
   [:paper    "paper"    "file-text"]])

(defn- kind-badge-section [article-id kind]
  [:div {:id "kind-badge"}
   [:button {:class     "p-0 bg-transparent border-0 cursor-pointer inline-flex items-center gap-1 group"
             :title     "Change type"
             :hx-get    (str "/api/articles/" article-id "/kind-picker")
             :hx-target "#kind-badge"
             :hx-swap   "outerHTML"}
    (c/kind-badge (or kind :article))
    [:i {:data-lucide "pencil"
         :class       "icon-sm text-stone-300 opacity-0 group-hover:opacity-100 transition-opacity"}]]])

(defn- kind-picker-section [article-id current-kind]
  [:div {:id "kind-badge" :class "flex items-center gap-1 flex-wrap"}
   (for [[k label icon] kind-options]
     (let [active? (= k (or current-kind :article))]
       [:button {:class     (str "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide border transition-all "
                                 (if active?
                                   "bg-stone-800 text-white border-stone-800"
                                   "bg-white text-stone-500 border-stone-200 hover:border-stone-400"))
                 :hx-post   (str "/api/articles/" article-id "/kind")
                 :hx-vals   (str "{\"kind\":\"" label "\"}")
                 :hx-target "#kind-badge"
                 :hx-swap   "outerHTML"}
        [:i {:data-lucide icon :class "icon-sm"}]
        label]))])

(defn- render-summary-section [article-id summary]
  [:div {:id "summary-section" :class "mb-6 mt-2"}
   [:div {:class "flex items-center justify-between mb-2"}
    [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400"} "Detailed Summary"]
    [:button {:class            "btn btn-xs btn-ghost text-stone-400 gap-1"
              :hx-post          (str "/api/articles/" article-id "/summarize")
              :hx-target        "#summary-section"
              :hx-swap          "outerHTML"}
     [:i {:data-lucide "refresh-cw" :class "icon-sm"}]
     "Regenerate"]]
   [:div {:class "space-y-3"}
    (for [para (remove str/blank? (str/split summary #"\n\n+"))]
      [:p {:class "text-sm text-stone-700 leading-relaxed"} para])]])

(defn- render-summary-button [article-id]
  [:div {:id "summary-section" :class "mb-6"}
   [:button {:class     "btn btn-sm btn-outline gap-2 text-stone-600 border-stone-300 hover:bg-stone-50"
             :hx-post   (str "/api/articles/" article-id "/summarize")
             :hx-target "#summary-section"
             :hx-swap   "outerHTML"}
    [:i {:data-lucide "sparkles" :class "icon-sm"}]
    "Generate detailed summary"]])

(defn- render-summary-pending [article-id]
  [:div {:id         "summary-section"
         :class      "mb-6"
         :hx-get     (str "/api/articles/" article-id "/summary-status")
         :hx-trigger "every 3s"
         :hx-swap    "outerHTML"}
   [:button {:class    "btn btn-sm btn-outline gap-2 text-stone-400 border-stone-200 cursor-not-allowed"
             :disabled true}
    [:span {:class "loading loading-spinner loading-xs"}]
    "Summarizing…"]])

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
                    article/retry-count article/comments article/folder-id
                    article/kind article/full-summary article/summary-status]} art
            display-title (or title url)
            folders       (db/all-folders db)
            inbox-items   (db/inbox-articles db)
            entry-ids     (mapv :xt/id inbox-items)
            cur-pos       (.indexOf entry-ids id)
            prev-id       (when (> cur-pos 0)
                            (nth entry-ids (dec cur-pos)))
            next-id       (when (and (>= cur-pos 0) (< cur-pos (dec (count entry-ids))))
                            (nth entry-ids (inc cur-pos)))]
        (ui/page (merge (db/base-page-opts db)
                        {:active :inbox
                         :title  (or title "Article")
                         :crumbs (str "Inbox / " (or title "Article"))})
                 [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-5xl mx-auto w-full"}
                  [:div {:class "scroll-progress"}]
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
                   [:div {:id "article-content" :class "flex-1 min-w-0 max-w-2xl mx-auto xl:mx-0 w-full"}
                    [:h1 {:id              "article-title"
                          :class           "serif-h1 text-2xl sm:text-3xl leading-tight cursor-text px-2 -mx-2 rounded-lg hover:bg-stone-100 focus:outline-none focus:ring-2 focus:ring-accent-200 transition-colors mb-3"
                          :contenteditable "false"
                          :data-original   display-title
                          :data-patch-url  (str "/api/articles/" id)
                          :onclick         "if(this.contentEditable!=='true'){this.contentEditable='true';var r=document.createRange(),s=window.getSelection();r.selectNodeContents(this);r.collapse(false);s.removeAllRanges();s.addRange(r)}"
                          :onkeydown       "if(event.key==='Enter'){event.preventDefault();this.blur()} if(event.key==='Escape'){this.innerText=this.dataset.original;this.blur()}"
                          :onblur          "(function(el){el.contentEditable='false';var t=el.innerText.trim();if(!t||t===el.dataset.original)return;fetch(el.dataset.patchUrl,{method:'PATCH',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'title='+encodeURIComponent(t)}).then(function(r){if(r.ok){el.dataset.original=t;showSavedToast()}else el.innerText=el.dataset.original}).catch(function(){el.innerText=el.dataset.original})})(this)"}
                     display-title]
                    [:div {:id "article-actions" :class "flex items-center gap-1 mb-3 flex-wrap"}
                     [:a {:href url :target "_blank" :rel "noopener noreferrer" :class "btn btn-sm btn-ghost gap-1.5"}
                      [:i {:data-lucide "external-link" :class "icon-sm"}]
                      [:span {:class "hidden sm:inline"} "Open"]]
                     [:button {:class    "btn btn-sm btn-ghost gap-1.5"
                               :title    "Copy link"
                               :data-url url
                               :onclick  "navigator.clipboard.writeText(this.dataset.url);showToast({type:'success',title:'Link copied'})"}
                      [:i {:data-lucide "link" :class "icon-sm"}]
                      [:span {:class "hidden sm:inline"} "Copy link"]]
                     (when (contains? #{nil :article :video :paper} kind)
                       (if (= status :read)
                         [:button {:hx-post              (str "/api/articles/" id "/unread")
                                   :hx-swap              "none"
                                   :hx-on--after-request "window.location.reload()"
                                   :class                "btn btn-sm btn-ghost gap-1.5 text-stone-500"
                                   :data-shortcut        "mark-read"}
                          [:i {:data-lucide "rotate-ccw" :class "icon-sm"}]
                          [:span {:class "hidden sm:inline"} "Move to unread"]]
                         [:button {:hx-post              (str "/api/articles/" id "/read")
                                   :hx-swap              "none"
                                   :hx-on--after-request "window.location='/inbox'"
                                   :class                "btn btn-sm btn-ghost gap-1.5 text-emerald-700"
                                   :data-shortcut        "mark-read"}
                          [:i {:data-lucide "check" :class "icon-sm"}]
                          [:span {:class "hidden sm:inline"} "Mark read"]]))
                     [:button {:hx-delete            (str "/api/articles/" id)
                               :hx-confirm           "Delete this entry?"
                               :hx-swap              "none"
                               :hx-on--after-request "window.location='/inbox'"
                               :class                "btn btn-sm btn-ghost text-red-500"
                               :data-shortcut        "delete"}
                      [:i {:data-lucide "trash-2" :class "icon-sm"}]
                      [:span {:class "hidden sm:inline"} "Delete"]]
                     [:button {:id      "focus-toggle"
                               :class   "btn btn-sm btn-ghost gap-1.5"
                               :title   "Focus mode (f)"
                               :onclick "toggleFocusMode()"}
                      [:i {:data-lucide "focus" :class "icon-sm"}]
                      [:span {:class "hidden sm:inline"} "Focus"]]
                     [:button {:class   "btn btn-sm btn-ghost gap-1.5"
                               :title   "Keyboard shortcuts (?)"
                               :onclick "document.getElementById('shortcuts-help').showModal()"}
                      [:i {:data-lucide "keyboard" :class "icon-sm"}]
                      [:span {:class "hidden sm:inline"} "Shortcuts"]]]
                    [:div {:id "article-meta" :class "flex items-center gap-2 text-sm text-stone-500 mb-6 flex-wrap"}
                     (kind-badge-section id kind)
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
                    (when (= kind :article)
                      (cond
                        (seq full-summary)              (render-summary-section id full-summary)
                        (= summary-status :pending)     (render-summary-pending id)
                        :else                           (render-summary-button id)))
                    (when (or topic (seq tags))
                      [:div {:class "flex flex-wrap gap-1.5"}
                       (when topic [:span {:class "chip font-medium"} topic])
                       (for [t tags] [:span {:class "chip"} t])])]
                   ;; Right — Notes + Folder
                   [:aside {:id "article-aside" :class "xl:w-72 xl:shrink-0 xl:sticky xl:top-20 flex flex-col border-t xl:border-t-0 border-stone-200 pt-6 xl:pt-0"}
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
                  [:span {:id            "entry-nav"
                          :data-prev-url (when prev-id (str "/article/" prev-id))
                          :data-next-url (when next-id (str "/article/" next-id))
                          :style         "display:none"
                          :aria-hidden   "true"}]
                  [:dialog {:id "shortcuts-help" :class "modal"}
                   [:div {:class "modal-box max-w-sm rounded-xl bg-white border border-stone-200"}
                    [:h3 {:class "font-serif text-xl mb-4"} "Keyboard shortcuts"]
                    [:table {:class "w-full text-sm"}
                     [:tbody
                      (for [[k label] [["r" "Mark as read / unread"]
                                       ["n" "Next entry"]
                                       ["p" "Previous entry"]
                                       ["d" "Delete"]
                                       ["s" "Add a note (Comment)"]
                                       ["f" "Toggle focus mode"]
                                       ["?" "Show this help"]
                                       ["Esc" "Exit focus mode / close"]]]
                        [:tr {:class "border-b border-stone-100 last:border-0"}
                         [:td {:class "py-2 pr-4 w-12"}
                          [:kbd {:class "px-2 py-0.5 rounded text-xs font-mono bg-stone-100 border border-stone-200 text-stone-700"} k]]
                         [:td {:class "py-2 text-stone-600"} label]])]]
                    [:div {:class "flex justify-end mt-4"}
                     [:button {:class   "btn btn-ghost btn-sm"
                               :onclick "document.getElementById('shortcuts-help').close()"}
                      "Close"]]]
                   [:form {:method "dialog" :class "modal-backdrop"}
                    [:button "close"]]]
                  [:script
                   "(function(){
  function isEditing(){
    var tag=document.activeElement&&document.activeElement.tagName;
    var ce=document.activeElement&&document.activeElement.isContentEditable;
    return tag==='INPUT'||tag==='TEXTAREA'||tag==='SELECT'||ce;
  }
  function nav(){return document.getElementById('entry-nav');}
  var FOCUS_KEY='rl:focus-mode';
  function setFocusMode(on){
    document.body.classList.toggle('focus-mode',on);
    localStorage.setItem(FOCUS_KEY,on?'1':'0');
    var btn=document.getElementById('focus-toggle');
    if(!btn)return;
    var icon=btn.querySelector('[data-lucide]');
    if(icon){icon.setAttribute('data-lucide',on?'minimize-2':'focus');lucide.createIcons({nodes:[icon]});}
  }
  window.toggleFocusMode=function(){setFocusMode(!document.body.classList.contains('focus-mode'));};
  if(localStorage.getItem(FOCUS_KEY)==='1')setFocusMode(true);
  window.toggleShortcutsHelp=function(){
    var d=document.getElementById('shortcuts-help');
    if(!d)return;
    d.open?d.close():d.showModal();
  };
  document.addEventListener('keydown',function(e){
    if(e.metaKey||e.ctrlKey||e.altKey)return;
    if(e.key==='Escape'){
      if(document.body.classList.contains('focus-mode')){setFocusMode(false);e.stopPropagation();}
      return;
    }
    if(isEditing())return;
    if(e.key==='?'){e.preventDefault();toggleShortcutsHelp();return;}
    if(e.key==='f'){e.preventDefault();toggleFocusMode();return;}
    if(e.key==='r'){e.preventDefault();var b=document.querySelector('[data-shortcut=\"mark-read\"]');if(b)b.click();return;}
    if(e.key==='d'){e.preventDefault();var b=document.querySelector('[data-shortcut=\"delete\"]');if(b)b.click();return;}
    if(e.key==='s'){e.preventDefault();var ta=document.querySelector('#article-aside textarea');if(ta)ta.focus();return;}
    if(e.key==='n'){e.preventDefault();var n=nav();if(n&&n.dataset.nextUrl)window.location=n.dataset.nextUrl;return;}
    if(e.key==='p'){e.preventDefault();var n=nav();if(n&&n.dataset.prevUrl)window.location=n.dataset.prevUrl;return;}
  });
  var h1=document.getElementById('article-title');
  if(h1){
    h1.addEventListener('keydown',function(e){
      if(e.key==='Enter'){e.preventDefault();h1.blur();}
      if(e.key==='Escape'){h1.innerText=h1.dataset.original;h1.blur();}
    });
  }
})();"]])))))

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

(defn mark-read [{:keys [biff/db path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (if-not id
      {:status 400 :body {:error "invalid id"}}
      (do
        (biff/submit-tx ctx [{:db/op          :update
                              :db/doc-type    :article
                              :xt/id          id
                              :article/status  :read
                              :article/read-at (db/now)}])
        (when-let [art (xt/pull db '[*] id)]
          (search/index-doc ctx (assoc art :article/status :read)))
        {:status 200 :body {:ok true}}))))

(defn mark-unread [{:keys [biff/db path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (if-not id
      {:status 400 :body {:error "invalid id"}}
      (do
        (biff/submit-tx ctx [{:db/op          :update
                              :db/doc-type    :article
                              :xt/id          id
                              :article/status  :ready
                              :article/read-at nil}])
        (when-let [art (xt/pull db '[*] id)]
          (search/index-doc ctx (assoc art :article/status :ready)))
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

(defn summarize-article [{:keys [biff/db path-params] :as ctx}]
  (let [id  (some-> (:id path-params) parse-uuid)
        art (when id (ffirst (xt/q db '{:find  [(pull ?e [:xt/id :article/url :article/title])]
                                         :in    [id]
                                         :where [[?e :xt/id id]
                                                 [?e :article/url _]]}
                                   id)))]
    (if-not art
      {:status 404 :body "not found"}
      (do
        (biff/submit-tx ctx [{:db/op                  :update
                              :db/doc-type            :article
                              :xt/id                  id
                              :article/summary-status :pending}])
        (future
          (let [prompt (str/replace @summary-prompt-tpl "<URL>" (:article/url art))
                {:keys [ok? data error]} (llm/invoke prompt {:allowed-tools "WebFetch"})]
            (if ok?
              (let [summary (str/trim (or (:summary data) ""))]
                (if (seq summary)
                  (do
                    (biff/submit-tx ctx [{:db/op                  :update
                                          :db/doc-type            :article
                                          :xt/id                  id
                                          :article/full-summary   summary
                                          :article/summary-status :done}])
                    (biff/submit-tx ctx [{:db/doc-type      :notification
                                          :xt/id            (UUID/randomUUID)
                                          :notif/type       :success
                                          :notif/title      (str "Summary ready: " (or (:article/title art) "Article"))
                                          :notif/body       "Подробное саммари было сгенерировано."
                                          :notif/link       (str "/article/" id)
                                          :notif/read       false
                                          :notif/created-at (db/now)}]))
                  (biff/submit-tx ctx [{:db/op                  :update
                                        :db/doc-type            :article
                                        :xt/id                  id
                                        :article/summary-status :failed}])))
              (do
                (log/warn "summarize-article failed:" error)
                (biff/submit-tx ctx [{:db/op                  :update
                                      :db/doc-type            :article
                                      :xt/id                  id
                                      :article/summary-status :failed}])))))
        (c/html-frag (render-summary-pending id))))))

(defn summary-status [{:keys [biff/db path-params]}]
  (let [id  (some-> (:id path-params) parse-uuid)
        art (when id (ffirst (xt/q db '{:find  [(pull ?e [:xt/id :article/full-summary :article/summary-status])]
                                         :in    [id]
                                         :where [[?e :xt/id id]]}
                                   id)))]
    (if-not art
      {:status 404 :body "not found"}
      (let [{:keys [article/full-summary article/summary-status]} art]
        (c/html-frag
          (cond
            (seq full-summary)              (render-summary-section id full-summary)
            (= summary-status :pending)     (render-summary-pending id)
            (= summary-status :failed)      [:div {:id "summary-section" :class "mb-6 space-y-2"}
                                             [:p {:class "text-sm text-red-500"} "Не удалось сгенерировать саммари."]
                                             (render-summary-button id)]
            :else                           (render-summary-button id)))))))

(defn kind-picker-handler [{:keys [biff/db path-params]}]
  (let [id  (some-> (:id path-params) parse-uuid)
        art (when id (ffirst (xt/q db '{:find [(pull ?e [:article/kind])]
                                         :in    [id]
                                         :where [[?e :xt/id id]]}
                                   id)))]
    (if-not art
      {:status 404 :body "not found"}
      (c/html-frag (kind-picker-section id (:article/kind art))))))

(defn update-kind [{:keys [path-params params] :as ctx}]
  (let [id       (some-> (:id path-params) parse-uuid)
        new-kind (some-> (or (:kind params) (get params "kind")) keyword)]
    (when (and id (#{:article :video :bookmark :thread :paper} new-kind))
      (biff/submit-tx ctx [{:db/op :update :db/doc-type :article :xt/id id :article/kind new-kind}]))
    (c/html-frag (kind-badge-section id new-kind))))

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
   ["/api/articles/:id/unread"    {:post   #'mark-unread}]
   ["/api/articles/:id/folder"    {:post   #'set-article-folder}]
   ["/api/articles/:id/summarize"       {:post #'summarize-article}]
   ["/api/articles/:id/summary-status"  {:get  #'summary-status}]
   ["/api/articles/:id/kind-picker"     {:get  #'kind-picker-handler}]
   ["/api/articles/:id/kind"            {:post #'update-kind}]])
