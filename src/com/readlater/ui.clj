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
html.dark,html.dark body{background:#141414;color:#E7E2D6}
html.dark .nav-item{color:#C9C2B4}
html.dark .nav-item:hover{background:#252525}
html.dark .nav-item.active{background:#3a2818;color:#E6C9A8}
#app-toast{position:fixed;top:64px;right:20px;z-index:200;pointer-events:none;display:flex;align-items:center;gap:.375rem;padding:.4rem .875rem;background:#fff;border:1px solid #EDE7DD;border-radius:9999px;box-shadow:0 2px 12px rgba(20,15,8,.12);font-size:13px;font-weight:500;color:#544D3F;opacity:0;transform:translateY(-10px);transition:opacity .18s ease,transform .18s ease}
#app-toast.visible{opacity:1;transform:translateY(0)}
html.dark #app-toast{background:#2a2a2a;border-color:#3a3a3a;color:#C9C2B4}
.tag-pill{display:inline-flex;align-items:center;gap:.375rem;padding:.375rem .75rem;border-radius:9999px;border:1px solid #D6D0C8;background:#fff;color:#544D3F;cursor:pointer;transition:border-color .15s,box-shadow .15s,background .15s;line-height:1.4;text-decoration:none}
.tag-pill:hover{border-color:#B47B53;box-shadow:0 1px 4px rgba(20,15,8,.08)}
.tag-pill.active{border-color:#714630;background:#FBF4EE;color:#553523;font-weight:600;box-shadow:0 1px 4px rgba(20,15,8,.1)}
.tag-pill .tag-badge{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace;font-size:10px;border-radius:9999px;padding:0 .375rem;background:#F4F1EA;color:#8B7355;transition:background .15s,color .15s}
.tag-pill.active .tag-badge{background:#E8C9A8;color:#714630}")

(defn- head-html [title]
  (str "<head>"
       "<meta charset='UTF-8'>"
       "<meta name='viewport' content='width=device-width,initial-scale=1'>"
       "<title>Readlater — " title "</title>"
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

(defn- nav-item [{:keys [href icon label active? badge]}]
  [:a {:href href :class (str "nav-item" (when active? " active"))}
   [:i {:data-lucide icon :class "icon-md"}]
   [:span label]
   (when badge [:span {:class "ml-auto chip chip-mono"} badge])])

(defn- sidebar [{:keys [active inbox-count queue-count]}]
  [:aside {:class "w-60 shrink-0 border-r border-stone-200 bg-stone-50/60 px-3 py-5 flex flex-col h-screen sticky top-0"}
   [:a {:href "/" :class "px-3 mb-8 flex items-center gap-2"}
    [:span {:class "text-lg font-semibold tracking-tight"} "Readlater"]
    [:span {:class "w-1.5 h-1.5 rounded-full bg-emerald-500"}]]
   [:nav {:class "space-y-0.5 flex-1"}
    (nav-item {:href "/"        :icon "sparkles"      :label "Today"   :active? (= active :today)})
    (nav-item {:href "/inbox"   :icon "inbox"         :label "Inbox"   :active? (= active :inbox)   :badge (str inbox-count)})
    (nav-item {:href "/week"    :icon "calendar-days" :label "Week"    :active? (= active :week)})
    (nav-item {:href "/tags"    :icon "tags"          :label "Tags"    :active? (= active :tags)})
    (nav-item {:href "/archive" :icon "archive"       :label "Archive" :active? (= active :archive)})
    (nav-item {:href "/queue"   :icon "list-checks"   :label "Queue"   :active? (= active :queue)   :badge (str queue-count)})]
   [:div {:class "pt-4 border-t border-stone-200 space-y-0.5"}
    [:a {:href "#" :class "nav-item" :onclick "event.preventDefault();cmdOpen()"}
     [:i {:data-lucide "search" :class "icon-md"}]
     [:span "Search"]
     [:span {:class "ml-auto chip chip-mono"} "⌘K"]]
    (nav-item {:href "/settings" :icon "settings" :label "Settings" :active? (= active :settings)})]])

(defn- topbar [{:keys [crumbs queue-processing]}]
  [:header {:class "border-b border-stone-200 px-8 h-14 flex items-center gap-4 sticky top-0 bar-bg z-30"}
   [:div {:class "text-sm text-stone-700 font-medium"} crumbs]
   [:div {:class "ml-auto flex items-center gap-2"}
    (when (pos? (or queue-processing 0))
      [:div {:class "text-xs text-stone-500"}
       (str queue-processing " articles enriching")])
    [:button {:class "btn btn-sm btn-ghost btn-square"
              :onclick "document.documentElement.classList.toggle('dark')"}
     [:i {:data-lucide "sun-moon" :class "icon-md"}]]
    [:button {:class "btn btn-sm border-none text-white gap-1.5"
              :style {:background "#8B5A3C"}
              :onclick "document.getElementById('add-url-modal').showModal()"}
     [:i {:data-lucide "plus" :class "icon-md"}] "Add URL"]]])

(def ^:private add-url-modal
  [:dialog {:id "add-url-modal" :class "modal"}
   [:div {:class "modal-box max-w-lg rounded-xl bg-white border border-stone-200"}
    [:h3 {:class "font-serif text-2xl mb-1"} "Add link"]
    [:p {:class "text-sm text-stone-500 mb-5"} "Claude will enrich the article in the background."]
    [:form {:hx-post "/api/add"
            :hx-target "#add-url-result"
            :hx-swap "innerHTML"
            :hx-on--after-request "if(event.detail.successful) this.reset()"}
     [:div {:class "join w-full"}
      [:input {:name "url" :type "url" :placeholder "https://..." :required true
               :class "input join-item input-bordered w-full bg-stone-100" :autofocus true}]
      [:button {:class "btn join-item border-none text-white" :style {:background "#8B5A3C"}} "Add"]]]
    [:div {:id "add-url-result" :class "mt-3 text-sm"}]
    [:div {:class "modal-action mt-2"}
     [:form {:method "dialog"}
      [:button {:class "btn btn-ghost btn-sm"} "Close"]]]]
   [:form {:method "dialog" :class "modal-backdrop"}
    [:button "close"]]])

;; ---------------------------------------------------------------------------
;; Public API

(defn page
  "Render a full app page with sidebar + topbar. Returns a Ring response map.
   opts keys: :active :title :crumbs :inbox-count :queue-count :queue-processing"
  [{:keys [active title crumbs inbox-count queue-count queue-processing]
    :or   {inbox-count 0 queue-count 0 queue-processing 0}}
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
            (sidebar {:active active :inbox-count inbox-count :queue-count queue-count})
            [:main {:class "flex-1 min-w-0"}
             (topbar {:crumbs crumbs :queue-processing queue-processing})
             body]]
           add-url-modal
           [:div {:id "app-toast"}
            [:span {:style {:color "#8B5A3C"}} "✓"]
            "Saved"]])
        ;; ⌘K command palette — emitted as raw HTML to avoid rum style-map restriction
        "<div id='cmd-overlay' onclick='if(event.target===this)cmdClose()' style='display:none;position:fixed;inset:0;z-index:500;background:rgba(10,8,5,.45);backdrop-filter:blur(3px);-webkit-backdrop-filter:blur(3px)'>"
        "<div style='position:absolute;top:18%;left:50%;transform:translateX(-50%);width:min(580px,92vw);background:#FDFBF7;border:1px solid #EDE7DD;border-radius:18px;box-shadow:0 8px 48px rgba(20,15,8,.24);overflow:hidden'>"
        "<div style='display:flex;align-items:center;gap:.75rem;padding:.875rem 1.125rem;border-bottom:1px solid #EDE7DD'>"
        "<svg xmlns='http://www.w3.org/2000/svg' width='17' height='17' viewBox='0 0 24 24' fill='none' stroke='#A89880' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round' style='flex-shrink:0'><circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/></svg>"
        "<input id='cmd-input' type='text' placeholder='Search articles…' autocomplete='off' spellcheck='false' style='flex:1;background:transparent;border:none;outline:none;font-size:15px;color:#1A1A1A;font-family:inherit'/>"
        "<kbd style='font-size:11px;color:#8B7355;background:#F4F1EA;border:1px solid #E2DACB;border-radius:5px;padding:2px 6px;flex-shrink:0'>esc</kbd>"
        "</div>"
        "<div id='cmd-results' style='max-height:400px;overflow-y:auto;padding:.375rem'></div>"
        "</div></div>"
        "<script>
var _cmdTimer;
function cmdOpen(){var o=document.getElementById('cmd-overlay');o.style.display='block';var i=document.getElementById('cmd-input');i.value='';i.focus();document.getElementById('cmd-results').innerHTML='<p style=\"text-align:center;padding:1.5rem;font-size:13px;color:#A89880\">Start typing to search…</p>';}
function cmdClose(){document.getElementById('cmd-overlay').style.display='none';}
function cmdSearch(q){
  clearTimeout(_cmdTimer);
  if(q.length<2){document.getElementById('cmd-results').innerHTML='<p style=\"text-align:center;padding:1.5rem;font-size:13px;color:#A89880\">Type at least 2 characters…</p>';return;}
  _cmdTimer=setTimeout(function(){
    fetch('/api/search/html?style=palette&q='+encodeURIComponent(q))
      .then(function(r){return r.text();})
      .then(function(html){document.getElementById('cmd-results').innerHTML=html;});
  },200);
}
document.addEventListener('keydown',function(e){
  if((e.metaKey||e.ctrlKey)&&e.key==='k'){e.preventDefault();cmdOpen();return;}
  if(e.key==='Escape'){cmdClose();}
  var results=document.getElementById('cmd-results');
  if(!results)return;
  var items=Array.from(results.querySelectorAll('a.cmd-result'));
  if(!items.length)return;
  var focused=document.activeElement;
  var idx=items.indexOf(focused);
  if(e.key==='ArrowDown'){e.preventDefault();if(idx<items.length-1)items[idx+1].focus();else if(idx===-1)items[0].focus();}
  if(e.key==='ArrowUp'){e.preventDefault();if(idx>0)items[idx-1].focus();else document.getElementById('cmd-input').focus();}
});
document.addEventListener('DOMContentLoaded',function(){
  var inp=document.getElementById('cmd-input');
  if(inp)inp.addEventListener('input',function(){cmdSearch(this.value.trim());});
});
lucide.createIcons();
function showSavedToast(){var t=document.getElementById('app-toast');t.classList.add('visible');clearTimeout(window._toastTimer);window._toastTimer=setTimeout(function(){t.classList.remove('visible')},2000)}
document.addEventListener('htmx:afterSettle',function(){lucide.createIcons();var inp=document.getElementById('cmd-input');if(inp)inp.addEventListener('input',function(){cmdSearch(this.value.trim());});});
</script>"
        "</body></html>")})

(defn on-error [{:keys [status] :as _ctx}]
  {:status  status
   :headers {"content-type" "text/html"}
   :body    (str "<!doctype html><html><body><h1>"
                 (if (= status 404) "Page not found." "Something went wrong.")
                 "</h1></body></html>")})
