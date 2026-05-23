(ns com.readlater.llm
  "Wrapper around the `claude` CLI. Spawns a process, passes prompt via -p,
   extracts the first balanced JSON object from stdout."
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(defn- extract-first-json
  "Find the first balanced {...} object in s. Returns the JSON string or nil."
  [s]
  (when (string? s)
    (loop [i      (.indexOf s "{")
           depth  0
           start  nil
           in-str false
           esc    false]
      (cond
        (neg? i)        nil
        (>= i (count s)) nil
        :else
        (let [c (.charAt s i)]
          (cond
            esc      (recur (inc i) depth start in-str false)
            in-str   (cond
                       (= c \\) (recur (inc i) depth start true true)
                       (= c \") (recur (inc i) depth start false false)
                       :else    (recur (inc i) depth start true false))
            (= c \") (recur (inc i) depth start true false)
            (= c \{) (recur (inc i) (inc depth) (or start i) false false)
            (= c \}) (let [d (dec depth)]
                       (if (zero? d)
                         (subs s start (inc i))
                         (recur (inc i) d start false false)))
            :else    (recur (inc i) depth start false false)))))))

(defn invoke
  "Run the claude CLI with prompt. Returns {:ok? bool :data {} :raw \"\" :error \"\"}.

   opts:
     :bin           path to claude binary (default \"claude\")
     :allowed-tools string or vec of tool names
     :timeout-ms    process timeout (default 180 000)"
  [prompt {:keys [bin allowed-tools timeout-ms]
           :or   {bin "claude" timeout-ms 180000}}]
  (let [tools-arg (cond
                    (string? allowed-tools) ["--allowedTools" allowed-tools]
                    (vector? allowed-tools) ["--allowedTools" (str/join "," allowed-tools)]
                    :else [])
        ;; No --output-format json: stdout = Claude's raw text response only.
        ;; With --output-format json Claude streams multiple JSON events to stdout
        ;; (system/assistant/tool/result), making it hard to reliably find our JSON.
        cmd  (concat [bin "--dangerously-skip-permissions" "-p" prompt] tools-arg)
        _    (log/debug "llm/invoke" (count prompt) "chars")
        {:keys [exit out err]} (apply sh/sh cmd)]
    (if-not (zero? exit)
      {:ok? false :raw out :error (str "claude exit " exit ": " (str/trim err))}
      (let [j (extract-first-json out)]
        (if-not j
          (do
            (log/warn "llm/invoke: no JSON in output. First 300 chars:"
                      (subs out 0 (min 300 (count out))))
            {:ok? false :raw out :error "no JSON in claude output"})
          (try
            {:ok? true :data (json/parse-string j true) :raw out}
            (catch Exception e
              {:ok? false :raw out :error (str "JSON parse: " (.getMessage e))})))))))
