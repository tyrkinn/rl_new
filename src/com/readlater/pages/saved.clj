(ns com.readlater.pages.saved
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]))

(defn saved-page [{:keys [biff/db]}]
  (let [items     (db/saved-items db)
        by-kind   (group-by :article/kind items)
        bookmarks (get by-kind :bookmark [])
        threads   (get by-kind :thread [])
        papers    (get by-kind :paper [])]
    (ui/page (merge (db/base-page-opts db) {:active :saved :title "Saved" :crumbs "Saved"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
              [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Saved"]
              [:p {:class "text-sm text-stone-500 mb-6"} "Bookmarks, threads and papers for reference."]
              (if (empty? items)
                [:p {:class "text-sm text-stone-400 mt-10 text-center"}
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
                    [:div {:class "space-y-3"} (map c/paper-card papers)]])])])))

(def routes    [["/saved" {:get #'saved-page}]])
(def api-routes [])
