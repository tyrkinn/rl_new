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

(defn today-batch
  "Returns today's Recommendation batch document or nil."
  [db]
  (ffirst (xt/q db '{:find  [(pull ?e [*])]
                     :in    [d]
                     :where [[?e :rec/date d]]}
                (.toString (LocalDate/now)))))

(defn articles-by-ids
  "Returns a map of uuid -> article-map for the given seq of UUIDs.
   Pulls only the fields needed for display; callers can specify a pull pattern."
  ([db ids]
   (articles-by-ids db ids '[:xt/id :article/title :article/url]))
  ([db ids pull-pattern]
   (->> ids
        (keep #(ffirst (xt/q db {:find  [(list 'pull '?e pull-pattern)]
                                  :in    '[id]
                                  :where '[[?e :xt/id id]]}
                              %)))
        (map (juxt :xt/id identity))
        (into {}))))

(defn all-rec-batches
  "Returns all Recommendation batches, sorted newest-first.
   Each batch doc shape: {:xt/id uuid :rec/date \"YYYY-MM-DD\"
                          :rec/generated-at inst? :rec/collections [...] ...}"
  [db]
  (->> (xt/q db '{:find  [(pull ?e [*])]
                  :where [[?e :rec/date _]]})
       (map first)
       (sort-by :rec/date #(compare %2 %1))))

(defn rec-batch-article-ids
  "Returns distinct article UUIDs from the library collections of a Rec batch doc."
  [batch]
  (->> (:rec/collections batch)
       (mapcat :collection/items)
       (map :item/article-id)
       distinct
       vec))

(defn articles-read-within
  "Given a seq of article UUIDs and a deadline inst, returns those
   whose :article/read-at is non-nil and not after the deadline."
  [db article-ids ^java.time.Instant deadline]
  (->> article-ids
       (keep (fn [id]
               (ffirst (xt/q db '{:find  [(pull ?e [:xt/id :article/read-at :article/status])]
                                  :in    [id]
                                  :where [[?e :xt/id id]]}
                              id))))
       (filter (fn [{:keys [article/read-at]}]
                 (and read-at
                      (not (.isAfter ^java.time.Instant read-at deadline)))))))

(defn rec-batches-with-ctr
  "Returns all Rec batches annotated with :ctr (0.0–1.0 or nil if no library items)
   and :ctr-read-count / :ctr-total-count."
  [db]
  (let [batches (all-rec-batches db)]
    (mapv (fn [batch]
            (let [ids      (rec-batch-article-ids batch)
                  total    (count ids)
                  deadline (.plusSeconds ^java.time.Instant (:rec/generated-at batch)
                                        (* 7 24 3600))]
              (if (zero? total)
                (assoc batch :ctr nil :ctr-read-count 0 :ctr-total-count 0)
                (let [read-n (count (articles-read-within db ids deadline))]
                  (assoc batch
                         :ctr             (double (/ read-n total))
                         :ctr-read-count  read-n
                         :ctr-total-count total)))))
          batches)))
