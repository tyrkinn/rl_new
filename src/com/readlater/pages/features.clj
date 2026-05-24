(ns com.readlater.pages.features
  (:require [com.readlater.db :as db]
            [com.readlater.ui :as ui]))

;; ---------------------------------------------------------------------------
;; Data

(def ^:private all-features
  [{:icon "inbox"          :color "#6B9E7E" :bg "#EFF5F1"
    :title "Universal Inbox"
    :desc  "Статьи, видео, закладки, треды и бумаги — один Inbox для всего. Любой URL в одно действие."}
   {:icon "cpu"            :color "#8B5A3C" :bg "#FBF4EE"
    :title "AI-обогащение"
    :desc  "Claude составляет TLDR, теги, время чтения, оценку качества и объясняет почему статья интересна."}
   {:icon "sparkles"       :color "#9B8EC4" :bg "#F3F2FA"
    :title "Today — подборка"
    :desc  "Каждый день — персональные коллекции из библиотеки. Claude учитывает темы и не повторяет старое."}
   {:icon "globe"          :color "#6B85A0" :bg "#EEF2F6"
    :title "Discover"
    :desc  "Тематические коллекции из HN, Lobsters, Dev.to и Reddit, отфильтрованные под твои интересы."}
   {:icon "github"         :color "#57534E" :bg "#F5F5F4"
    :title "GitHub Trending"
    :desc  "Карточка с трендовыми репозиториями недели. Добавляй в Inbox одним кликом."}
   {:icon "play-circle"    :color "#DC6B6B" :bg "#FEF2F2"
    :title "Видео по интересам"
    :desc  "YouTube-видео из HN под твои темы из настроек. Обновляются вместе с Today-подборкой."}
   {:icon "align-left"     :color "#6B9E7E" :bg "#EFF5F1"
    :title "Детальное саммари"
    :desc  "Одна кнопка — Claude сходит на страницу и напишет подробный пересказ на русском в фоне."}
   {:icon "search"         :color "#8B5A3C" :bg "#FBF4EE"
    :title "Полнотекстовый поиск"
    :desc  "Meilisearch ищет мгновенно по заголовкам, тегам, TLDR и содержимому всех карточек."}
   {:icon "folder"         :color "#9B8EC4" :bg "#F3F2FA"
    :title "Цветные папки"
    :desc  "Организуй ссылки по темам. Папки с произвольными цветами видны прямо в сайдбаре."}
   {:icon "message-square" :color "#6B85A0" :bg "#EEF2F6"
    :title "Заметки"
    :desc  "Добавляй личные заметки на странице статьи. Хранятся в библиотеке, видны только тебе."}
   {:icon "tags"           :color "#57534E" :bg "#F5F5F4"
    :title "Теги и темы"
    :desc  "Теги и топики назначаются автоматически. Страница Tags для навигации по тематике."}
   {:icon "terminal"       :color "#DC6B6B" :bg "#FEF2F2"
    :title "macOS Quick Action"
    :desc  "Правая кнопка → Services → Save to Readlater. Или горячая клавиша ⌘⇧S из любого браузера."}])

(def ^:private flows
  [{:number "01"
    :title  "Добавь — получи готовую карточку"
    :desc   "Скопируй ссылку, вставь в Inbox. Claude уходит в фон и возвращается с TLDR, тегами и оценкой."
    :color  "#6B9E7E"
    :steps  [{:icon "link"         :label "Копируешь URL"}
             {:icon "plus-circle"  :label "Вставляешь в Inbox"}
             {:icon "loader"       :label "AI обогащает"}
             {:icon "inbox"        :label "Готово в Inbox"}
             {:icon "book-open"    :label "Читаешь с TLDR"}]}
   {:number "02"
    :title  "Открой Today — получи персональную ленту"
    :desc   "Каждое утро Claude анализирует библиотеку и собирает коллекции + забирает лучшее из интернета."
    :color  "#8B5A3C"
    :steps  [{:icon "sun"          :label "Открываешь Today"}
             {:icon "library"      :label "Анализ библиотеки"}
             {:icon "globe"        :label "Скрейп источников"}
             {:icon "sparkles"     :label "Claude группирует"}
             {:icon "layout-grid"  :label "Готовые коллекции"}]}
   {:number "03"
    :title  "Углубись в статью по запросу"
    :desc   "TLDR мало? Одна кнопка — и Claude прочитает статью целиком и напишет подробный пересказ."
    :color  "#6B85A0"
    :steps  [{:icon "file-text"    :label "Открываешь статью"}
             {:icon "list"         :label "Читаешь TLDR"}
             {:icon "sparkles"     :label "Жмёшь саммари"}
             {:icon "loader"       :label "Генерируется в фоне"}
             {:icon "align-left"   :label "Читаешь детально"}]}])

