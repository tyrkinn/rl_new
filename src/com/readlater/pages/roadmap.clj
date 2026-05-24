(ns com.readlater.pages.roadmap
  (:require [com.readlater.db :as db]
            [com.readlater.ui :as ui]))

;; ---------------------------------------------------------------------------
;; Changelog data

(def ^:private changelog
  [{:version "0.6"
    :date     "Май 2026"
    :label    "current"
    :items    ["Saved: поиск по Meilisearch с дебаунсом + фильтр по типу (Bookmarks / Threads / Papers)"
               "Discover: редизайн в editorial bento-сетку — цветовая идентичность источника, компактные строки"
               "Discover на мобильном: кнопка «+» всегда видна на тач-устройствах, правильный hover в тёмной теме"
               "Кнопка «Копировать ссылку» на всех карточках inbox, saved и в строках списка"
               "Статья: кнопка «Mark read» меняется на «Move to unread» если статья уже прочитана"
               "Оба действия (read/unread) сразу обновляют индекс Meilisearch"
               "Архив: фильтрация по status=:read вместо наличия read-at — unread-toggle работает корректно"]}
   {:version "0.5"
    :date     "Май 2026"
    :items    ["Страница Today: персональные подборки из библиотеки + внешние источники"
               "Discover: HackerNews, Lobsters, Dev.to, Reddit — тематические коллекции через AI"
               "Trending Repos: карточка с трендовыми GitHub-репозиториями"
               "Видеорекомендации по интересам из настроек (YouTube через HN)"
               "Интересы в настройках влияют на всю генерацию Discover"
               "Подробное саммари статьи: генерируется в фоне, на русском"
               "Очередь показывает статус Summarizing с фиолетовым индикатором"
               "Уведомление при готовности саммари"
               "Кнопка «Копировать ссылку» в карточках и на странице статьи"]}
   {:version "0.4"
    :date     "Май 2026"
    :items    ["Полноценная тёмная тема — сайдбар, карточки, бейджи, модалки, command palette"
               "Исправлен клавиатурный фокус на планшете при навигации"
               "Страница Roadmap с changelog и планами развития"]}
   {:version "0.3"
    :date     "Май 2026"
    :items    ["Типы ссылок: статья, видео, закладка, тред, бумага"
               "Страница Saved — закладки, треды и статьи отдельно"
               "Авто-определение типа по URL при добавлении"
               "Бейдж типа на странице статьи"
               "Миграция существующих ссылок при старте"]}
   {:version "0.2"
    :date     "Май 2026"
    :items    ["Уведомления: тосты с иконками по типу события"
               "Колокольчик в топбаре со счётчиком непрочитанных"
               "Шторка уведомлений"
               "Живые счётчики в сайдбаре (inbox, queue)"]}
   {:version "0.1"
    :date     "Май 2026"
    :items    ["Первый запуск — inbox, обогащение через Claude"
               "TLDR, теги, время чтения, оценка качества"
               "Папки с цветами"
               "Поиск через Meilisearch"
               "Вид по неделям, архив, очередь"
               "macOS Quick Action для сохранения из браузера"]}])

;; ---------------------------------------------------------------------------
;; Roadmap data

(def ^:private roadmap
  [{:phase    "1"
    :title    "Захват контента"
    :color    "#6B9E7E"
    :status   :next
    :features [{:icon "puzzle"   :title "Browser Extension"
                :desc "Chrome, Firefox, Safari — сохраняй одним кликом не открывая приложение."}
               {:icon "share-2"  :title "iOS / Android Share Sheet"
                :desc "Делись ссылкой напрямую в Readlater из любого приложения."}
               {:icon "mail"     :title "Email-forwarding"
                :desc "Личный адрес для пересылки писем: newsletters падают прямо в inbox."}
               {:icon "rss"      :title "RSS / Atom подписки"
                :desc "Подпишись на фид — новые статьи появляются автоматически."}]}
   {:phase    "2"
    :title    "Командная палитра"
    :color    "#8B5A3C"
    :status   :planned
    :features [{:icon "terminal"    :title "Команды в поиске"
                :desc "Вводи > и получаешь список действий: добавить URL, перейти на страницу, сменить тему."}
               {:icon "zap"         :title "Быстрые действия"
                :desc "Пометить прочитанным, переместить в папку, добавить тег — не покидая клавиатуру."}
               {:icon "keyboard"    :title "Горячие клавиши"
                :desc "Полная навигация по сайту с клавиатуры через единый интерфейс поиска."}]}
   {:phase    "3"
    :title    "AI & Intelligence"
    :color    "#6B85A0"
    :status   :planned
    :features [{:icon "message-circle" :title "Чат с библиотекой"
                :desc "Спрашивай Claude: «Что я сохранял про Clojure в этом месяце?»"}
               {:icon "search"         :title "Семантический поиск"
                :desc "Поиск по смыслу, а не только по ключевым словам."}
               {:icon "link-2"         :title "Связанные материалы"
                :desc "Автоматическая линковка статей на похожие темы."}]}
   {:phase    "4"
    :title    "Рекомендации"
    :color    "#9B8EC4"
    :status   :planned
    :features [{:icon "sparkles"    :title "Похожие статьи"
                :desc "На основе твоей библиотеки Claude предлагает статьи по тем же темам."}
               {:icon "globe"       :title "Интересные сайты"
                :desc "Подборка источников, которые стоит читать регулярно — по твоим интересам."}
               {:icon "trending-up" :title "Что сейчас популярно"
                :desc "Trending-материалы из Hacker News, arXiv и других источников под твой профиль."}]}
   {:phase    "5"
    :title    "Персонализация"
    :color    "#5B8A6A"
    :status   :planned
    :features [{:icon "user-plus"   :title "Регистрация и авторизация"
                :desc "Собственный аккаунт: каждый пользователь со своей библиотекой, настройками и подборками."}
               {:icon "key"         :title "Свои API-ключи"
                :desc "Вставь ключ от Anthropic, OpenAI или другого провайдера — приложение использует его вместо встроенного CLI."}
               {:icon "cpu"         :title "Выбор модели"
                :desc "Выбери модель в настройках: Claude Sonnet, Haiku, GPT-4o или любую другую — для каждой функции отдельно."}]}])

