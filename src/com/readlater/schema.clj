(ns com.readlater.schema)

(def schema
  {:article/id :uuid
   :article
   [:map {:closed true}
    [:xt/id                                          :article/id]
    [:article/url                                    :string]
    [:article/url-normalized                         :string]
    [:article/source        [:enum :service :cli :bookmarklet]]
    [:article/status        [:enum :queued :enriching :ready :read
                             :archived :failed :paywall :notfound
                             :login-required :deleted]]
    [:article/title         {:optional true} [:maybe :string]]
    [:article/byline        {:optional true} [:maybe :string]]
    [:article/lang          {:optional true} [:maybe :string]]
    [:article/published-at  {:optional true} [:maybe inst?]]
    [:article/tldr          {:optional true} [:maybe [:vector :string]]]
    [:article/tags          {:optional true} [:maybe [:vector :string]]]
    [:article/topic         {:optional true} [:maybe :string]]
    [:article/why-interesting {:optional true} [:maybe :string]]
    [:article/reading-time-min {:optional true} [:maybe :int]]
    [:article/quality-score    {:optional true} [:maybe :int]]
    [:article/keywords      {:optional true} [:maybe [:vector :string]]]
    [:article/kind          {:optional true} [:maybe [:enum :article :video :bookmark :thread :paper]]]
    [:article/folder-id     {:optional true} [:maybe :uuid]]
    [:article/channel       {:optional true} [:maybe :string]]
    [:article/platform      {:optional true} [:maybe :string]]
    [:article/duration-min  {:optional true} [:maybe :int]]
    [:article/category      {:optional true} [:maybe :string]]
    [:article/author-handle {:optional true} [:maybe :string]]
    [:article/key-findings  {:optional true} [:maybe [:vector :string]]]
    [:article/paper-authors {:optional true} [:maybe [:vector :string]]]
    [:article/field         {:optional true} [:maybe :string]]
    [:article/synonyms      {:optional true} [:maybe [:vector :string]]]
    [:article/full-summary    {:optional true} [:maybe :string]]
    [:article/summary-status  {:optional true} [:maybe [:enum :pending :done :failed]]]
    [:article/comments      {:optional true} [:maybe [:vector [:map [:text :string] [:created-at inst?]]]]]
    [:article/added-at      inst?]
    [:article/enriched-at  {:optional true} [:maybe inst?]]
    [:article/read-at       {:optional true} [:maybe inst?]]
    [:article/archived-at   {:optional true} [:maybe inst?]]
    [:article/deleted-at    {:optional true} [:maybe inst?]]
    [:article/error         {:optional true} [:maybe :string]]
    [:article/error-reason  {:optional true} [:maybe [:enum :paywall :notfound :login-required :other]]]
    [:article/retry-count   :int]
    [:article/next-attempt-at {:optional true} [:maybe inst?]]]

   :rec/id :uuid
   :rec
   [:map {:closed true}
    [:xt/id               :rec/id]
    [:rec/date            :string]
    [:rec/generated-at    inst?]
    [:rec/prompt-snapshot :string]
    [:rec/collections     [:vector :any]]
    [:rec/external-collections {:optional true} [:maybe [:vector :any]]]]

   :syn/id :uuid
   :syn
   [:map {:closed true}
    [:xt/id                   :syn/id]
    [:syn/generated-at        inst?]
    [:syn/prompt-snapshot     :string]
    [:syn/dictionary          [:map-of :string [:vector :string]]]
    [:syn/articles-considered :int]
    [:syn/dictionary-size     :int]]

   :folder/id :uuid
   :folder
   [:map {:closed true}
    [:xt/id             :folder/id]
    [:folder/name       :string]
    [:folder/color      :string]
    [:folder/created-at inst?]]

   :notif/id :uuid
   :notification
   [:map {:closed true}
    [:xt/id           :notif/id]
    [:notif/type      [:enum :success :error :info :warning]]
    [:notif/title     :string]
    [:notif/body      {:optional true} [:maybe :string]]
    [:notif/link      {:optional true} [:maybe :string]]
    [:notif/read      :boolean]
    [:notif/created-at inst?]]

   :settings/id :keyword
   :settings
   [:map {:closed true}
    [:xt/id                                    :settings/id]
    [:settings/enrich-prompt       {:optional true} :string]
    [:settings/recommend-prompt    {:optional true} :string]
    [:settings/synonyms-prompt     {:optional true} :string]
    [:settings/interests           {:optional true} :string]
    [:settings/recommend-cron-time {:optional true} :string]
    [:settings/synonyms-cron       {:optional true} :string]
    [:settings/claude-bin          {:optional true} :string]
    [:settings/meilisearch-url     {:optional true} :string]]})

(def module {:schema schema})
