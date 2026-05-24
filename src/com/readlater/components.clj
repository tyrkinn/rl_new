(ns com.readlater.components
  (:require [rum.core :as rum]
            [clojure.string :as str])
  (:import [java.time ZoneOffset]
           [java.time.format DateTimeFormatter]))

(defn html-frag [hiccup]
  {:status  200
   :headers {"content-type" "text/html; charset=UTF-8"}
   :body    (rum/render-static-markup hiccup)})

(defn fmt-comment-time [^java.time.Instant t]
  (when t
    (.format (.atOffset t ZoneOffset/UTC)
             (DateTimeFormatter/ofPattern "MMM d, HH:mm"))))

(defn fmt-read-date [^java.time.Instant t]
  (when t
    (.format (.atOffset t ZoneOffset/UTC)
             (DateTimeFormatter/ofPattern "MMM d, yyyy"))))

(defn comment-bubble [{:keys [text created-at]}]
  [:div {:class "group flex flex-col gap-1"}
   [:div {:class "bg-white border border-stone-200 rounded-2xl rounded-tl-sm px-4 py-3 shadow-soft"}
    [:p {:class "text-sm text-stone-800 leading-relaxed whitespace-pre-wrap"} text]]
   [:span {:class "text-xs text-stone-400 pl-1"} (fmt-comment-time created-at)]])

(defn- status-badge [status]
  (case status
    :queued    [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200"}
                [:span {:class "w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"}]
                "pending"]
    :enriching [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-200"}
                [:span {:class "loading loading-spinner loading-xs"}]
                "enriching"]
    nil))

