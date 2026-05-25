(ns com.readlater.db
  (:require [xtdb.api :as xt])
  (:import [java.time Instant LocalDate]
           [java.time.temporal ChronoUnit]))

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

(defn quick-reads
  "Returns inbox Readables with reading-time-min at or below max-min (default 5)."
  ([db] (quick-reads db 5))
  ([db max-min]
   (->> (inbox-articles db)
        (filter #(when-let [t (:article/reading-time-min %)]
                   (<= t max-min))))))

(defn count-quick-reads
  ([db] (count (quick-reads db)))
  ([db max-min] (count (quick-reads db max-min))))

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

(defn archive-surprise-candidates
  "Returns Readables eligible for Surprise Me: status=:read, quality-score >= min-quality,
   not read within recency-days. Falls back progressively if no results."
  ([db] (archive-surprise-candidates db {:min-quality 7 :recency-days 7}))
  ([db {:keys [min-quality recency-days]}]
   (let [cutoff (.minus (Instant/now) recency-days ChronoUnit/DAYS)]
     (->> (xt/q db '{:find  [(pull ?e [*])]
                     :in    [min-q]
                     :where [[?e :article/status :read]
                             [?e :article/quality-score ?q]
                             [(>= ?q min-q)]]}
                min-quality)
          (map first)
          (remove :article/deleted-at)
          (remove #(when-let [t (:article/read-at %)]
                     (.isAfter t cutoff)))))))

(defn random-surprise
  "Returns a random high-quality archived Readable, with progressive quality fallback."
  [db]
  (or (let [cs (archive-surprise-candidates db {:min-quality 7 :recency-days 7})]
        (when (seq cs) (rand-nth cs)))
      (let [cs (archive-surprise-candidates db {:min-quality 5 :recency-days 7})]
        (when (seq cs) (rand-nth cs)))
      (let [cs (->> (xt/q db '{:find  [(pull ?e [*])]
                               :where [[?e :article/status :read]]})
                    (map first)
                    (remove :article/deleted-at))]
        (when (seq cs) (rand-nth cs)))))

(defn app-settings [db]
  (ffirst (xt/q db '{:find [(pull ?e [*])] :where [[?e :xt/id :settings/global]]})))

(defn base-page-opts [db]
  {:inbox-count (count-inbox db)
   :queue-count (count-queue db)
   :notif-count (unread-notif-count db)
   :folders     (all-folders-with-counts db)})
