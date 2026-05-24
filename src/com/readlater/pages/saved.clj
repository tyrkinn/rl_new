(ns com.readlater.pages.saved
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [com.readlater.search :as srch]
            [clojure.string :as str]))

(defn- grouped-view [items]
  (let [by-kind   (group-by :article/kind items)
        bookmarks (get by-kind :bookmark [])
        threads   (get by-kind :thread [])
        papers    (get by-kind :paper [])]
    (if (empty? items)
      [:p {:class "text-sm text-stone-400 mt-6 text-center"}
       "Nothing saved yet — add a URL and select Bookmark, Thread or Paper."]
      [:<>
       (when (seq bookmarks)
         [:section {:class "mb-8"}
          [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
           [:i {:data-lucide "bookmark" :class "icon-sm"}]
           (str "Bookmarks · " (count bookmarks))]
          [:div {:class "space-y-3"} (map c/bookmark-card bookmarks)]])
       (when (seq threads)
         [:section {:class "mb-8"}
          [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
           [:i {:data-lucide "message-square" :class "icon-sm"}]
           (str "Threads · " (count threads))]
          [:div {:class "space-y-3"} (map c/thread-card threads)]])
       (when (seq papers)
         [:section {:class "mb-8"}
          [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-2"}
           [:i {:data-lucide "file-text" :class "icon-sm"}]
           (str "Papers · " (count papers))]
          [:div {:class "space-y-3"} (map c/paper-card papers)]])])))

(defn- search-bar []
  [:<>
   [:div {:class "relative mb-3"}
    [:i {:data-lucide "search"
         :class "absolute left-3 top-1/2 -translate-y-1/2 icon-md text-stone-400 pointer-events-none"}]
    [:input {:id          "saved-q"
             :name        "q"
             :type        "text"
             :placeholder "Search saved…"
             :class       "input input-bordered w-full pl-9 bg-stone-50 focus:bg-white"
             :hx-get      "/api/saved/search"
             :hx-trigger  "input changed delay:300ms, search"
             :hx-target   "#saved-results"
             :hx-swap     "innerHTML"
             :hx-include  "#saved-kind"}]
    [:input {:type "hidden" :id "saved-kind" :name "kind" :value "all"}]]
   [:div {:class "flex gap-1.5 flex-wrap mb-1"}
    (for [[val label] [["all" "All"] ["bookmark" "Bookmarks"] ["thread" "Threads"] ["paper" "Papers"]]]
      [:button {:class    (str "saved-filter-chip tag-pill text-xs" (when (= val "all") " active"))
                :data-kind val
                :onclick  (str "savedFilter('" val "')")}
       label])]])

(defn saved-page [{:keys [biff/db]}]
  (let [items (db/saved-items db)]
    (ui/page (merge (db/base-page-opts db) {:active :saved :title "Saved" :crumbs "Saved"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
              [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Saved"]
              [:p {:class "text-sm text-stone-500 mb-4"} "Bookmarks, threads and papers for reference."]
              (search-bar)
              [:div {:id "saved-results" :class "mt-5"}
               (grouped-view items)]])))

(defn search-saved [{:keys [params biff/db] :as ctx}]
  (let [q    (str/trim (or (:q params) ""))
        kind (str/trim (or (:kind params) "all"))]
    (if (and (str/blank? q) (= kind "all"))
      (c/html-frag (grouped-view (db/saved-items db)))
      (let [docs (srch/saved-hits ctx)]
        (c/html-frag
          (if (nil? docs)
            [:p {:class "text-sm text-red-400 text-center py-6"} "Search unavailable"]
            (if (seq docs)
              [:div {:class "space-y-3"} (map c/link-card docs)]
              [:p {:class "text-sm text-stone-400 text-center py-8"} "Nothing found"])))))))

(def routes    [["/saved" {:get #'saved-page}]])
(def api-routes [["/api/saved/search" {:get #'search-saved}]])