(defn article-card [{:keys [xt/id article/title article/url article/status
                             article/tldr article/tags article/reading-time-min]}]
  (let [pending?      (#{:queued :enriching} status)
        display-title (or title url)]
    [:div {:class "card-art p-5"}
     [:div {:class "flex items-start justify-between gap-3"}
      [:div {:class "flex-1 min-w-0"}
       [:a {:href  (str "/article/" id)
            :class (str "font-serif text-lg font-medium leading-snug hover:underline"
                        (when pending? " text-stone-400"))}
        display-title]
       (when (and pending? (nil? title))
         [:p {:class "text-xs text-stone-400 mt-0.5 truncate"} url])]
      [:div {:class "flex items-center gap-1.5 shrink-0"}
       (status-badge status)
       [:a {:href url :target "_blank" :rel "noopener noreferrer"
            :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"
            :title "Open original"}
        [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
     (when-let [summary (first tldr)]
       [:p {:class "text-sm text-stone-600 mt-2 line-clamp-2"} summary])
     (when (or reading-time-min (seq tags))
       [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
        (when reading-time-min [:span {:class "chip"} (str reading-time-min " min")])
        (for [tag (take 4 tags)]
          [:span {:class "chip"} tag])])]))

;; ---------------------------------------------------------------------------
;; Kind badge

(defn kind-badge [kind]
  (case kind
    :video    [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-red-50 text-red-600 border border-red-100"}
               [:i {:data-lucide "play-circle" :class "icon-sm"}] "video"]
    :bookmark [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-blue-50 text-blue-600 border border-blue-100"}
               [:i {:data-lucide "bookmark" :class "icon-sm"}] "bookmark"]
    :thread   [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-purple-50 text-purple-600 border border-purple-100"}
               [:i {:data-lucide "message-square" :class "icon-sm"}] "thread"]
    :paper    [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-emerald-50 text-emerald-700 border border-emerald-100"}
               [:i {:data-lucide "file-text" :class "icon-sm"}] "paper"]
    nil))

;; ---------------------------------------------------------------------------
;; Video card

(defn video-card [{:keys [xt/id article/title article/url article/status
                           article/tldr article/tags article/channel
                           article/platform article/duration-min]}]
  (let [pending? (#{:queued :enriching} status)]
    [:div {:class "card-art overflow-hidden"}
     [:div {:class "flex items-center gap-2 px-5 pt-4 pb-3 border-b border-stone-100"}
      [:div {:class "w-8 h-8 rounded-lg bg-red-50 border border-red-100 flex items-center justify-center flex-shrink-0"}
       [:i {:data-lucide "play-circle" :class "text-red-500" :style {:width "18px" :height "18px"}}]]
      [:div {:class "flex-1 min-w-0"}
       [:div {:class "flex items-center gap-1.5 flex-wrap"}
        (when platform [:span {:class "text-xs font-medium text-stone-500"} platform])
        (when channel  [:span {:class "text-xs text-stone-400 truncate"} (str "· " channel)])]]
      (when duration-min
        [:span {:class "chip chip-mono shrink-0"} (str duration-min " min")])]
     [:div {:class "p-5"}
      [:div {:class "flex items-start justify-between gap-3"}
       [:div {:class "flex-1 min-w-0"}
        [:a {:href  (str "/article/" id)
             :class (str "font-serif text-lg font-medium leading-snug hover:underline"
                         (when pending? " text-stone-400"))}
         (or title url)]
        (when (and pending? (nil? title))
          [:p {:class "text-xs text-stone-400 mt-0.5 truncate"} url])]
       [:div {:class "flex items-center gap-1.5 shrink-0"}
        (status-badge status)
        [:a {:href url :target "_blank" :rel "noopener noreferrer"
             :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"
             :title "Watch"}
         [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
      (when-let [summary (first tldr)]
        [:p {:class "text-sm text-stone-600 mt-2 line-clamp-2"} summary])
      (when (seq tags)
        [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
         (for [tag (take 4 tags)]
           [:span {:class "chip"} tag])])]]))

;; ---------------------------------------------------------------------------
;; Bookmark card

(defn bookmark-card [{:keys [xt/id article/title article/url article/status
                              article/why-interesting article/tags article/category article/platform]}]
  (let [domain (try (-> (java.net.URI. url) .getHost (str/replace #"^www\." ""))
                    (catch Exception _ url))]
    [:div {:class "card-art p-5"}
     [:div {:class "flex items-start justify-between gap-3"}
      [:div {:class "flex-1 min-w-0"}
       [:div {:class "flex items-center gap-2 mb-1 flex-wrap"}
        [:div {:class "w-6 h-6 rounded bg-stone-100 flex items-center justify-center flex-shrink-0"}
         [:i {:data-lucide "bookmark" :class "text-stone-500" :style {:width "12px" :height "12px"}}]]
        (when category
          [:span {:class "chip chip-mono text-[10px] uppercase tracking-wide"} category])
        (when platform
          [:span {:class "chip chip-mono text-[10px]"} platform])]
       [:a {:href  (str "/article/" id)
            :class "font-serif text-lg font-medium leading-snug hover:underline"}
        (or title url)]
       [:p {:class "text-xs text-stone-400 mt-0.5"} domain]]
      [:a {:href url :target "_blank" :rel "noopener noreferrer"
           :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5 shrink-0"
           :title "Open"}
       [:i {:data-lucide "external-link" :class "icon-sm"}]]]
     (when why-interesting
       [:p {:class "text-sm text-stone-600 mt-2 line-clamp-2"} why-interesting])
     (when (seq tags)
       [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
        (for [tag (take 4 tags)]
          [:span {:class "chip"} tag])])]))

;; ---------------------------------------------------------------------------
;; Thread card

(defn thread-card [{:keys [xt/id article/title article/url article/status
                            article/tldr article/tags article/platform article/author-handle]}]
  [:div {:class "card-art p-5"}
   [:div {:class "flex items-start justify-between gap-3 mb-3"}
    [:div {:class "flex items-center gap-2 flex-wrap"}
     [:div {:class "w-6 h-6 rounded bg-purple-50 border border-purple-100 flex items-center justify-center flex-shrink-0"}
      [:i {:data-lucide "message-square" :class "text-purple-500" :style {:width "12px" :height "12px"}}]]
     (when platform [:span {:class "chip chip-mono text-[10px]"} platform])
     (when author-handle [:span {:class "text-xs text-stone-500 font-medium"} author-handle])]
    [:a {:href url :target "_blank" :rel "noopener noreferrer"
         :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5 shrink-0"}
     [:i {:data-lucide "external-link" :class "icon-sm"}]]]
   [:a {:href  (str "/article/" id)
        :class "font-serif text-[17px] font-medium leading-snug hover:underline text-stone-800"}
    (or title url)]
   (when (seq tldr)
     [:ul {:class "mt-2 space-y-1"}
      (for [point (take 3 tldr)]
        [:li {:class "flex items-start gap-2 text-sm text-stone-600"}
         [:span {:class "text-stone-400 shrink-0 mt-0.5"} "·"]
         [:span point]])])
   (when (seq tags)
     [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
      (for [tag (take 4 tags)] [:span {:class "chip"} tag])])])

;; ---------------------------------------------------------------------------
;; Paper card

(defn paper-card [{:keys [xt/id article/title article/url article/status
                           article/why-interesting article/tags article/field
                           article/paper-authors article/key-findings]}]
  [:div {:class "card-art p-5"}
   [:div {:class "flex items-start justify-between gap-3"}
    [:div {:class "flex-1 min-w-0"}
     [:div {:class "flex items-center gap-2 mb-1 flex-wrap"}
      [:div {:class "w-6 h-6 rounded bg-emerald-50 border border-emerald-100 flex items-center justify-center flex-shrink-0"}
       [:i {:data-lucide "file-text" :class "text-emerald-600" :style {:width "12px" :height "12px"}}]]
      (when field [:span {:class "chip chip-mono text-[10px]"} field])]
     [:a {:href  (str "/article/" id)
          :class "font-serif text-lg font-medium leading-snug hover:underline text-stone-800"}
      (or title url)]
     (when (seq paper-authors)
       [:p {:class "text-xs text-stone-500 mt-0.5"} (str/join ", " (take 3 paper-authors))])]
    [:a {:href url :target "_blank" :rel "noopener noreferrer"
         :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5 shrink-0"}
     [:i {:data-lucide "external-link" :class "icon-sm"}]]]
   (when why-interesting
     [:p {:class "text-sm text-stone-600 mt-2 line-clamp-2"} why-interesting])
   (when (seq key-findings)
     [:ul {:class "mt-2 space-y-1"}
      (for [f (take 3 key-findings)]
        [:li {:class "flex items-start gap-2 text-sm text-stone-600"}
         [:span {:class "text-emerald-500 font-medium shrink-0 mt-0.5"} "→"]
         [:span f]])])
   (when (seq tags)
     [:div {:class "flex items-center gap-2 mt-3 flex-wrap"}
      (for [tag (take 4 tags)] [:span {:class "chip"} tag])])])

;; ---------------------------------------------------------------------------
;; Universal link card dispatcher

(defn link-card [item]
  (case (:article/kind item)
    :video    (video-card item)
    :bookmark (bookmark-card item)
    :thread   (thread-card item)
    :paper    (paper-card item)
    (article-card item)))

(defn week-status-dot [status]
  [:span {:class (str "w-2 h-2 rounded-full shrink-0 mt-1 "
                      (case status
                        :ready             "bg-emerald-400"
                        :read              "bg-stone-300"
                        :queued            "bg-amber-400 animate-pulse"
                        :enriching         "bg-blue-400 animate-pulse"
                        :failed            "bg-red-400"
                        (:paywall
                         :notfound
                         :login-required)  "bg-orange-400"
                        "bg-stone-300"))}])

(defn week-row [{:keys [xt/id article/title article/url article/status
                        article/tags article/reading-time-min]}]
  (let [row-id (str "wr-" id)]
    [:div {:id    row-id
           :class "group flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-stone-50 -mx-3 transition-colors"}
     (week-status-dot status)
     [:a {:href  (str "/article/" id)
          :class "flex-1 min-w-0 font-serif text-[15px] leading-snug truncate hover:underline text-stone-800"}
      (or title url)]
     (when (seq tags)
       [:div {:class "hidden sm:flex items-center gap-1 shrink-0"}
        (for [tag (take 2 tags)]
          [:span {:class "chip"} tag])])
     (when reading-time-min
       [:span {:class "chip chip-mono shrink-0"} (str reading-time-min "m")])
     (when (not= status :read)
       [:button {:class               "opacity-0 group-hover:opacity-100 btn btn-xs btn-ghost text-emerald-600 gap-1 shrink-0 transition-opacity"
                 :title               "Mark as read"
                 :hx-post             (str "/api/articles/" id "/read")
                 :hx-swap             "none"
                 :hx-on--after-request (str "var e=document.getElementById('" row-id "');"
                                            "e.style.transition='opacity .25s';"
                                            "e.style.opacity='0';"
                                            "setTimeout(function(){e.remove()},260)")}
        [:i {:data-lucide "check" :class "icon-sm"}]])
     [:a {:href  url :target "_blank" :rel "noopener noreferrer"
          :title "Open original"
          :class "opacity-0 group-hover:opacity-100 btn btn-xs btn-ghost text-stone-400 px-1 shrink-0 transition-opacity"}
      [:i {:data-lucide "external-link" :class "icon-sm"}]]]))
