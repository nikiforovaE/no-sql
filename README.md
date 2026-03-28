# EventHub - NoSQL Database Project

[![EventHub](https://github.com/nikiforovaE/no-sql/actions/workflows/eventhub.yml/badge.svg)](https://github.com/nikiforovaE/no-sql/actions/workflows/eventhub.yml)

EventHub — backend‑сервис платформы мероприятий, предназначенный для изучения различных подходов к хранению и обработке
данных с использованием NoSQL баз данных.

## 🛠 Технологический стек

- **Язык программирования:** Java 21
- **Фреймворк:** Spring Boot 4.0.3
- **База данных:** Redis 7
- **Сборка:** Maven
- **Оркестрация:** Docker & Docker Compose

## 🚀 Как запустить проект

### Предварительные требования

Для локального запуска вам потребуются установленные:

- [Docker](https://docs.docker.com/get-docker/) и Docker Compose
- Утилита `make`

### 1. Настройка окружения

В корне проекта должен находиться конфигурационный файл `.env.local`.

```env
APP_HOST=localhost
APP_PORT=8080
APP_USER_SESSION_TTL=60

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0
```

### 2. Запуск приложения

В корне проекта выполнить команду:

```
make run
```

*После запуска сервис будет доступен по адресу: `http://localhost:8080`*

### 3. Остановка приложения

Для остановки всех контейнеров выполнить команду:

```bash
make stop
```

---

## 📖 API

Проект предоставляет REST API для управления анонимными сессиями. Сессии хранятся в Redis и идентифицируются через
Cookie `X-Session-Id`.

> 💡 Коллекция запросов Postman находится в папке `api/` в корне репозитория

### 1. Проверка состояния сервиса

`GET /health`

Проверяет работоспособность сервиса. Если клиент передает валидную `X-Session-Id`, она возвращается обратно.
**Запрос без Cookie (нет сессии):**

```http
GET /health
```

**Ответ:**

```http
HTTP/1.1 200 OK
Content-Type: application/json
{"status":"ok"}
```

**Запрос с Cookie (сессия уже существует):**

```http
GET /health
Cookie: X-Session-Id=a3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d
```

**Ответ:**

```http
HTTP/1.1 200 OK
Set-Cookie: X-Session-Id=a3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Type: application/json
{"status":"ok"}
```

### 2. Создание или обновление сессии

`POST /session`

Создает новую анонимную сессию при первом визите пользователя или обновляет TTL существующей сессии при повторном
визите.
**Запрос:**

```http
POST http://localhost:8080/session
```

**Ответ при первом визите (создание сессии):**

```http
HTTP/1.1 201 Created
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Length: 0
```

**Ответ при повторном визите (существующая сессия):**

```http
HTTP/1.1 200 OK
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Length: 0
```
