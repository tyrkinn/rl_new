(ns com.readlater.components
  (:require [rum.core :as rum])
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
