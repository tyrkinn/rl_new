# Read Later — приложение для отложенного чтения

Стартовый проект на основе **[Biff](https://github.com/jacobobryant/biff)** — фреймворка для создания веб-приложений на Clojure.

## 📖 О проекте

Это полнофункциональное веб-приложение, позволяющее пользователям сохранять статьи и ссылки для последующего чтения. Проект демонстрирует лучшие практики разработки на Clojure с использованием:

- **Biff** — фреймворк для веб-разработки
- **XTDB** — документоориентированная база данных
- **Ring/Reitit** — маршрутизация и HTTP-сервер
- **Hato** — HTTP-клиент
- **Malli** — валидация схем данных
- **Tailwind CSS** — стилизация интерфейса

## 🚀 Быстрый старт

### Требования

- [Java 21+](https://adoptium.net/)
- [Clojure tools.deps](https://clojure.org/guides/install_clojure)
- Bash (для Linux/macOS)

### Запуск в режиме разработки

1. **Клонируйте репозиторий:**
   ```bash
   git clone <repository-url>
   cd <project-directory>
   ```

2. **Создайте файл конфигурации:**
   ```bash
   cp resources/config.template.env resources/config.env
   ```

3. **Настройте переменные окружения** в `resources/config.env`:
   - `DOMAIN` — домен вашего приложения
   - `MAILERSEND_API_KEY`, `MAILERSEND_FROM`, `MAILERSEND_REPLY_TO` — настройки почты
   - `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` — защита от ботов
   - Секреты можно сгенерировать автоматически:
     ```bash
     clj -M:dev generate-secrets
     ```

4. **Запустите приложение:**
   ```bash
   clj -M:dev dev
   ```

5. **Откройте браузер** по адресу `http://localhost:8080`

> 💡 **Совет:** Добавьте алиас в `.bashrc` или `.zshrc`:
> ```bash
> alias biff='clj -M:dev'
> ```
> Тогда запуск будет выглядеть проще: `biff dev`

## 🛠 Разработка

### REPL-driven разработка

Проект поддерживает горячую перезагрузку кода. При сохранении файлов изменения автоматически применяются.

Подключитесь к nREPL-серверу (порт `7888`) из вашей IDE:

- **Emacs/CIDER:** `M-x cider-connect`
- **VS Code/Calva:** Connect to a Running REPL
- **IntelliJ/Cursive:** nREPL → Connect to nREPL server

### Полезные команды

| Команда | Описание |
|---------|----------|
| `clj -M:dev dev` | Запуск в режиме разработки |
| `clj -M:dev generate-secrets` | Генерация секретов |
| `clj -M:dev --help` | Список всех доступных команд |
| `clj -M:dev test` | Запуск тестов |
| `clj -M:dev uberjar` | Сборка production-версии |

### Добавление тестовых данных

В REPL выполните:
```clojure
(repl/add-fixtures)
```

Для сброса базы данных (только в dev):
```bash
rm -rf storage/xtdb
```
Затем перезапустите приложение и добавьте фикстуры снова.

## 📦 Структура проекта

```
.
├── src/                    # Исходный код приложения
│   └── com/
│       └── readlater/      # Основные модули
│           ├── app.clj     # Основной модуль приложения
│           ├── middleware.clj
│           ├── schema.clj  # Схемы данных Malli
│           ├── ui.clj      # UI компоненты (Rum)
│           ├── worker.clj  # Фоновые задачи
│           └── ...
├── resources/              # Ресурсы и конфигурация
│   ├── config.edn          # Основная конфигурация
│   ├── config.template.env # Шаблон переменных окружения
│   ├── fixtures.edn        # Тестовые данные
│   └── public/             # Статические файлы
├── dev/                    # Инструменты разработки
│   ├── repl.clj            # REPL утилиты
│   └── tasks.clj           # Пользовательские задачи
├── test/                   # Тесты
├── deps.edn                # Зависимости Clojure
├── Dockerfile              # Docker-образ для продакшена
└── server-setup.sh         # Скрипт настройки сервера
```

## 🌐 Развёртывание в продакшене

### Вариант 1: Docker

1. **Соберите образ:**
   ```bash
   docker build -t your-app .
   ```

2. **Запустите контейнер:**
   ```bash
   docker run --rm \
     -e BIFF_PROFILE=prod \
     -e DOMAIN=yourdomain.com \
     -e MAILERSEND_API_KEY=your_key \
     # ... другие переменные окружения
     -p 8080:8080 \
     your-app
   ```

### Вариант 2: Прямая установка на сервер (Ubuntu/Debian)

1. **Настройте сервер:**
   ```bash
   bash server-setup.sh prod
   ```
   Скрипт установит:
   - Java, Clojure, Babashka
   - Nginx с настройками проксирования
   - systemd-сервис для приложения
   - SSL-сертификаты через Let's Encrypt

2. **Настройте CI/CD:**
   - Настройте Git-деплои или используйте rsync
   - Установите необходимые переменные окружения

3. **Управление сервисом:**
   ```bash
   sudo systemctl restart app
   sudo journalctl -u app -f
   ```

### Переменные окружения для продакшена

Обязательно установите следующие переменные:

| Переменная | Описание |
|------------|----------|
| `BIFF_PROFILE` | `prod` для продакшена |
| `DOMAIN` | Домен приложения |
| `HOST` | `0.0.0.0` для Docker |
| `PORT` | Порт приложения (по умолчанию 8080) |
| `MAILERSEND_*` | Настройки почты |
| `RECAPTCHA_*` | Настройки reCAPTCHA |
| `COOKIE_SECRET` | Секрет для шифрования сессий |
| `JWT_SECRET` | Секрет для JWT-токенов |

## 🔧 Конфигурация

Основная конфигурация находится в [`resources/config.edn`](resources/config.edn). Переменные окружения переопределяют значения из этого файла.

### Почта (MailerSend)

Приложение использует MailerSend для отправки писем с ссылками для входа:
- Зарегистрируйтесь на [mailersend.com](https://www.mailersend.com/)
- Верифицируйте домен
- Получите API-ключ

### Защита от ботов (reCAPTCHA)

- Создайте сайт в [Google reCAPTCHA](https://www.google.com/recaptcha/about/)
- Выберите **reCAPTCHA v2 Invisible**
- Добавьте домены: `localhost` и ваш продакшен-домен

### База данных

По умолчанию используется встроенный XTDB. Для продакшена рекомендуется PostgreSQL:

```env
PROD_XTDB_TOPOLOGY=jdbc
XTDB_JDBC_URL=jdbc:postgresql://host:port/dbname?user=alice&password=abc123&sslmode=require
```

## 🧪 Тестирование

```bash
# Запустить все тесты
clj -M:dev test

# Запустить конкретный тест
clj -M:dev test :only com.readlater.app-test
```

## 📚 Ресурсы

- [Документация Biff](https://biffweb.com/)
- [Clojure Guide](https://clojure.org/guides)
- [XTDB Documentation](https://docs.xtdb.com/)
- [Malli GitHub](https://github.com/metosin/malli)

## 🤝 Вклад в проект

1. Fork репозитория
2. Создайте ветку (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'Add amazing feature'`)
4. Запушьте ветку (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📄 Лицензия

Этот проект основан на Biff Starter Project. См. оригинальную лицензию в репозитории [Biff](https://github.com/jacobobryant/biff).

---

**Разработано с ❤️ на Clojure**