;; ---------------------------------------------------------------------------
;; Components

(defn- status-chip [status]
  (case status
    :next    [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-amber-50 text-amber-700 border border-amber-200"}
              [:span {:class "w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"}] "Следующий"]
    :planned [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-blue-50 text-blue-600 border border-blue-100"} "Запланировано"]
    :ideas   [:span {:class "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-stone-100 text-stone-500 border border-stone-200"} "Идеи"]
    nil))

(defn- roadmap-card [{:keys [phase title color status features]}]
  [:div {:class "card-art p-5"}
   [:div {:class "flex items-center justify-between gap-3 mb-4"}
    [:div {:class "flex items-center gap-3"}
     [:div {:class "w-8 h-8 rounded-lg flex items-center justify-center text-white text-xs font-bold shrink-0"
            :style {:background color}}
      (str "v" phase)]
     [:h3 {:class "font-semibold text-stone-800"} title]]
    (status-chip status)]
   [:ul {:class "space-y-3"}
    (for [{:keys [icon title desc]} features]
      [:li {:class "flex gap-3"}
       [:div {:class "w-7 h-7 rounded-lg bg-stone-100 flex items-center justify-center shrink-0 mt-0.5"}
        [:i {:data-lucide icon :style {:width "14px" :height "14px" :color color}}]]
       [:div
        [:p {:class "text-sm font-medium text-stone-700 leading-snug"} title]
        [:p {:class "text-xs text-stone-400 mt-0.5 leading-relaxed"} desc]]])]])

(defn- changelog-entry [{:keys [version date label items]}]
  [:div {:class "flex gap-4"}
   [:div {:class "flex flex-col items-center"}
    [:div {:class "w-2.5 h-2.5 rounded-full mt-1 shrink-0"
           :style {:background (if label "#8B5A3C" "#D6CFC8")}}]
    [:div {:class "w-px flex-1 bg-stone-200 mt-1"}]]
   [:div {:class "pb-8 -mt-0.5"}
    [:div {:class "flex items-center gap-2 mb-2 flex-wrap"}
     [:span {:class "font-semibold text-stone-800"} (str "v" version)]
     [:span {:class "text-xs text-stone-400"} date]
     (when label
       [:span {:class "inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-emerald-50 text-emerald-700 border border-emerald-200"}
        "current"])]
    [:ul {:class "space-y-1"}
     (for [item items]
       [:li {:class "flex items-start gap-2 text-sm text-stone-600"}
        [:span {:class "text-stone-300 shrink-0 mt-1"} "·"]
        item])]]])

;; ---------------------------------------------------------------------------
;; Page

(defn roadmap-page [{:keys [biff/db]}]
  (ui/page (merge (db/base-page-opts db) {:active :roadmap :title "Roadmap" :crumbs "Roadmap"})
           [:div {:class "px-4 sm:px-6 lg:px-10 py-6 sm:py-8 max-w-4xl mx-auto w-full"}
            [:h1 {:class "serif-h1 text-3xl sm:text-4xl mb-2"} "Roadmap"]
            [:p {:class "text-sm text-stone-500 mb-10"}
             "Что уже сделано и куда движемся дальше."]

            ;; Roadmap grid
            [:section {:class "mb-14"}
             [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-4 flex items-center gap-2"}
              [:i {:data-lucide "map" :class "icon-sm"}]
              "Планы"]
             [:div {:class "grid sm:grid-cols-2 gap-4"}
              (map roadmap-card roadmap)]]

            ;; Changelog timeline
            [:section
             [:h2 {:class "text-xs font-semibold uppercase tracking-wider text-stone-400 mb-6 flex items-center gap-2"}
              [:i {:data-lucide "history" :class "icon-sm"}]
              "Changelog"]
             (map changelog-entry changelog)]]))

(def routes    [["/roadmap" {:get #'roadmap-page}]])
(def api-routes [])