;; ---------------------------------------------------------------------------
;; Components

(defn- feature-item [{:keys [icon color bg title desc]}]
  [:div {:class "flex gap-4"}
   [:div {:class "w-10 h-10 rounded-xl flex items-center justify-center shrink-0 mt-0.5"
          :style {:background bg}}
    [:i {:data-lucide icon :style {:width "18px" :height "18px" :color color}}]]
   [:div
    [:p {:class "text-sm font-semibold text-stone-800 mb-0.5"} title]
    [:p {:class "text-sm text-stone-500 leading-relaxed"} desc]]])

(defn- flow-step [{:keys [icon label]} color]
  [:div {:class "flex flex-col items-center gap-2 text-center w-20 shrink-0"}
   [:div {:class "w-10 h-10 rounded-full flex items-center justify-center"
          :style {:background (str color "18")}}
    [:i {:data-lucide icon :style {:width "16px" :height "16px" :color color}}]]
   [:p {:class "text-[11px] leading-tight text-stone-500 font-medium"} label]])

(defn- flow-arrow [color]
  [:div {:class "flex items-center self-center mb-4 shrink-0"}
   [:svg {:width "20" :height "12" :viewBox "0 0 20 12" :fill "none" :xmlns "http://www.w3.org/2000/svg"}
    [:path {:d "M1 6h16M13 1l6 5-6 5" :stroke color :stroke-width "1.5"
            :stroke-linecap "round" :stroke-linejoin "round" :opacity "0.4"}]]])

(defn- flow-card [{:keys [number title desc color steps]}]
  [:div {:class "card-art p-6 sm:p-7"}
   [:div {:class "flex items-start gap-4 mb-6"}
    [:span {:class "font-mono text-3xl font-bold leading-none shrink-0"
            :style {:color (str color "30")}} number]
    [:div
     [:h3 {:class "font-semibold text-stone-800 mb-1"} title]
     [:p {:class "text-sm text-stone-500 leading-relaxed"} desc]]]
   [:div {:class "flex items-start gap-1 flex-wrap sm:flex-nowrap overflow-x-auto pb-1"}
    (interpose (flow-arrow color)
               (map #(flow-step % color) steps))]])

;; ---------------------------------------------------------------------------
;; Page

(defn features-page [{:keys [biff/db]}]
  (ui/page (merge (db/base-page-opts db) {:active :features :title "Features" :crumbs "Features"})
           [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-10 max-w-5xl mx-auto w-full"}

            ;; Hero
            [:section {:class "mb-14"}
             [:div {:class "max-w-2xl"}
              [:h1 {:class "serif-h1 text-4xl sm:text-5xl mb-4 leading-tight"} "Всё, что нужно,\nчтобы читать лучше"]
              [:p {:class "text-lg text-stone-500 leading-relaxed mb-6"}
               "Readlater — персональная система управления знаниями. Сохраняй ссылки,
                   получай умные карточки через AI, открывай каждое утро персональную подборку."]
              [:div {:class "flex flex-wrap gap-2"}
               (for [[label icon] [["AI-powered" "sparkles"] ["Offline-first" "database"]
                                   ["Open source" "github"] ["Privacy-first" "lock"]]]
                 [:span {:class "inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-stone-100 text-stone-600 border border-stone-200"}
                  [:i {:data-lucide icon :class "icon-sm"}]
                  label])]]]

            ;; Features grid
            [:section {:class "mb-14"}
             [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-8 flex items-center gap-2"}
              [:i {:data-lucide "zap" :class "icon-sm"}]
              "Возможности"]
             [:div {:class "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-7"}
              (map feature-item all-features)]]

            ;; Divider
            [:div {:class "border-t border-stone-200 mb-14"}]

            ;; Flows
            [:section
             [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-6 flex items-center gap-2"}
              [:i {:data-lucide "workflow" :class "icon-sm"}]
              "Основные сценарии"]
             [:div {:class "space-y-4"}
              (map flow-card flows)]]]))

(def routes    [["/features" {:get #'features-page}]])
(def api-routes [])
