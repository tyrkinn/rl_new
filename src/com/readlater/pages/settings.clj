(ns com.readlater.pages.settings
  (:require [com.readlater.db :as db]
            [com.readlater.ui :as ui]))

(def ^:private quick-action-script
  "for url in \"$@\"; do\n  curl -s -X POST http://localhost:7777/api/add \\\n    -d \"url=$url&source=service\" > /dev/null\ndone")

(defn settings-page [{:keys [biff/db]}]
  (ui/page (merge (db/base-page-opts db) {:active :settings :title "Settings" :crumbs "Settings"})
           [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-3xl mx-auto w-full"}
            [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Settings"]
            [:section {:class "mt-8"}
             [:h2 {:class "text-xl font-semibold mb-1"} "macOS Quick Action"]
             [:p {:class "text-sm text-stone-500 mb-6"}
              "Сохраняй любую ссылку правой кнопкой мыши или горячей клавишей."]
             [:ol {:class "space-y-5"}
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "1"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Открой Automator"]
                [:p {:class "text-sm text-stone-500"} "Spotlight → Automator, создай новый документ типа " [:strong "Quick Action"] "."]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "2"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Настрой входные данные"]
                [:p {:class "text-sm text-stone-500"}
                 "Вверху: «Workflow receives current» → " [:strong "URLs"] " → «in any application»."]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "3"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700 mb-2"} "Добавь «Run Shell Script»"]
                [:p {:class "text-sm text-stone-500 mb-3"}
                 "В библиотеке: Utilities → " [:strong "Run Shell Script"] ". Установи «Pass input» → " [:strong "as arguments"] ". Вставь:"]
                [:div {:class "relative group"}
                 [:pre {:class "font-mono text-xs bg-stone-900 text-stone-100 rounded-lg px-4 py-3 overflow-x-auto leading-relaxed"}
                  quick-action-script]
                 [:button {:class   "absolute top-2 right-2 sm:opacity-0 sm:group-hover:opacity-100 btn btn-xs bg-stone-700 text-stone-200 border-none transition-opacity"
                           :onclick "navigator.clipboard.writeText(this.previousElementSibling.textContent);this.textContent='Copied!';setTimeout(()=>this.textContent='Copy',1500)"}
                  "Copy"]]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "4"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Сохрани как «Save to Readlater»"]
                [:p {:class "text-sm text-stone-500"} "File → Save, имя: «Save to Readlater»."]]]
              [:li {:class "flex gap-4"}
               [:span {:class "flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-semibold text-white" :style {:background "#8B5A3C"}} "5"]
               [:div
                [:p {:class "text-sm font-medium text-stone-700"} "Назначь горячую клавишу (опционально)"]
                [:p {:class "text-sm text-stone-500"}
                 "System Settings → Keyboard → Keyboard Shortcuts → Services → найди «Save to Readlater» → назначь "
                 [:kbd {:class "font-mono text-xs bg-stone-100 border border-stone-300 rounded px-1.5 py-0.5"} "⌘⇧S"] "."]]]]
             [:div {:class "mt-6 p-4 rounded-lg bg-stone-50 border border-stone-200 flex gap-3"}
              [:i {:data-lucide "mouse-pointer-2" :class "icon-md text-stone-400 shrink-0 mt-0.5"}]
              [:p {:class "text-sm text-stone-500"}
               "После настройки: выдели URL на любой странице → правая кнопка → Services → "
               [:strong "Save to Readlater"] ". Или просто нажми "
               [:kbd {:class "font-mono text-xs bg-stone-100 border border-stone-300 rounded px-1.5 py-0.5"} "⌘⇧S"]
               " когда ссылка выделена."]]]]))

(def routes    [["/settings" {:get #'settings-page}]])
(def api-routes [])
