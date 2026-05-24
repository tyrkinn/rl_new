(ns com.readlater.pages.search
  (:require [com.readlater.db :as db]
            [com.readlater.ui :as ui]
            [clojure.string :as str]))

(defn search-page [{:keys [biff/db params]}]
  (let [q (str/trim (or (:q params) ""))]
    (ui/page (merge (db/base-page-opts db) {:active :search :title "Search" :crumbs "Search"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
              [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Search"]
              [:p {:class "text-sm text-stone-400 mb-6"}
               "Full-text search across titles, summaries, tags and keywords."]
              [:div {:class "relative mb-6"}
               [:i {:data-lucide "search"
                    :class       "absolute left-3 top-1/2 -translate-y-1/2 icon-md text-stone-400 pointer-events-none"}]
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

(def routes    [["/search" {:get #'search-page}]])
(def api-routes [])
