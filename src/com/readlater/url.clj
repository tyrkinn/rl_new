(ns com.readlater.url
  (:require [clojure.string :as str])
  (:import [java.net URI]))

(def ^:private tracking-params
  #{"utm_source" "utm_medium" "utm_campaign" "utm_term" "utm_content"
    "fbclid" "gclid" "ref" "ref_src" "ref_url" "mc_cid" "mc_eid"
    "_ga" "yclid" "igshid"})

(defn- strip-query [q]
  (when q
    (let [pairs (->> (str/split q #"&")
                     (remove str/blank?)
                     (map #(str/split % #"=" 2))
                     (remove (fn [[k]] (contains? tracking-params (str/lower-case k))))
                     (sort-by first))]
      (when (seq pairs)
        (->> pairs (map #(str/join "=" %)) (str/join "&"))))))

(defn normalize
  "Strip fragment, tracking params, trailing slash, lowercase host."
  [u]
  (when (string? u)
    (try
      (let [uri    (URI. u)
            host   (some-> (.getHost uri) str/lower-case)
            path   (or (.getPath uri) "/")
            path'  (if (and (> (count path) 1) (str/ends-with? path "/"))
                     (subs path 0 (dec (count path)))
                     path)
            q      (strip-query (.getQuery uri))
            scheme (or (.getScheme uri) "https")]
        (cond-> (str scheme "://" host path')
          q (str "?" q)))
      (catch Exception _ u))))
