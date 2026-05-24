(ns com.readlater.app
  (:require [com.readlater.search               :as search]
            [com.readlater.db                   :as db]
            [com.readlater.components           :as c]
            [com.readlater.pages.today          :as today]
            [com.readlater.pages.inbox          :as inbox]
            [com.readlater.pages.week           :as week]
            [com.readlater.pages.tags           :as tags]
            [com.readlater.pages.archive        :as archive]
            [com.readlater.pages.queue          :as queue]
            [com.readlater.pages.folders        :as folders]
            [com.readlater.pages.search         :as search-pg]
            [com.readlater.pages.settings       :as settings-pg]
            [com.readlater.pages.notifications  :as notifications]
            [com.readlater.pages.saved          :as saved]
            [com.readlater.pages.roadmap        :as roadmap]
            [com.readlater.pages.features       :as features]))

(defn health [{:keys [biff/db]}]
  {:status 200 :body {:ok true :db_ok (boolean db) :version "0.1.0"}})

(defn nav-counts [{:keys [biff/db]}]
  (let [inbox (db/count-inbox db)
        queue (db/count-queue db)
        notif (db/unread-notif-count db)]
    (c/html-frag
     [:<>
      [:span {:id "badge-inbox" :hx-swap-oob "true"
              :class (str "ml-auto chip chip-mono" (when-not (pos? inbox) " hidden"))}
       (when (pos? inbox) (str inbox))]
      [:span {:id "badge-queue" :hx-swap-oob "true"
              :class (str "ml-auto chip chip-mono" (when-not (pos? queue) " hidden"))}
       (when (pos? queue) (str queue))]
      [:span {:id "badge-notif" :hx-swap-oob "true"
              :class (str "notif-bell-badge" (when-not (pos? notif) " hidden"))}
       (when (pos? notif) (str notif))]])))


(def module
  {:routes
   (into [] cat [today/routes
                 inbox/routes
                 saved/routes
                 week/routes
                 tags/routes
                 archive/routes
                 queue/routes
                 folders/routes
                 search-pg/routes
                 settings-pg/routes
                 roadmap/routes
                 features/routes])

   :api-routes
   (into [] cat [today/api-routes
                 inbox/api-routes
                 saved/api-routes
                 tags/api-routes
                 archive/api-routes
                 queue/api-routes
                 folders/api-routes
                 notifications/api-routes
                 search-pg/api-routes
                 settings-pg/api-routes
                 [["/api/search"      {:get #'search/search}]
                  ["/api/search/html" {:get #'search/search-html}]
                  ["/api/nav-counts"  {:get #'nav-counts}]
                  ["/health"          {:get #'health}]]])})
