(ns com.readlater.pages.tags
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [xtdb.api :as xt]))

(defn tags-page [{:keys [biff/db]}]
  (let [tag-groups (->> (xt/q db '{:find  [(pull ?e [:xt/id :article/status :article/deleted-at :article/tags])]
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
    (ui/page (merge (db/base-page-opts db) {:active :tags :title "Tags" :crumbs "Tags"})
             [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-4xl mx-auto w-full"}
              [:div {:class "flex items-baseline gap-3 mb-8"}
               [:h1 {:class "serif-h1 text-3xl sm:text-4xl"} "Tags"]
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
                      [:button {:class    "tag-pill"
                                :hx-get   (str "/api/tags/" enc)
                                :hx-target "#tag-articles"
                                :hx-swap  "innerHTML"
                                :onclick  "document.querySelectorAll('.tag-pill').forEach(function(e){e.classList.remove('active')});this.classList.add('active')"
                                :style    {:font-size (str font-px "px")}}
                       tag
                       [:span {:class "tag-badge"} (str count)]]))]
                 [:div {:id "tag-articles" :class "mt-8"}]])])))

(defn tag-articles [{:keys [biff/db path-params]}]
  (let [tag      (some-> (:tag path-params) (java.net.URLDecoder/decode "UTF-8"))
        articles (->> (xt/q db '{:find  [(pull ?e [*])]
                                 :where [[?e :article/status _]]})
                      (map first)
                      (remove :article/deleted-at)
                      (filter #(some #{tag} (:article/tags %)))
                      (sort-by :article/added-at #(compare %2 %1)))]
    (c/html-frag
     [:div
      [:div {:class "flex items-center gap-2 mb-5"}
       [:h2 {:class "font-serif text-2xl font-semibold"} tag]
       [:span {:class "chip chip-mono"} (str (count articles) " articles")]]
      (if (empty? articles)
        [:p {:class "text-sm text-stone-400"} "No articles with this tag."]
        [:div {:class "space-y-3"} (map c/article-card articles)])])))

(defn tag-counts [_ctx] {:status 200 :body []})

(def routes    [["/tags" {:get #'tags-page}]])
(def api-routes [["/api/tags"      {:get #'tag-counts}]
                 ["/api/tags/:tag" {:get #'tag-articles}]])
