(ns com.readlater.pages.folders
  (:require [com.readlater.db :as db]
            [com.readlater.components :as c]
            [com.readlater.ui :as ui]
            [com.biffweb :as biff]
            [xtdb.api :as xt]
            [clojure.string :as str])
  (:import [java.util UUID]))

(defn folder-page [{:keys [biff/db path-params]}]
  (let [id       (some-> (:id path-params) parse-uuid)
        folder   (when id
                   (ffirst (xt/q db '{:find  [(pull ?e [*])]
                                      :in    [id]
                                      :where [[?e :xt/id id]
                                              [?e :folder/name _]]}
                                 id)))
        articles (when id
                   (->> (xt/q db '{:find  [(pull ?e [*])]
                                   :in    [fid]
                                   :where [[?e :article/folder-id fid]]}
                              id)
                        (map first)
                        (remove :article/deleted-at)
                        (sort-by :article/added-at #(compare %2 %1))))]
    (if-not folder
      (ui/on-error {:status 404})
      (ui/page (merge (db/base-page-opts db)
                      {:active           :folders
                       :active-folder-id id
                       :title            (:folder/name folder)
                       :crumbs           (str "Folders / " (:folder/name folder))})
               [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
                [:div {:class "flex items-center justify-between gap-3 mb-6"}
                 [:div {:class "flex items-center gap-3"}
                  [:span {:class "w-4 h-4 rounded-full flex-shrink-0"
                          :style {:background (:folder/color folder)}}]
                  [:h1 {:class "serif-h1 text-3xl sm:text-4xl"} (:folder/name folder)]]
                 [:button {:class              "btn btn-sm btn-ghost text-red-500 gap-1.5"
                           :hx-delete          (str "/api/folders/" id)
                           :hx-confirm         (str "Delete folder \"" (:folder/name folder) "\"? Articles will not be deleted.")
                           :hx-swap            "none"
                           :hx-on--after-request "window.location='/inbox'"}
                  [:i {:data-lucide "trash-2" :class "icon-sm"}]
                  [:span {:class "hidden sm:inline"} "Delete folder"]]]
                (if (empty? articles)
                  [:p {:class "text-sm text-stone-400 mt-10 text-center"}
                   "No articles in this folder yet. Open an article and assign it from the sidebar."]
                  [:div {:class "space-y-3"}
                   (map c/article-card articles)])]))))

(defn create-folder [{:keys [params] :as ctx}]
  (let [name  (str/trim (or (get params :name) (get params "name") ""))
        color (str/trim (or (get params :color) (get params "color") "#9B9080"))]
    (if (str/blank? name)
      {:status 400 :body "name required"}
      (let [id (UUID/randomUUID)]
        (biff/submit-tx ctx [{:db/doc-type       :folder
                              :xt/id             id
                              :folder/name       name
                              :folder/color      color
                              :folder/created-at (db/now)}])
        {:status 200 :headers {"HX-Redirect" (str "/folders/" id)}}))))

(defn delete-folder [{:keys [biff/db path-params] :as ctx}]
  (let [id (some-> (:id path-params) parse-uuid)]
    (when id
      (let [arts (->> (xt/q db '{:find  [(pull ?e [:xt/id])]
                                 :in    [fid]
                                 :where [[?e :article/folder-id fid]]}
                            id)
                      (map first))]
        (doseq [art arts]
          (biff/submit-tx ctx [{:db/op           :update
                                :db/doc-type     :article
                                :xt/id           (:xt/id art)
                                :article/folder-id nil}])))
      (biff/submit-tx ctx [[:xtdb.api/delete id]]))
    {:status 200 :headers {"HX-Redirect" "/inbox"}}))

(def routes    [["/folders/:id" {:get #'folder-page}]])
(def api-routes [["/api/folders"     {:post   #'create-folder}]
                 ["/api/folders/:id" {:delete #'delete-folder}]])
