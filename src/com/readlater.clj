(ns com.readlater
  (:require [com.biffweb :as biff]
            [com.readlater.app :as app]
            [com.readlater.middleware :as mid]
            [com.readlater.ui :as ui]
            [com.readlater.worker :as worker]
            [com.readlater.search :as search]
            [com.readlater.schema :as schema]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [malli.core :as malc]
            [malli.registry :as malr]
            [nrepl.cmdline :as nrepl-cmd])
  (:gen-class))

(def modules
  [app/module
   schema/module
   worker/module])

(def routes
  [["" {:middleware [mid/wrap-site-defaults]}
    (keep :routes modules)]
   ["" {:middleware [mid/wrap-api-defaults]}
    (keep :api-routes modules)]])

(def handler
  (-> (biff/reitit-handler {:routes routes})
      mid/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static modules)))

(defn generate-assets! [ctx]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [ctx]
  (biff/add-libs ctx)
  (biff/eval-files! ctx)
  (generate-assets! ctx)
  (test/run-all-tests #"com.readlater.*-test"))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge (keep :schema modules)))})

(def initial-system
  {:biff/modules            #'modules
   :biff/handler            #'handler
   :biff/malli-opts         #'malli-opts
   :biff.beholder/on-save   #'on-save
   :biff.middleware/on-error #'ui/on-error
   :biff.xtdb/tx-fns        biff/tx-fns})

(defonce system (atom {}))

(def components
  [biff/use-aero-config
   biff/use-xtdb
   worker/use-pending-drain
   worker/use-kind-migration
   worker/use-enrich-worker
   search/use-meili-index
   biff/use-queues
   biff/use-xtdb-tx-listener
   biff/use-htmx-refresh
   biff/use-jetty
   biff/use-chime
   biff/use-beholder])

(defn start []
  (let [new-system (reduce (fn [sys component]
                             (log/info "starting:" (str component))
                             (component sys))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "Readlater started at" (:biff/base-url new-system))
    new-system))

(defn -main []
  (let [{:keys [biff.nrepl/args]} (start)]
    (apply nrepl-cmd/-main args)))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
