(ns com.readlater.components
  (:require [rum.core :as rum]
            [clojure.string :as str])
  (:import [java.time ZoneOffset]
           [java.time.format DateTimeFormatter]))

(defn- domain-hsl [url]
  (try
    (let [host (-> (java.net.URI. url) .getHost (str/replace #"^www\." ""))
          hv   (Math/abs (int (.hashCode host)))
          hue  (mod hv 360)
          sat  (+ 45 (mod (quot hv 360) 20))
          lig  (+ 48 (mod (quot hv 7200) 12))]
      (str "hsl(" hue "," sat "%," lig "%)"))
    (catch Exception _ "#C9A87C")))

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

(defn- copy-btn [url]
  [:button {:class   "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5 shrink-0"
            :title   "Copy link"
            :data-url url
            :onclick "navigator.clipboard.writeText(this.dataset.url);showToast({type:'success',title:'Link copied'})"}
   [:i {:data-lucide "link" :class "icon-sm"}]])

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
    [:div {:class "card-art p-5" :style {:border-left (str "3px solid " (domain-hsl url))}}
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
       (copy-btn url)
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
    :article  [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-stone-100 text-stone-600 border border-stone-200"}
               [:i {:data-lucide "file" :class "icon-sm"}] "article"]
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
    [:div {:class "card-art overflow-hidden" :style {:border-left (str "3px solid " (domain-hsl url))}}
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
        (copy-btn url)
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
    [:div {:class "card-art p-5" :style {:border-left (str "3px solid " (domain-hsl url))}}
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
      [:div {:class "flex items-center gap-0.5 shrink-0"}
       (copy-btn url)
       [:a {:href url :target "_blank" :rel "noopener noreferrer"
            :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"
            :title "Open"}
        [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
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
  [:div {:class "card-art p-5" :style {:border-left (str "3px solid " (domain-hsl url))}}
   [:div {:class "flex items-start justify-between gap-3 mb-3"}
    [:div {:class "flex items-center gap-2 flex-wrap"}
     [:div {:class "w-6 h-6 rounded bg-purple-50 border border-purple-100 flex items-center justify-center flex-shrink-0"}
      [:i {:data-lucide "message-square" :class "text-purple-500" :style {:width "12px" :height "12px"}}]]
     (when platform [:span {:class "chip chip-mono text-[10px]"} platform])
     (when author-handle [:span {:class "text-xs text-stone-500 font-medium"} author-handle])]
    [:div {:class "flex items-center gap-0.5 shrink-0"}
     (copy-btn url)
     [:a {:href url :target "_blank" :rel "noopener noreferrer"
          :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"}
      [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
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
  [:div {:class "card-art p-5" :style {:border-left (str "3px solid " (domain-hsl url))}}
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
    [:div {:class "flex items-center gap-0.5 shrink-0"}
     (copy-btn url)
     [:a {:href url :target "_blank" :rel "noopener noreferrer"
          :class "btn btn-xs btn-ghost text-stone-400 hover:text-stone-600 px-1.5"}
      [:i {:data-lucide "external-link" :class "icon-sm"}]]]]
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
                        article/tags article/reading-time-min article/kind]}]
  (let [row-id   (str "wr-" id)
        article? (contains? #{nil :article} kind)
        dc       (domain-hsl url)]
    [:div {:class           "swipe-row -mx-3 rounded-lg overflow-hidden"
           :data-read-url   (str "/api/articles/" id "/read")
           :data-delete-url (str "/api/articles/" id)}
     [:div {:class "swipe-bg swipe-bg-read"}
      [:i {:data-lucide "check" :style {:width "16px" :height "16px"}}] " Read"]
     [:div {:class "swipe-bg swipe-bg-delete"}
      "Delete " [:i {:data-lucide "trash-2" :style {:width "16px" :height "16px"}}]]
     [:div {:id    row-id
            :class "swipe-inner group flex items-center gap-3 py-2 hover:bg-stone-50 transition-colors"
            :style {:border-left (str "3px solid " dc) :padding-left "9px" :padding-right "12px"}}
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
      (when (and article? (not= status :read))
        [:button {:class               "opacity-0 group-hover:opacity-100 btn btn-xs btn-ghost text-emerald-600 gap-1 shrink-0 transition-opacity"
                  :title               "Mark as read"
                  :hx-post             (str "/api/articles/" id "/read")
                  :hx-swap             "none"
                  :hx-on--after-request (str "var e=document.getElementById('" row-id "');"
                                             "var r=e.closest('.swipe-row')||e;"
                                             "r.style.transition='opacity .25s';"
                                             "r.style.opacity='0';"
                                             "setTimeout(function(){r.remove()},260)")}
         [:i {:data-lucide "check" :class "icon-sm"}]])
      [:button {:class    "opacity-0 group-hover:opacity-100 btn btn-xs btn-ghost text-stone-400 px-1 shrink-0 transition-opacity"
                :title    "Copy link"
                :data-url url
                :onclick  "navigator.clipboard.writeText(this.dataset.url);showToast({type:'success',title:'Link copied'})"}
       [:i {:data-lucide "link" :class "icon-sm"}]]
      [:a {:href  url :target "_blank" :rel "noopener noreferrer"
           :title "Open original"
           :class "opacity-0 group-hover:opacity-100 btn btn-xs btn-ghost text-stone-400 px-1 shrink-0 transition-opacity"}
       [:i {:data-lucide "external-link" :class "icon-sm"}]]]]))

;; ---------------------------------------------------------------------------
;; Discover cards — editorial bento grid style

(def ^:private disc-sources
  {"HackerNews" {:color "#E8601C" :label "Hacker News"}
   "Lobsters"   {:color "#C0392B" :label "Lobsters"}
   "Dev.to"     {:color "#3B49DF" :label "Dev.to"}
   "Reddit"     {:color "#FF4500" :label "Reddit"}
   "GitHub"     {:color "#24292F" :label "GitHub"}
   "YouTube"    {:color "#FF0000" :label "YouTube"}})

(defn- disc-source-header [source-label vibe count-label]
  (let [{:keys [color label]} (get disc-sources source-label {:color "#8B5A3C" :label source-label})]
    [:div {:class "disc-header flex items-center justify-between px-4 py-2.5 border-b border-stone-100/80"
           :style {:background (str "color-mix(in srgb," color " 8%, transparent)")}}
     [:div {:class "flex items-center gap-2"}
      [:span {:class "w-2 h-2 rounded-full flex-shrink-0" :style {:background color}}]
      [:span {:class "text-[10px] font-bold uppercase tracking-[0.1em] text-stone-600"} label]
      (when (seq vibe)
        [:span {:class "text-[10px] text-stone-400 font-medium"} (str "· " vibe)])]
     (when count-label
       [:span {:class "text-[10px] font-mono text-stone-400"} count-label])]))

(defn- disc-add-btn [url kind]
  [:button {:class               "disc-add opacity-0 inline-flex items-center justify-center flex-shrink-0 w-5 h-5 rounded-full border border-stone-200 bg-white text-stone-400 hover:text-emerald-600 hover:border-emerald-300 hover:bg-emerald-50 transition-all"
            :title               "Add to inbox"
            :hx-post             "/api/add"
            :hx-vals             (str "{\"url\":\"" url "\",\"kind\":\"" kind "\"}")
            :hx-swap             "none"
            :hx-on--after-request "if(event.detail.successful){this.outerHTML='<span class=\"inline-flex items-center justify-center flex-shrink-0 w-5 h-5 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-500\" style=\"font-size:9px\">✓</span>'}"}
   [:i {:data-lucide "plus" :style {:width "10px" :height "10px"}}]])

(defn external-collection-card [{:keys [theme vibe items source-label]}]
  (let [{:keys [color]} (get disc-sources source-label {:color "#8B5A3C"})]
    [:div {:class "disc-card card-art overflow-hidden flex flex-col"
           :style {:border-left (str "3px solid " color)}}
     (disc-source-header source-label vibe (str (count items) " stories"))
     (when (seq theme)
       [:div {:class "px-4 pt-3 pb-1"}
        [:h3 {:class "font-serif text-[15px] font-semibold leading-snug text-stone-900 line-clamp-2"} theme]])
     [:div {:class "flex flex-col divide-y divide-stone-100/70"}
      (for [{:keys [url title blurb]} (take 6 items)]
        [:div {:class "group flex items-start gap-3 px-4 py-2.5 hover:bg-stone-50/60 transition-colors"}
         [:div {:class "flex-1 min-w-0"}
          [:a {:href url :target "_blank" :rel "noopener noreferrer"
               :class "text-[13px] font-medium leading-snug hover:underline text-stone-800 line-clamp-2"}
           title]
          (when (seq blurb)
            [:p {:class "text-[11px] text-stone-400 mt-0.5 line-clamp-1 italic"} blurb])]
         (disc-add-btn url "article")])]]))

(defn github-trending-card [{:keys [repos]}]
  (let [{:keys [color]} (get disc-sources "GitHub")]
    [:div {:class "disc-card card-art overflow-hidden flex flex-col"
           :style {:border-left (str "3px solid " color)}}
     (disc-source-header "GitHub" "trending" (str (count repos) " repos"))
     [:div {:class "flex flex-col divide-y divide-stone-100/70"}
      (for [{:keys [name url description stars language]} (take 6 repos)]
        [:div {:class "group flex items-start gap-3 px-4 py-2.5 hover:bg-stone-50/60 transition-colors"}
         [:div {:class "flex-1 min-w-0"}
          [:a {:href url :target "_blank" :rel "noopener noreferrer"
               :class "text-[13px] font-semibold leading-snug hover:underline text-stone-800 line-clamp-1 font-mono"}
           name]
          [:div {:class "flex items-center gap-2.5 mt-0.5"}
           (when (seq language)
             [:span {:class "text-[11px] text-stone-500 font-mono"} language])
           (when (pos? (or stars 0))
             [:span {:class "inline-flex items-center gap-0.5 text-[11px] text-stone-400"}
              [:i {:data-lucide "star" :style {:width "10px" :height "10px"}}]
              (if (>= stars 1000) (str (quot stars 1000) "k") (str stars))])]
          (when (seq description)
            [:p {:class "text-[11px] text-stone-400 mt-0.5 line-clamp-1"} description])]
         (disc-add-btn url "bookmark")])]]))

(defn video-recs-card [{:keys [videos]}]
  (let [{:keys [color]} (get disc-sources "YouTube")]
    [:div {:class "disc-card card-art overflow-hidden flex flex-col"
           :style {:border-left (str "3px solid " color)}}
     (disc-source-header "YouTube" "по интересам" (str (count videos) " videos"))
     [:div {:class "flex flex-col divide-y divide-stone-100/70"}
      (for [{:keys [url title topic points]} (take 7 videos)]
        [:div {:class "group flex items-start gap-3 px-4 py-2.5 hover:bg-stone-50/60 transition-colors"}
         [:div {:class "flex-1 min-w-0"}
          [:a {:href url :target "_blank" :rel "noopener noreferrer"
               :class "text-[13px] font-medium leading-snug hover:underline text-stone-800 line-clamp-2"}
           title]
          [:div {:class "flex items-center gap-2 mt-0.5"}
           (when (seq topic)
             [:span {:class "text-[11px] text-stone-400"} topic])
           (when (pos? (or points 0))
             [:span {:class "text-[11px] text-stone-400 font-mono"} (str points " pts")])]]
         (disc-add-btn url "video")])]]))
