(ns com.readlater.db
  (:require [xtdb.api :as xt])
  (:import [java.time Instant]))

(defn now [] (Instant/now))

(defn- inbox-kind? [k]
  (#{:article :video nil} k))

(defn- saved-kind? [k]
  (#{:bookmark :thread :paper} k))

(defn inbox-articles [db]
  (->> (xt/q db '{:find  [(pull ?e [*])]
                  :where [[?e :article/status _]]})
       (map first)
       (filter #(and (= :ready (:article/status %))
                     (inbox-kind? (:article/kind %))))
       (remove :article/deleted-at)
       (sort-by :article/added-at #(compare %2 %1))))

(defn saved-items [db]
  (->> (xt/q db '{:find  [(pull ?e [*])]
                  :where [[?e :article/status _]]})
       (map first)
       (filter #(and (= :ready (:article/status %))
                     (saved-kind? (:article/kind %))))
       (remove :article/deleted-at)
       (sort-by :article/added-at #(compare %2 %1))))

(defn count-inbox [db]
  (count (inbox-articles db)))

(defn count-queue [db]
  (->> (xt/q db '{:find  [(pull ?e [:article/status :article/deleted-at])]
                  :where [[?e :article/status _]]})
       (map first)
       (remove :article/deleted-at)
       (filter #(#{:queued :enriching :failed} (:article/status %)))
       count))

(defn all-folders [db]
  (->> (xt/q db '{:find  [(pull ?e [*])]
                  :where [[?e :folder/name _]]})
       (map first)
       (sort-by :folder/created-at)))

(defn all-folders-with-counts [db]
  (let [folders       (all-folders db)
        folder-counts (->> (xt/q db '{:find  [(pull ?e [:article/folder-id :article/deleted-at])]
                                      :where [[?e :article/folder-id _]]})
                           (map first)
                           (remove :article/deleted-at)
                           (group-by :article/folder-id)
                           (map (fn [[fid arts]] [fid (count arts)]))
                           (into {}))]
    (mapv (fn [f] (assoc f :article-count (get folder-counts (:xt/id f) 0))) folders)))

(defn unread-notif-count [db]
  (count (xt/q db '{:find [?e]
                    :where [[?e :notif/type _]
                            [?e :notif/read false]]})))

(defn recent-notifications [db]
  (->> (xt/q db '{:find [(pull ?e [*])]
                  :where [[?e :notif/type _]]})
       (map first)
       (sort-by :notif/created-at #(compare %2 %1))
       (take 50)))

(defn app-settings [db]
  (ffirst (xt/q db '{:find [(pull ?e [*])] :where [[?e :xt/id :settings/global]]})))

(defn base-page-opts [db]
  {:inbox-count (count-inbox db)
   :queue-count (count-queue db)
   :notif-count (unread-notif-count db)
   :folders     (all-folders-with-counts db)})
