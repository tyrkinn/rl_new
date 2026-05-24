(ns com.readlater.ui
  (:require [rum.core :as rum]))

;; ---------------------------------------------------------------------------
;; Head

(def ^:private tailwind-config-js
  "tailwind.config={darkMode:'class',theme:{extend:{colors:{cream:{50:'#FDFBF7',100:'#FAF8F5',200:'#F4F1EA',300:'#EDE7DD',400:'#E2DACB'},ink:{900:'#1A1A1A',800:'#262626',700:'#2D2D2D',600:'#3A3A3A'},accent:{50:'#FBF4EE',100:'#F4E4D5',200:'#E8C9A8',300:'#D2A37E',400:'#B47B53',500:'#8B5A3C',600:'#714630',700:'#553523'}},fontFamily:{sans:['Inter','ui-sans-serif','system-ui','sans-serif'],serif:['\"Source Serif 4\"','Charter','Georgia','serif'],mono:['\"JetBrains Mono\"','ui-monospace','Menlo','monospace']},boxShadow:{soft:'0 1px 2px rgba(20,15,8,.04),0 6px 18px rgba(20,15,8,.06)'}}}};" )

(def ^:private custom-css
  ":root{--cream:#FAF8F5;--ink:#1A1A1A;--accent:#8B5A3C}
html,body{background:#FAF8F5}
body{font-family:'Inter',sans-serif;color:#2A2620}
.font-serif{font-family:'Source Serif 4',Charter,Georgia,serif}
.font-mono{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace}
[data-lucide]{width:1em;height:1em;flex-shrink:0}
.icon-sm{width:14px;height:14px}.icon-md{width:18px;height:18px}
.nav-item{display:flex;align-items:center;gap:.75rem;padding:.5rem .75rem;border-radius:.625rem;color:#544D3F;font-size:14px;transition:background .15s,color .15s}
.nav-item:hover{background:#F4F1EA}
.nav-item.active{background:#F4E4D5;color:#553523;font-weight:500}
.chip{display:inline-flex;align-items:center;gap:.25rem;padding:.125rem .5rem;border-radius:9999px;font-size:11.5px;line-height:1.4;background:#F4F1EA;color:#544D3F}
.chip-mono{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace;font-size:11px}
.card-art{background:#fff;border:1px solid #EDE7DD;border-radius:14px;transition:transform .2s,box-shadow .2s}
.card-art:hover{transform:translateY(-2px);box-shadow:0 1px 2px rgba(20,15,8,.04),0 12px 30px rgba(20,15,8,.08)}
.bar-bg{background:rgba(250,248,245,.85);backdrop-filter:saturate(180%) blur(8px);-webkit-backdrop-filter:saturate(180%) blur(8px)}
.serif-h1{font-family:'Source Serif 4',Charter,Georgia,serif;font-weight:600;letter-spacing:-0.02em}
mark{background:#F4E4D5;color:#553523;padding:0 2px;border-radius:3px}
#toast-container{position:fixed;top:1rem;right:1rem;z-index:9990;display:flex;flex-direction:column;gap:.5rem;pointer-events:none;width:min(360px,calc(100vw - 2rem))}
.toast-item{background:#fff;border-radius:14px;box-shadow:0 4px 24px rgba(0,0,0,.13),0 1px 6px rgba(0,0,0,.06);border:1px solid rgba(0,0,0,.06);padding:12px 12px 14px 14px;display:flex;align-items:flex-start;gap:10px;pointer-events:all;transform:translateX(calc(100% + 1.5rem));opacity:0;transition:transform .38s cubic-bezier(.34,1.2,.64,1),opacity .2s;overflow:hidden;position:relative;border-left:3px solid #E2DACB}
.toast-item.toast-in{transform:translateX(0);opacity:1}
.toast-item.toast-out{transform:translateX(calc(100% + 1.5rem));opacity:0;transition:transform .26s cubic-bezier(.4,0,.6,1),opacity .16s}
.toast-success{border-left-color:#10b981}
.toast-error{border-left-color:#ef4444}
.toast-warning{border-left-color:#f59e0b}
.toast-info{border-left-color:#3b82f6}
.toast-progress{position:absolute;bottom:0;left:0;height:2px;width:100%;transform-origin:left;animation:toast-prog 4s linear forwards}
.toast-success .toast-progress{background:#10b981}
.toast-error .toast-progress{background:#ef4444}
.toast-warning .toast-progress{background:#f59e0b}
.toast-info .toast-progress{background:#3b82f6}
@keyframes toast-prog{from{transform:scaleX(1)}to{transform:scaleX(0)}}
#notif-overlay{display:none;position:fixed;inset:0;z-index:400;background:rgba(10,8,5,.3);backdrop-filter:blur(2px);-webkit-backdrop-filter:blur(2px)}
#notif-overlay.open{display:block}
#notif-drawer{position:fixed;top:0;right:0;bottom:0;z-index:401;width:min(380px,100vw);background:#fff;box-shadow:-8px 0 48px rgba(20,15,8,.14);display:flex;flex-direction:column;transform:translateX(100%);transition:transform .3s cubic-bezier(.4,0,.2,1)}
#notif-drawer.open{transform:translateX(0)}
.notif-bell-wrap{position:relative}
.notif-bell-badge{position:absolute;top:-3px;right:-3px;min-width:16px;height:16px;border-radius:9999px;background:#ef4444;color:#fff;display:flex;align-items:center;justify-content:center;font-size:9px;font-weight:700;padding:0 3px;line-height:1;pointer-events:none}
.tag-pill{display:inline-flex;align-items:center;gap:.375rem;padding:.375rem .75rem;border-radius:9999px;border:1px solid #D6D0C8;background:#fff;color:#544D3F;cursor:pointer;transition:border-color .15s,box-shadow .15s,background .15s;line-height:1.4;text-decoration:none}
.tag-pill:hover{border-color:#B47B53;box-shadow:0 1px 4px rgba(20,15,8,.08)}
.tag-pill.active{border-color:#714630;background:#FBF4EE;color:#553523;font-weight:600;box-shadow:0 1px 4px rgba(20,15,8,.1)}
.tag-pill .tag-badge{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace;font-size:10px;border-radius:9999px;padding:0 .375rem;background:#F4F1EA;color:#8B7355;transition:background .15s,color .15s}
.tag-pill.active .tag-badge{background:#E8C9A8;color:#714630}
@media(max-width:1023px){
  #sidebar{position:fixed;top:0;left:0;bottom:0;z-index:300;width:240px;transform:translateX(-100%);transition:transform .25s cubic-bezier(.4,0,.2,1);overflow-y:auto;height:100dvh}
  #sidebar.open{transform:translateX(0)}
  #mob-overlay{display:none;position:fixed;inset:0;z-index:299;background:rgba(10,8,5,.4);backdrop-filter:blur(3px);-webkit-backdrop-filter:blur(3px)}
  #mob-overlay.open{display:block}
  main{padding-bottom:5rem}
}
.mob-search-fab{display:none;position:fixed;bottom:1.375rem;left:50%;transform:translateX(-50%);z-index:50;align-items:center;gap:.625rem;padding:.625rem 1.5rem;background:rgba(22,18,14,.86);-webkit-backdrop-filter:blur(20px);backdrop-filter:blur(20px);border-radius:9999px;color:#F0EAE2;font-size:14px;font-weight:500;letter-spacing:.01em;box-shadow:0 8px 40px rgba(139,90,60,.28),0 2px 8px rgba(0,0,0,.18),inset 0 1px 0 rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.09);cursor:pointer;white-space:nowrap;transition:transform .15s ease,box-shadow .15s ease;-webkit-tap-highlight-color:transparent}
.mob-search-fab:active{transform:translateX(-50%) scale(.96);box-shadow:0 4px 20px rgba(139,90,60,.2),0 1px 4px rgba(0,0,0,.15),inset 0 1px 0 rgba(255,255,255,.06)}
@media(max-width:1023px){.mob-search-fab{display:flex}}
.kind-opt-radio:checked+.kind-opt-pill{border-color:transparent;color:#fff;background:var(--kc,#8B5A3C)}
/* === DARK === */
html.dark,html.dark body{background:#141414;color:#E7E2D6}
html.dark #sidebar{background:#181818;border-right-color:#252525}
html.dark .bar-bg{background:rgba(20,20,20,.9) !important;backdrop-filter:saturate(180%) blur(8px);-webkit-backdrop-filter:saturate(180%) blur(8px)}
html.dark header{border-bottom-color:#252525 !important}
html.dark .nav-item{color:#C9C2B4}
html.dark .nav-item:hover{background:#252525}
html.dark .nav-item.active{background:#3a2818;color:#E6C9A8}
html.dark .card-art{background:#1e1e1e;border-color:#2a2a2a}
html.dark .card-art:hover{box-shadow:0 1px 2px rgba(0,0,0,.2),0 12px 30px rgba(0,0,0,.35)}
html.dark .card-art .border-b,html.dark .card-art .border-stone-100{border-color:#2a2a2a}
html.dark .chip{background:#252525;color:#C9C2B4}
html.dark .tag-pill{background:#1e1e1e;border-color:#303030;color:#C9C2B4}
html.dark .tag-pill:hover{border-color:#A07050;box-shadow:none}
html.dark .tag-pill.active{background:#2a1a10;border-color:#714630;color:#E6C9A8}
html.dark .tag-pill .tag-badge{background:#252525;color:#A89880}
html.dark .tag-pill.active .tag-badge{background:#3a2818;color:#D4A574}
html.dark mark{background:#3a2818;color:#E6C9A8}
html.dark .kind-opt-pill{border-color:#333;color:#C9C2B4;background:#1e1e1e}
html.dark .toast-item{background:#1e1e1e;border-color:rgba(255,255,255,.07);box-shadow:0 4px 24px rgba(0,0,0,.5),0 1px 6px rgba(0,0,0,.3);color:#E7E2D6}
html.dark .toast-item p:first-of-type{color:#E7E2D6 !important}
html.dark .toast-item p+p{color:#908880 !important}
html.dark .toast-item button{color:#5e5850 !important}
html.dark #notif-drawer{background:#1c1c1c}
html.dark .mob-search-fab{background:rgba(12,10,8,.9);border-color:rgba(255,255,255,.07);box-shadow:0 8px 40px rgba(139,90,60,.2),0 2px 8px rgba(0,0,0,.3),inset 0 1px 0 rgba(255,255,255,.06)}
html.dark .border-stone-100{border-color:#242424}
html.dark .border-stone-200{border-color:#282828}
html.dark .border-red-100{border-color:#3a1a1a}
html.dark .border-blue-100{border-color:#1a2a3a}
html.dark .border-purple-100{border-color:#2a1a3a}
html.dark .border-emerald-100{border-color:#1a3028}
html.dark .border-amber-200{border-color:#3a2a0a}
html.dark .bg-white{background:#1e1e1e}
html.dark .bg-stone-50{background:#1a1a1a}
html.dark .bg-stone-100{background:#222}
html.dark .bg-stone-900{background:#0a0a0a}
html.dark .bg-red-50{background:#200f0f}
html.dark .bg-blue-50{background:#0f1829}
html.dark .bg-purple-50{background:#1a1028}
html.dark .bg-emerald-50{background:#0e1f18}
html.dark .bg-amber-50{background:#201a0a}
html.dark .text-stone-900{color:#EDE7DB}
html.dark .text-stone-800{color:#E0D9CF}
html.dark .text-stone-700{color:#C8C1B7}
html.dark .text-stone-600{color:#A09890}
html.dark .text-stone-500{color:#808070}
html.dark .text-stone-400{color:#5e5850}
html.dark .text-red-700{color:#f87171}
html.dark .text-red-600{color:#f87171}
html.dark .text-amber-700{color:#fbbf24}
html.dark .text-blue-700{color:#60a5fa}
html.dark .text-blue-600{color:#60a5fa}
html.dark .text-purple-600{color:#c084fc}
html.dark .text-emerald-700{color:#34d399}
html.dark .text-emerald-600{color:#34d399}
html.dark .text-emerald-500{color:#34d399}
html.dark .modal-box{background:#1c1c1c !important;border-color:#2a2a2a !important;color:#E7E2D6 !important}
html.dark .input,html.dark .input-bordered{background:#1c1c1c !important;border-color:#2f2f2f !important;color:#E7E2D6 !important}
html.dark .input:focus{border-color:#8B5A3C !important;background:#1e1e1e !important}
html.dark .textarea,html.dark .textarea-bordered{background:#1c1c1c !important;border-color:#2f2f2f !important;color:#E7E2D6 !important}
html.dark .hover\\:bg-stone-50:hover{background:#1e1e1e !important}
html.dark .hover\\:bg-stone-100:hover{background:#252525 !important}
html.dark #cmd-search-box{background:#1c1c1c !important;border-color:#2a2a2a !important;box-shadow:0 8px 48px rgba(0,0,0,.7) !important}
html.dark #cmd-input{color:#E7E2D6 !important;caret-color:#E7E2D6}
html.dark kbd{background:#252525 !important;border-color:#333 !important;color:#A89880 !important}
html.dark #cmd-search-box>div:first-child{border-bottom-color:#2a2a2a !important}")

(def ^:private theme-init-js
  "var s=localStorage.getItem('theme');if(s==='dark'||(s==null&&window.matchMedia('(prefers-color-scheme:dark)').matches)){document.documentElement.classList.add('dark');}")

(defn- head-html [title]
  (str "<head>"
       "<meta charset='UTF-8'>"
       "<meta name='viewport' content='width=device-width,initial-scale=1'>"
       "<title>Readlater — " title "</title>"
       "<script>" theme-init-js "</script>"
       "<link rel='preconnect' href='https://fonts.googleapis.com'>"
       "<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>"
       "<link rel='stylesheet' href='https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Source+Serif+4:ital,opsz,wght@0,8..60,400;0,8..60,600;1,8..60,400&family=JetBrains+Mono:wght@400;500&display=swap'>"
       "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/daisyui@4.12.10/dist/full.min.css'>"
       "<script src='https://cdn.tailwindcss.com'></script>"
       "<script>" tailwind-config-js "</script>"
       "<script src='https://unpkg.com/lucide@latest'></script>"
       "<script src='https://unpkg.com/htmx.org@2.0.4'></script>"
       "<style>" custom-css "</style>"
       "</head>"))

;; ---------------------------------------------------------------------------
;; Components

(defn- nav-item [{:keys [href icon label active? badge badge-id]}]
  [:a {:href href :class (str "nav-item" (when active? " active")) :onclick "sidebarClose()"}
   [:i {:data-lucide icon :class "icon-md"}]
   [:span label]
   (cond
     badge-id [:span {:id badge-id :class (str "ml-auto chip chip-mono" (when-not badge " hidden"))} badge]
     badge    [:span {:class "ml-auto chip chip-mono"} badge])])

(def ^:private folder-colors
  [["terracotta" "#C17B5A"] ["sage" "#6B9E7E"] ["slate" "#6B85A0"]
   ["amber" "#C9A84C"] ["rose" "#B87070"] ["lavender" "#9B8EC4"]
   ["teal" "#4E9E8F"] ["stone" "#9B9080"]])

(def ^:private create-folder-modal
  [:dialog {:id "create-folder-modal" :class "modal"}
   [:div {:class "modal-box max-w-sm rounded-xl bg-white border border-stone-200"}
    [:h3 {:class "font-serif text-xl mb-4"} "New folder"]
    [:form {:hx-post "/api/folders"
            :hx-swap "none"}
     [:div {:class "mb-4"}
      [:label {:class "block text-sm font-medium text-stone-700 mb-1.5"} "Name"]
      [:input {:name "name" :type "text" :required true :placeholder "e.g. Research, Projects…"
               :class "input input-bordered w-full bg-stone-50 focus:bg-white"}]]
     [:div {:class "mb-5"}
      [:label {:class "block text-sm font-medium text-stone-700 mb-2"} "Color"]
      [:div {:class "flex gap-2.5 flex-wrap"}
       (for [[i [_cname color]] (map-indexed vector folder-colors)]
         [:label {:class "cursor-pointer"}
          [:input {:type "radio" :name "color" :value color :class "sr-only peer"
                   :required true :checked (zero? i)}]
          [:span {:class "block w-7 h-7 rounded-full ring-2 ring-transparent ring-offset-2 peer-checked:ring-stone-800 transition-all hover:scale-110 active:scale-95"
                  :style {:background color}}]])]]
     [:div {:class "flex justify-end gap-2"}
      [:button {:type "button" :class "btn btn-ghost btn-sm"
                :onclick "document.getElementById('create-folder-modal').close()"} "Cancel"]
      [:button {:type "submit" :class "btn btn-sm border-none text-white" :style {:background "#8B5A3C"}} "Create"]]]]
   [:form {:method "dialog" :class "modal-backdrop"}
    [:button "close"]]])

(defn- sidebar [{:keys [active active-folder-id inbox-count queue-count folders]}]
  [:aside {:id "sidebar" :class "w-60 shrink-0 border-r border-stone-200 bg-stone-50/60 px-3 py-5 flex flex-col h-screen lg:sticky lg:top-0 overflow-y-auto"}
   [:div {:class "px-3 mb-8 flex items-center justify-between"}
    [:a {:href "/" :class "flex items-center gap-2"}
     [:span {:class "text-lg font-semibold tracking-tight"} "Readlater"]
     [:span {:class "w-1.5 h-1.5 rounded-full bg-emerald-500"}]]
    [:button {:class "lg:hidden p-1 text-stone-400 hover:text-stone-700 rounded-lg hover:bg-stone-100"
              :onclick "sidebarClose()"}
     [:i {:data-lucide "x" :class "icon-md"}]]]
   [:nav {:class "space-y-0.5"}
    (nav-item {:href "/"        :icon "sparkles"      :label "Today"   :active? (= active :today)})
    (nav-item {:href "/inbox"   :icon "inbox"         :label "Inbox"   :active? (= active :inbox)
               :badge-id "badge-inbox" :badge (when (pos? inbox-count) (str inbox-count))})
    (nav-item {:href "/saved"   :icon "bookmark"      :label "Saved"   :active? (= active :saved)})
    (nav-item {:href "/week"    :icon "calendar-days" :label "Week"    :active? (= active :week)})
    (nav-item {:href "/tags"    :icon "tags"          :label "Tags"    :active? (= active :tags)})
    (nav-item {:href "/archive" :icon "archive"       :label "Archive" :active? (= active :archive)})
    (nav-item {:href "/queue"   :icon "list-checks"   :label "Queue"   :active? (= active :queue)
               :badge-id "badge-queue" :badge (when (pos? queue-count) (str queue-count))})]
   [:span {:hx-get "/api/nav-counts" :hx-trigger "every 8s" :hx-swap "none" :class "hidden" :aria-hidden "true"}]
   ;; Folders section
   [:div {:class "mt-4 pt-3 border-t border-stone-200 flex-1"}
    [:div {:class "px-3 mb-1.5 flex items-center justify-between"}
     [:span {:class "text-xs font-semibold uppercase tracking-wider text-stone-400"} "Folders"]
     [:button {:class "w-5 h-5 rounded flex items-center justify-center text-stone-400 hover:text-stone-700 hover:bg-stone-100 transition-colors"
               :onclick "document.getElementById('create-folder-modal').showModal()"}
      [:i {:data-lucide "plus" :class "icon-sm"}]]]
    [:nav {:class "space-y-0.5"}
     (if (seq folders)
       (for [{:keys [xt/id folder/name folder/color article-count]} folders]
         [:a {:href    (str "/folders/" id)
              :class   (str "nav-item" (when (= active-folder-id id) " active"))
              :onclick "sidebarClose()"}
          [:span {:class "w-2 h-2 rounded-full flex-shrink-0" :style {:background color}}]
          [:span {:class "flex-1 truncate min-w-0"} name]
          (when (pos? (or article-count 0))
            [:span {:class "ml-auto chip chip-mono"} (str article-count)])])
       [:button {:class   "nav-item w-full text-stone-400 text-sm"
                 :onclick "document.getElementById('create-folder-modal').showModal()"}
        [:i {:data-lucide "folder-plus" :class "icon-md"}]
        "New folder"])]]
   [:div {:class "pt-3 border-t border-stone-200 space-y-0.5"}
    [:a {:href "#" :class "nav-item" :onclick "event.preventDefault();sidebarClose();cmdOpen()"}
     [:i {:data-lucide "search" :class "icon-md"}]
     [:span "Search"]
     [:span {:class "ml-auto chip chip-mono"} "⌘K"]]
    (nav-item {:href "/roadmap"  :icon "map"      :label "Roadmap"  :active? (= active :roadmap)})
    (nav-item {:href "/settings" :icon "settings" :label "Settings" :active? (= active :settings)})]])

(defn- topbar [{:keys [crumbs queue-processing notif-count]}]
  [:header {:class "border-b border-stone-200 px-4 lg:px-8 h-14 flex items-center gap-3 sticky top-0 bar-bg z-30"}
   [:button {:class "lg:hidden btn btn-sm btn-ghost btn-square shrink-0"
             :onclick "sidebarOpen()"}
    [:i {:data-lucide "menu" :class "icon-md"}]]
   [:div {:class "text-sm text-stone-700 font-medium min-w-0 truncate"} crumbs]
   [:div {:class "ml-auto flex items-center gap-1.5 shrink-0"}
    (when (pos? (or queue-processing 0))
      [:div {:class "hidden sm:block text-xs text-stone-500"}
       (str queue-processing " articles enriching")])
    [:div {:class "notif-bell-wrap"}
     [:button {:class "btn btn-sm btn-ghost btn-square" :onclick "notifOpen()"}
      [:i {:data-lucide "bell" :class "icon-md"}]]
     [:span {:id    "badge-notif"
             :class (str "notif-bell-badge" (when-not (pos? (or notif-count 0)) " hidden"))}
      (when (pos? (or notif-count 0)) (str notif-count))]]
    [:button {:class "btn btn-sm btn-ghost btn-square"
              :onclick "var d=document.documentElement;d.classList.toggle('dark');localStorage.setItem('theme',d.classList.contains('dark')?'dark':'light')"}
     [:i {:data-lucide "sun-moon" :class "icon-md"}]]
    [:button {:class "btn btn-sm border-none text-white gap-1.5"
              :style {:background "#8B5A3C"}
              :onclick "document.getElementById('add-url-modal').showModal();setTimeout(function(){var i=document.getElementById('add-url-input');if(i)i.focus()},50)"}
     [:i {:data-lucide "plus" :class "icon-md"}]
     [:span {:class "hidden sm:inline"} "Add URL"]]]])

(def ^:private kind-options
  [{:value "auto"     :icon "sparkles"       :label "Auto"     :color "#8B5A3C"}
   {:value "article"  :icon "newspaper"      :label "Article"  :color "#44403c"}
   {:value "video"    :icon "play-circle"    :label "Video"    :color "#dc2626"}
   {:value "bookmark" :icon "bookmark"       :label "Bookmark" :color "#2563eb"}
   {:value "thread"   :icon "message-square" :label "Thread"   :color "#7c3aed"}
   {:value "paper"    :icon "file-text"      :label "Paper"    :color "#059669"}])

(def ^:private add-url-modal
  [:dialog {:id "add-url-modal" :class "modal"}
   [:div {:class "modal-box max-w-lg rounded-xl bg-white border border-stone-200"}
    [:h3 {:class "font-serif text-2xl mb-1"} "Add link"]
    [:p {:class "text-sm text-stone-500 mb-5"} "Claude will enrich it in the background."]
    [:form {:hx-post "/api/add"
            :hx-target "#add-url-result"
            :hx-swap "innerHTML"
            :hx-on--after-request "if(event.detail.successful){this.reset();document.getElementById('add-url-modal').close();}"}
     [:div {:class "mb-4"}
      [:input {:id "add-url-input" :name "url" :type "url" :placeholder "https://…" :required true
               :class "input input-bordered w-full bg-stone-50 focus:bg-white"
               :oninput "detectAddKind(this.value)"}]]
     [:div {:class "mb-4"}
      [:p {:class "text-xs font-medium text-stone-500 mb-2"} "Content type"]
      [:div {:class "flex gap-1.5 flex-wrap"}
       (for [{:keys [value icon label color]} kind-options]
         [:label {:class "kind-opt-label cursor-pointer"}
          [:input {:type "radio" :name "kind" :value value :class "sr-only kind-opt-radio"
                   :checked (= value "auto")}]
          [:span {:class "kind-opt-pill flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-stone-200 text-xs font-medium text-stone-600 hover:border-stone-300 transition-all select-none"
                  :data-color color}
           [:i {:data-lucide icon :style {:width "13px" :height "13px" :flex-shrink "0"}}]
           label]])]]
     [:button {:class "btn w-full border-none text-white" :style {:background "#8B5A3C"}} "Save"]]
    [:div {:id "add-url-result" :class "mt-3 text-sm min-h-5"}]
    [:div {:class "modal-action mt-2"}
     [:form {:method "dialog"}
      [:button {:class "btn btn-ghost btn-sm"} "Close"]]]]
   [:form {:method "dialog" :class "modal-backdrop"}
    [:button "close"]]])

;; ---------------------------------------------------------------------------
;; Public API

(defn page
  "Render a full app page with sidebar + topbar. Returns a Ring response map.
   opts keys: :active :title :crumbs :inbox-count :queue-count :notif-count :queue-processing :folders :active-folder-id"
  [{:keys [active title crumbs inbox-count queue-count notif-count queue-processing folders active-folder-id]
    :or   {inbox-count 0 queue-count 0 notif-count 0 queue-processing 0 folders []}}
   body]
  {:status  200
   :headers {"content-type" "text/html; charset=UTF-8"}
   :body
   (str "<!doctype html>"
        "<html lang='en' data-theme='readlater'>"
        (head-html (or title "Readlater"))
        "<body class='min-h-screen' hx-boost='true'>"
        (rum/render-static-markup
          [:<>
           [:div {:class "flex"}
            (sidebar {:active active :active-folder-id active-folder-id
                      :inbox-count inbox-count :queue-count queue-count :folders folders})
            [:main {:class "flex-1 min-w-0"}
             (topbar {:crumbs crumbs :queue-processing queue-processing :notif-count notif-count})
             body]]
           add-url-modal
           create-folder-modal
           [:div {:id "mob-overlay" :onclick "sidebarClose()"}]
           [:button {:class "mob-search-fab" :onclick "cmdOpen()"}
            [:i {:data-lucide "search" :style {:width "15px" :height "15px" :flex-shrink "0"}}]
            "Search…"]
           [:div {:id "toast-container"}]
           [:div {:id "notif-overlay" :onclick "notifClose()"}]
           [:div {:id "notif-drawer"}
            [:div {:hx-get     "/api/notifications/drawer"
                   :hx-trigger "notifOpened from:body"
                   :hx-target  "#notif-drawer"
                   :hx-swap    "innerHTML"}
             [:div {:class "flex items-center justify-between px-4 py-3.5 border-b border-stone-100"}
              [:div {:class "flex items-center gap-2"}
               [:i {:data-lucide "bell" :class "icon-md text-stone-600"}]
               [:span {:class "font-semibold text-stone-900 text-[15px]"} "Notifications"]]
              [:button {:class "btn btn-sm btn-ghost btn-square text-stone-400" :onclick "notifClose()"}
               [:i {:data-lucide "x" :class "icon-md"}]]]
             [:div {:class "flex items-center justify-center h-40"}
              [:div {:class "loading loading-spinner loading-sm text-stone-300"}]]]]])
        ;; ⌘K command palette — emitted as raw HTML to avoid rum style-map restriction
        "<div id='cmd-overlay' onclick='if(event.target===this)cmdClose()' style='display:none;position:fixed;inset:0;z-index:500;background:rgba(10,8,5,.45);backdrop-filter:blur(3px);-webkit-backdrop-filter:blur(3px)'>"
        "<div id='cmd-search-box' style='position:absolute;top:18%;left:0;right:0;margin:0 auto;width:min(580px,92vw);background:#FDFBF7;border:1px solid #EDE7DD;border-radius:18px;box-shadow:0 8px 48px rgba(20,15,8,.24);overflow:hidden;will-change:transform'>"
        "<div style='display:flex;align-items:center;gap:.75rem;padding:.875rem 1.125rem;border-bottom:1px solid #EDE7DD'>"
        "<svg xmlns='http://www.w3.org/2000/svg' width='17' height='17' viewBox='0 0 24 24' fill='none' stroke='#A89880' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round' style='flex-shrink:0'><circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/></svg>"
        "<input id='cmd-input' type='text' placeholder='Search articles…' autocomplete='off' spellcheck='false' style='flex:1;background:transparent;border:none;outline:none;font-size:15px;color:#1A1A1A;font-family:inherit'/>"
        "<kbd style='font-size:11px;color:#8B7355;background:#F4F1EA;border:1px solid #E2DACB;border-radius:5px;padding:2px 6px;flex-shrink:0'>esc</kbd>"
        "</div>"
        "<div id='cmd-results' style='max-height:400px;overflow-y:auto;padding:.375rem'></div>"
        "</div></div>"
        "<script>
function sidebarOpen(){var s=document.getElementById('sidebar');var o=document.getElementById('mob-overlay');if(s)s.classList.add('open');if(o)o.classList.add('open');document.body.style.overflow='hidden';}
function sidebarClose(){var s=document.getElementById('sidebar');var o=document.getElementById('mob-overlay');if(s)s.classList.remove('open');if(o)o.classList.remove('open');document.body.style.overflow='';}
var _notifLoaded=false;
function notifOpen(){document.getElementById('notif-overlay').classList.add('open');document.getElementById('notif-drawer').classList.add('open');document.body.style.overflow='hidden';if(!_notifLoaded){_notifLoaded=true;document.body.dispatchEvent(new Event('notifOpened'));}}
function notifClose(){document.getElementById('notif-overlay').classList.remove('open');document.getElementById('notif-drawer').classList.remove('open');document.body.style.overflow='';}
var _TICONS={success:'<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"17\" height=\"17\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#10b981\" stroke-width=\"2.2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><polyline points=\"22 4 12 14.01 9 11.01\"/></svg>',error:'<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"17\" height=\"17\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#ef4444\" stroke-width=\"2.2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"15\" y1=\"9\" x2=\"9\" y2=\"15\"/><line x1=\"9\" y1=\"9\" x2=\"15\" y2=\"15\"/></svg>',warning:'<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"17\" height=\"17\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#f59e0b\" stroke-width=\"2.2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z\"/><line x1=\"12\" y1=\"9\" x2=\"12\" y2=\"13\"/><line x1=\"12\" y1=\"17\" x2=\"12.01\" y2=\"17\"/></svg>',info:'<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"17\" height=\"17\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"#3b82f6\" stroke-width=\"2.2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/></svg>'};
function showToast(o){var type=o.type||'info',title=o.title||'',body=o.body||'',link=o.link||'';var c=document.getElementById('toast-container');if(!c)return;var t=document.createElement('div');t.className='toast-item toast-'+type;t.innerHTML='<div style=\"flex-shrink:0;margin-top:1px\">'+(_TICONS[type]||_TICONS.info)+'</div><div style=\"flex:1;min-width:0\"><p style=\"font-size:13px;font-weight:500;color:#1a1a1a;line-height:1.4\">'+title+'</p>'+(body?'<p style=\"font-size:12px;color:#6b7280;margin-top:1px;line-height:1.4\">'+body+'</p>':'')+(link?'<a href=\"'+link+'\" style=\"font-size:12px;color:#8B5A3C;text-decoration:underline;margin-top:3px;display:inline-block;font-weight:500\">Open article \\u2192</a>':'')+'</div><button onclick=\"this.closest(\\'.toast-item\\').remove()\" style=\"flex-shrink:0;width:20px;height:20px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#9ca3af;font-size:15px;line-height:1;cursor:pointer;border:none;background:none;padding:0;margin-top:-1px\">&times;</button><div class=\"toast-progress\"></div>';c.appendChild(t);requestAnimationFrame(function(){requestAnimationFrame(function(){t.classList.add('toast-in');});});var timer=setTimeout(function(){dismissToast(t);},4000);t.querySelector('button').addEventListener('click',function(){clearTimeout(timer);});}
function dismissToast(t){t.classList.add('toast-out');t.classList.remove('toast-in');setTimeout(function(){if(t.parentNode)t.parentNode.removeChild(t);},300);}
if(window._showToastH)document.removeEventListener('showToast',window._showToastH);
window._showToastH=function(e){showToast(e.detail||{});};
document.addEventListener('showToast',window._showToastH);
var _cmdTimer,_srchAnim;
function cmdOpen(){
  var o=document.getElementById('cmd-overlay');
  var fab=document.querySelector('.mob-search-fab');
  var box=document.getElementById('cmd-search-box');
  var inp=document.getElementById('cmd-input');
  o.style.display='block';
  inp.value='';
  document.getElementById('cmd-results').innerHTML='<p style=\"text-align:center;padding:1.5rem;font-size:13px;color:#A89880\">Start typing to search…</p>';
  if(fab&&box&&'animate'in box&&window.innerWidth<1024){
    requestAnimationFrame(function(){
      var fr=fab.getBoundingClientRect(),br=box.getBoundingClientRect();
      var dx=fr.left+fr.width/2-(br.left+br.width/2);
      var dy=fr.top+fr.height/2-(br.top+br.height/2);
      var sx=fr.width/br.width,sy=fr.height/br.height;
      if(_srchAnim)_srchAnim.cancel();
      _srchAnim=box.animate([
        {transform:'translate('+dx+'px,'+dy+'px) scale('+sx+','+sy+')',opacity:.9},
        {transform:'translate(0,0) scale(1)',opacity:1}
      ],{duration:400,easing:'cubic-bezier(.34,1.4,.64,1)',fill:'forwards'});
      fab.animate([
        {opacity:1,transform:'translateX(-50%) scale(1)'},
        {opacity:0,transform:'translateX(-50%) scale(.78)'}
      ],{duration:160,easing:'ease-in',fill:'forwards'});
      _srchAnim.finished.then(function(){inp.focus();});
    });
  } else {
    inp.focus();
  }
}
function cmdClose(){
  var o=document.getElementById('cmd-overlay');
  var fab=document.querySelector('.mob-search-fab');
  var box=document.getElementById('cmd-search-box');
  if(fab&&box&&'animate'in box&&window.innerWidth<1024){
    var fr=fab.getBoundingClientRect(),br=box.getBoundingClientRect();
    var dx=fr.left+fr.width/2-(br.left+br.width/2);
    var dy=fr.top+fr.height/2-(br.top+br.height/2);
    var sx=fr.width/br.width,sy=fr.height/br.height;
    if(_srchAnim)_srchAnim.cancel();
    _srchAnim=box.animate([
      {transform:'translate(0,0) scale(1)',opacity:1},
      {transform:'translate('+dx+'px,'+dy+'px) scale('+sx+','+sy+')',opacity:.85}
    ],{duration:280,easing:'cubic-bezier(.4,0,.6,1)',fill:'forwards'});
    fab.animate([
      {opacity:0,transform:'translateX(-50%) scale(.78)'},
      {opacity:1,transform:'translateX(-50%) scale(1)'}
    ],{duration:240,delay:100,easing:'cubic-bezier(.34,1.4,.64,1)',fill:'forwards'});
    _srchAnim.finished.then(function(){
      o.style.display='none';
      if(_srchAnim){_srchAnim.cancel();_srchAnim=null;}
    });
  } else {
    o.style.display='none';
  }
}
function cmdSearch(q){
  clearTimeout(_cmdTimer);
  if(q.length<2){document.getElementById('cmd-results').innerHTML='<p style=\"text-align:center;padding:1.5rem;font-size:13px;color:#A89880\">Type at least 2 characters…</p>';return;}
  _cmdTimer=setTimeout(function(){
    fetch('/api/search/html?style=palette&q='+encodeURIComponent(q))
      .then(function(r){return r.text();})
      .then(function(html){document.getElementById('cmd-results').innerHTML=html;});
  },200);
}
if(window._keydownH)document.removeEventListener('keydown',window._keydownH);
window._keydownH=function(e){
  if((e.metaKey||e.ctrlKey)&&e.key==='k'){e.preventDefault();cmdOpen();return;}
  if(e.key==='Escape'){cmdClose();notifClose();}
  var results=document.getElementById('cmd-results');
  if(!results)return;
  var items=Array.from(results.querySelectorAll('a.cmd-result'));
  if(!items.length)return;
  var focused=document.activeElement;
  var idx=items.indexOf(focused);
  if(e.key==='ArrowDown'){e.preventDefault();if(idx<items.length-1)items[idx+1].focus();else if(idx===-1)items[0].focus();}
  if(e.key==='ArrowUp'){e.preventDefault();if(idx>0)items[idx-1].focus();else document.getElementById('cmd-input').focus();}
};
document.addEventListener('keydown',window._keydownH);
document.addEventListener('DOMContentLoaded',function(){
  var inp=document.getElementById('cmd-input');
  if(inp)inp.addEventListener('input',function(){cmdSearch(this.value.trim());});
});
lucide.createIcons();
(function(){
  var lastCheck=Date.now();
  function checkReady(){
    var since=lastCheck;
    lastCheck=Date.now();
    fetch('/api/events/recent-ready?since='+since)
      .then(function(r){return r.json();})
      .then(function(d){
        if(d.articles&&d.articles.length>0){
          var a=d.articles[0];
          showToast({type:'success',title:(a.title||'Article')+' is ready',body:'Tap to open',link:'/article/'+a.id});
        }
      })
      .catch(function(){});
  }
  if(window._checkReadyTimer)clearInterval(window._checkReadyTimer);
  window._checkReadyTimer=setInterval(checkReady,5000);
})();
if(window._afterSettleH)document.removeEventListener('htmx:afterSettle',window._afterSettleH);
window._afterSettleH=function(){lucide.createIcons();var inp=document.getElementById('cmd-input');if(inp)inp.addEventListener('input',function(){cmdSearch(this.value.trim());});};
document.addEventListener('htmx:afterSettle',window._afterSettleH);
(function(){
  function detectKindFromUrl(url){
    var u=url.toLowerCase();
    if(u.includes('youtube.com/watch')||u.includes('youtu.be/')||u.includes('vimeo.com/')&&!u.includes('vimeo.com/user')||u.includes('twitch.tv/videos')||u.includes('dailymotion.com/video'))return 'video';
    if(u.includes('arxiv.org/')||u.includes('doi.org/')||u.includes('researchgate.net/publication')||u.includes('semanticscholar.org/paper')||u.includes('ncbi.nlm.nih.gov/pmc'))return 'paper';
    if((u.includes('twitter.com/')&&u.includes('/status/'))||(u.includes('x.com/')&&u.includes('/status/'))||(u.includes('reddit.com/r/')&&u.includes('/comments/'))||u.includes('news.ycombinator.com/item')||u.includes('lobste.rs/s/'))return 'thread';
    return null;
  }
  window.detectAddKind=function(url){
    var detected=detectKindFromUrl(url);
    var target=detected||'auto';
    var radios=document.querySelectorAll('.kind-opt-radio');
    radios.forEach(function(r){if(r.value===target)r.checked=true;});
  };
  document.addEventListener('htmx:afterSettle',function(){
    document.querySelectorAll('.kind-opt-pill').forEach(function(p){
      p.style.setProperty('--kc',p.dataset.color);
    });
  });
  document.querySelectorAll('.kind-opt-pill').forEach(function(p){
    p.style.setProperty('--kc',p.dataset.color);
  });
})();
</script>"
        "</body></html>")})

(defn on-error [{:keys [status] :as _ctx}]
  {:status  status
   :headers {"content-type" "text/html"}
   :body    (str "<!doctype html><html><body><h1>"
                 (if (= status 404) "Page not found." "Something went wrong.")
                 "</h1></body></html>")})
