# EventHub - NoSQL Database Project

[![EventHub](https://github.com/nikiforovaE/no-sql/actions/workflows/eventhub.yml/badge.svg)](https://github.com/nikiforovaE/no-sql/actions/workflows/eventhub.yml)

1. **[Лабораторные работы](https://github.com/sitnikovik/ndbx/tree/main/docs/lab)** — технические задания для каждой
   лабораторной работы

2. **[CONTRIBUTING.md](CONTRIBUTING.md)** — требования к структуре проекта, процесс разработки и проверки

3. **[Документация курса](https://github.com/sitnikovik/ndbx)** — методические материалы и дополнительные ресурсы

EventHub — backend‑сервис платформы мероприятий, предназначенный для изучения различных подходов к хранению и обработке
данных с использованием NoSQL баз данных.

## 🛠 Технологический стек

- **Язык программирования:** Java 21
- **Фреймворк:** Spring Boot 4.0.3
- **База данных:** Redis 7, MongoDB, Apache Cassandra
- **Сборка:** Maven
- **Оркестрация:** Docker & Docker Compose

## 🏗 Архитектура данных

Проект использует NoSQL подход, разделяя данные на два уровня хранения:

### 1. Управление сессиями (Redis)

- **Тип данных:** Hash.
- **Формат ключа:** `sid:{session_id}`, где `sid` — криптографически стойкий 128-битный токен (hex).
- **Поля Hash-таблицы:**
    - `created_at`: метка времени создания.
    - `updated_at`: метка последнего обновления сессии.
    - user_id: идентификатор пользователя из MongoDB (добавляется после успешной аутентификации).
- **Жизненный цикл:** Ограничен TTL (`APP_USER_SESSION_TTL`). Сессия обновляется при вызове `POST /session` и
  `POST /auth/login`.

### 2. Хранение сущностей (MongoDB)

MongoDB выступает основным хранилищем для пользователей и событий. Используется архитектура с шардированием и
репликацией.

#### Коллекция `users`

- **Репликация:** Replica Set (1 primary, 2 secondary).
- **Схема:** `full_name`, `username`, `password_hash`.
- **Индексы:** Уникальный индекс по полю `username`.

#### Коллекция `events`

- **Шардирование:** По хэш-ключу `created_by` (ID организатора).
- **Репликация:** Каждый шард представлен Replica Set (1 primary, 2 secondary).
- **Схема:** `title`, `description`, `category`, `price`, `location.address`, `location.city`, `created_at`,
  `created_by`, `started_at`, `finished_at`.
- **Индексы:**
    - Уникальный по `title`.
    - Составной по `{title, created_by}`.
    - Одиночный по `created_by`.

### 3. Реакции (Apache Cassandra & Redis)

Система лайков и дизлайков реализована на Apache Cassandra с кэшированием в Redis (паттерн **Cache-Aside**).

#### Cassandra (Таблица `event_reactions`)

- `event_id`: ID мероприятия в MongoDB.
- `like_value`: `1` (лайк) или `-1` (дизлайк).
- `created_by`: ID пользователя.
- `created_at`: timestamp в UTC.

#### Кэширование (Redis)

- **Ключ:** `event:{event_title_md5_hash}:reactions` (MD5 хэш от названия мероприятия).
- **Значение:** JSON объект `{"likes": x, "dislikes": y}`.
- **Особенность:** Подсчет реакций идет по **названию** мероприятия (если у одного спектакля несколько сеансов, лайки
  суммируются).

---

## 🚀 Как запустить проект

### Предварительные требования

Для локального запуска вам потребуются установленные:

- [Docker](https://docs.docker.com/get-docker/) и Docker Compose
- Утилита `make`

### 1. Настройка окружения

В корне проекта должен находиться конфигурационный файл `.env.local`.

```env
# Хост, по которому доступно ваше приложение
APP_HOST=0.0.0.0
# Порт, на котором оно слушает запросы
APP_PORT=8080
# Время жизни сессии в секундах
APP_USER_SESSION_TTL=60 # 1 минута (ДЛЯ ТЕСТОВ)
# Время жизни лайков в кэше в секундах.
APP_LIKE_TTL=60

# Redis connection
# Хост Redis сервера
REDIS_HOST=redis
# Порт Redis сервера
REDIS_PORT=6379
# Пароль для Redis (оставьте пустым, если пароль не используется)
REDIS_PASSWORD=
# Номер базы данных Redis (по умолчанию - 0)
REDIS_DB=0

# MongoDB connection
MONGODB_DATABASE=eventhub
# Имя пользователя для аутентификации в MongoDB
MONGODB_USER=eventhub
# Пароль для аутентификации в MongoDB
MONGODB_PASSWORD=eventhub
# Хост MongoDB сервера
MONGODB_HOST=mongos
# Порт MongoDB сервера
MONGODB_PORT=27017
MONGO_SHARD_PORT=27018

# Список хостов Cassandra в виде строки, разделенной запятыми (пока что один хост).
CASSANDRA_HOSTS=cassandra-test
# Порт Cassandra
CASSANDRA_PORT=9042
# Имя пользователя для подключения к Cassandra
CASSANDRA_USERNAME=
# Пароль для подключения к Cassandra
CASSANDRA_PASSWORD=
# Имя ключевого пространства Cassandra
CASSANDRA_KEYSPACE=testkeyspace
# Уровень согласованности Cassandra
CASSANDRA_CONSISTENCY=ONE
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

## 📋 API Эндпоинты

> 💡 Коллекция запросов находится [здесь](api/swagger-api-docs.json)

### Сессии и состояние

- `GET /health` — проверка работоспособности системы.
  Возвращает статус "ok" и обновляет TTL куки X-Session-Id, если она валидна.
- `POST /session` — управление анонимной сессией.
  Создает новую сессию (201) или продлевает существующую (200) без необходимости авторизации.

### Аутентификация

- `POST /auth/login` — вход в систему. Проверяет credentials и привязывает ID пользователя к текущей сессии в Redis.
- `POST /auth/logout` — выход. Удаляет данные сессии из Redis и принудительно очищает куку на клиенте (`Max-Age=0`).

### Пользователи и организаторы

- `POST /users` — регистрация нового пользователя. Создает профиль и автоматически открывает новую авторизованную
  сессию.
- `GET /users` — поиск организаторов. Поддерживает фильтрацию по `name` (частичное совпадение) и `id`, а также пагинацию
  через `limit` и `offset`.
- `GET /users/{id}` — получение публичного профиля организатора (имя, логин) по его идентификатору.
- `GET /users/{id}/events` — список мероприятий, созданных конкретным пользователем. Поддерживает те же фильтры, что и
  общий поиск, включая `include=reactions`.

### События и Реакции

- `POST /events` — создание нового мероприятия. Доступно только авторизованным пользователям.
- `GET /events` — поиск мероприятий с мощной фильтрацией:
    - По подстроке в названии (`title`), категории (`category`), городу (`city`).
    - По диапазону цен (`price_from`, `price_to`) и дат (`date_from`, `date_to`).
    - Опциональное включение счетчиков лайков через `include=reactions`.
- `GET /events/{id}` — детальная информация о конкретном сеансе мероприятия.
- `PATCH /events/{id}` — редактирование мероприятия. Доступно только его создателю. Позволяет менять категорию, цену и
  город (передача пустой строки в `city` удаляет поле из БД).
- `POST /events/{id}/like` — поставить лайк. Данные сохраняются в Cassandra. Если был дизлайк — он перезаписывается.
- `POST /events/{id}/dislike` — поставить дизлайк. Работает аналогично лайку, обновляя статус реакции пользователя.

---

## 📋 API подробно

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

### 3. Регистрация пользователей

`POST /users`

Cоздание пользователя, от лица которого, можно создавать события,
на которые будет подписываться все желающие, в том числе, и анонимные пользователи.

При успешной регистрации создается новая сессия и привязывается к новому пользователю.
**Тело запроса:**

JSON-струтура пользователя и пароль

```json
{
  "full_name": "Джон Доу",
  "username": "j0hnd0e42",
  "password": "svp4_dvp4_str0ng_passw0rd"
}
```

**Ответ (при успешном создании):**

```http
HTTP/1.1 201 Created
Set-Cookie: X-Session-Id=1238a2c1d9e4b7f0a5c6d2e8b1a355dd; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Length: 0
```

**Ответ (если пользователь уже существует):**

```http
HTTP/1.1 409 Conflict
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Type: application/json
Content-Length: 999
{"message": "user already exists"}
```

**Ответ (если данные не валидны):**

```http
HTTP/1.1 400 Bad Request
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Type: application/json
Content-Length: 999
{"message": "invalid \"{field_name}\" field"}
```

> При 400 или 409 ошибках обновляется сессия если она есть.

### 4. Аутентификация пользователя

`POST /auth/login`

**Тело запроса:**

```json
{
  "username": "j0hnd0e42",
  "password": "svp4_dvp4_str0ng_passw0rd"
}
```

- `username` *string* - имя пользователя, по которому происходит аутентификация
- `password` *string* - пароль, по которому происходит аутентификация

**Ответ (при успешной аутентификации):**

```http
HTTP/1.1 204 No Content
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Length: 0
```

**Ответ (если аутентификация не прошла):**

```http
HTTP/1.1 401 Unauthorized
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
{"message": "invalid credentials"}
```

### 5. Выход из аккаунта

`POST /auth/logout`

**Ответ (при успешном выходе):**

```http
HTTP/1.1 204 No Content
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age=0
Content-Length: 0
```

### 6. Создание события

`POST /events`

На событие могут подписываться все желающие, в том числе и не авторизованные пользователи.
Эндпоинт возвращает идентификатор созданного события.
Доступно только для авторизованных пользователей.

**Тело запроса:**

```json
{
  "title": "Мой день рождения",
  "address": "г. Санкт-Петербург, ул. Пушкина, дом Колотушкина",
  "started_at": "2026-04-01T12:00:00+03:00",
  "finished_at": "2026-04-01T23:00:00+03:00",
  "description": "Приглашаю вас отпраздновать мое 30-с-чем-то-летие"
}
```

**Ответ (при успешной создании):**

```http
HTTP/1.1 201 Created
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Length: 999
Content-Type: application/json
{"id": "12e9c0b1a2b3c3d5e6f7a8b7"}
```

**Ответ (если пользователь не авторизован):**

```http
HTTP/1.1 401 Unauthorized
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Length: 0
```

**Ответ (если параметры невалидны):**

```http
HTTP/1.1 400 Bad Request
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Type: application/json
Content-Length: 999
{"message": "invalid \"{field_name}\" field"}
```

**Ответ (если событие уже создано):**

```http
HTTP/1.1 409 Conflict
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Type: application/json
Content-Length: 999
{"message": "event already exists"}
```

### 7. Просмотр событий

`GET /events`

Просмотр событий с возможностью фильтрации и пагинации.

```http
GET /events?title=my_supa_party&limit=10&offset=10 HTTP/1.1
Host:localhost:8080
Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d
```

Параметры фильтрации задаются через **GET-параметры**:

- `title` *string* - название события или подстрока, входящее в название события (по аналоги c `LIKE` в SQL)
- `limit` *uint* - `(>= 0)` максимально количество событий в выборке (участвует в пагинации)
- `offset` *uint* - `(>= 0)` кол-во событий, которое нужно пропустить (участвует в пагинации)

**Ответ (события найдены):**

```http
HTTP/1.1 200 ОК
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Type: application/json
Content-Length: 999
```

```json
{
  "events": [
    {
      "id": "12e9c0b1a2b3c3d5e6f7a8b7",
      "title": "Мой день рождения",
      "description": "Приглашаю вас отпраздновать мое 30-с-чем-то-летие",
      "location": {
        "address": "г. Санкт-Петербург, ул. Пушкина, дом Колотушкина"
      },
      "created_at": "2026-03-14T14:59:32+03:00",
      "created_by": "65e9c0b1a2b3c4d5e6f7a8b9",
      "started_at": "2026-04-01T12:00:00+03:00",
      "finished_at": "2026-04-01T23:00:00+03:00"
    }
  ],
  "count": 1
}
```

- `events` - список всех найденных событий
- `count` - кол-во найденных событий и должно соответствовть размеру списка *events*

**Ответ (события НЕ найдены):**

```http
HTTP/1.1 200 ОК
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Type: application/json
Content-Length: 999
```

```json
{
  "events": [],
  "count": 0
}
```

**Ответ (если параметры невалидны):**

```http
HTTP/1.1 400 Bad Request
Set-Cookie: X-Session-Id=3f8a2c1d9e4b7f0a5c6d2e8b1a3f9c7d; HttpOnly; Path=/; Max-Age={APP_USER_SESSION_TTL}
Content-Length: 999
Content-Type: application/json
{"message": "invalid \"{field_name}\" parameter"}
```
