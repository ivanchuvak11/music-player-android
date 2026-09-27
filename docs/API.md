# Music Player REST API

REST API бекенду для Android-застосунку **Music Player**.

## Базова URL-адреса

Під час локальної розробки:

```text
http://localhost:5116/api
```

Більшість endpoint'ів потребують JWT-авторизації.

Для захищених запитів необхідно передавати токен у заголовку:

```http
Authorization: Bearer <token>
```

---

# Авторизація

## Реєстрація

Створює новий обліковий запис користувача.

```http
POST /api/auth/register
```

### Запит

```json
{
  "username": "ivan",
  "email": "ivan@example.com",
  "password": "Test12345"
}
```

### Відповідь

```json
{
  "id": 1,
  "username": "ivan",
  "email": "ivan@example.com",
  "createdAt": "2026-09-26T10:00:00Z"
}
```

---

## Вхід

Виконує авторизацію користувача та повертає JWT-токен.

```http
POST /api/auth/login
```

### Запит

```json
{
  "email": "ivan@example.com",
  "password": "Test12345"
}
```

### Відповідь

```json
{
  "token": "<jwt-token>",
  "userId": 1,
  "username": "ivan",
  "email": "ivan@example.com"
}
```

Android-застосунок повинен використовувати отриманий токен для всіх захищених запитів до API.

---

# Користувач

## Отримання поточного користувача

Повертає інформацію про авторизованого користувача.

```http
GET /api/users/me
```

Потрібна авторизація.

### Відповідь

```json
{
  "id": 1,
  "username": "ivan",
  "email": "ivan@example.com",
  "createdAt": "2026-09-26T10:00:00Z"
}
```

---

# Audius

Audius використовується як основне зовнішнє джерело музичних треків.

Усі Audius endpoint'и потребують JWT-авторизації.

## Пошук треків

Виконує пошук музичних треків через Audius.

```http
GET /api/audius/search?q={query}&limit={limit}
```

### Приклад

```http
GET /api/audius/search?q=electronic&limit=10
```

### Відповідь

```json
[
  {
    "source": "audius",
    "externalId": "abkvg",
    "title": "Electronic Butterflies",
    "artist": "Seb Park",
    "artworkUrl": "https://example.com/cover.jpg",
    "durationMs": 117000
  }
]
```

---

## Популярні треки

Повертає список популярних треків Audius.

```http
GET /api/audius/trending?limit={limit}
```

### Приклад

```http
GET /api/audius/trending?limit=10
```

### Відповідь

```json
[
  {
    "source": "audius",
    "externalId": "track-id",
    "title": "Example Track",
    "artist": "Example Artist",
    "artworkUrl": "https://example.com/cover.jpg",
    "durationMs": 215000
  }
]
```

---

## Отримання інформації про трек

Повертає інформацію про конкретний трек Audius.

```http
GET /api/audius/tracks/{id}
```

### Приклад

```http
GET /api/audius/tracks/abkvg
```

### Відповідь

```json
{
  "source": "audius",
  "externalId": "abkvg",
  "title": "Electronic Butterflies",
  "artist": "Seb Park",
  "artworkUrl": "https://example.com/cover.jpg",
  "durationMs": 117000
}
```

---

## Відтворення треку

Повертає аудіопотік треку Audius через бекенд.

```http
GET /api/audius/tracks/{id}/stream
```

### Приклад

```http
GET /api/audius/tracks/abkvg/stream
```

У разі успішного запиту бекенд повертає аудіопотік треку.

Android-застосунок може використовувати цей endpoint як джерело аудіо для Media3/ExoPlayer.

---

# Jamendo

Jamendo використовується як додаткове зовнішнє джерело незалежної музики з прямими audio URL.

Усі Jamendo endpoint'и потребують JWT-авторизації.

Для роботи потрібен `Jamendo:ClientId` у конфігурації backend.

## Пошук треків Jamendo

```http
GET /api/jamendo/search?q={query}&limit={limit}
```

### Приклад

```http
GET /api/jamendo/search?q=rock&limit=10
```

### Відповідь

```json
[
  {
    "source": "jamendo",
    "externalId": "1848357",
    "title": "Example Track",
    "artist": "Example Artist",
    "artworkUrl": "https://usercontent.jamendo.com/example.jpg",
    "durationMs": 180000,
    "album": "Example Album",
    "licenseUrl": "https://creativecommons.org/licenses/by-nc-nd/3.0/",
    "jamendoUrl": "https://www.jamendo.com/track/1848357",
    "streamUrl": "https://prod-1.storage.jamendo.com/?trackid=1848357&format=mp32"
  }
]
```

---

## Отримання інформації про Jamendo трек

```http
GET /api/jamendo/tracks/{id}
```

### Приклад

```http
GET /api/jamendo/tracks/1848357
```

---

## Відтворення Jamendo треку

```http
GET /api/jamendo/tracks/{id}/stream
```

### Приклад

```http
GET /api/jamendo/tracks/1848357/stream
```

Android-застосунок може використовувати цей endpoint або прямий `streamUrl` як джерело аудіо для Media3/ExoPlayer.

---

# SoundCloud

SoundCloud використовується як додаткове зовнішнє джерело музичних треків.

Усі SoundCloud endpoint'и потребують JWT-авторизації.

Для роботи потрібен `SoundCloud:AccessToken` або `SoundCloud:ClientId` у конфігурації backend.

Backend повертає тільки треки з доступом `playable`, які можна відтворювати поза SoundCloud. Android UI повинен показувати автора, SoundCloud як джерело та посилання `soundCloudUrl`.

## Пошук треків SoundCloud

```http
GET /api/soundcloud/search?q={query}&limit={limit}
```

### Приклад

```http
GET /api/soundcloud/search?q=lofi&limit=10
```

### Відповідь

```json
[
  {
    "source": "soundcloud",
    "externalId": "123456789",
    "title": "Example Track",
    "artist": "Example Artist",
    "artworkUrl": "https://example.com/artwork.jpg",
    "durationMs": 180000,
    "genre": "Lo-Fi",
    "soundCloudUrl": "https://soundcloud.com/example/track",
    "streamUrl": "/api/soundcloud/tracks/123456789/stream"
  }
]
```

---

## Отримання інформації про SoundCloud трек

```http
GET /api/soundcloud/tracks/{id}
```

### Приклад

```http
GET /api/soundcloud/tracks/123456789
```

---

## Відтворення SoundCloud треку

```http
GET /api/soundcloud/tracks/{id}/stream
```

### Приклад

```http
GET /api/soundcloud/tracks/123456789/stream
```

Android-застосунок може використовувати цей endpoint як джерело аудіо для Media3/ExoPlayer.

---

# Плейлисти

## Отримання плейлистів користувача

```http
GET /api/playlists
```

Потрібна авторизація.

### Відповідь

```json
[
  {
    "id": 1,
    "name": "My Favorites",
    "createdAt": "2026-09-26T10:00:00Z",
    "trackCount": 2
  }
]
```

---

## Отримання плейлиста

```http
GET /api/playlists/{id}
```

Потрібна авторизація.

### Приклад

```http
GET /api/playlists/1
```

### Відповідь

```json
{
  "id": 1,
  "name": "My Favorites",
  "createdAt": "2026-09-26T10:00:00Z",
  "tracks": [
    {
      "id": 2,
      "source": "audius",
      "externalId": "abkvg",
      "title": "Electronic Butterflies",
      "artist": "Seb Park",
      "artworkUrl": "https://example.com/cover.jpg",
      "durationMs": 117000,
      "addedAt": "2026-09-26T10:05:00Z"
    }
  ]
}
```

---

## Створення плейлиста

```http
POST /api/playlists
```

Потрібна авторизація.

### Запит

```json
{
  "name": "My Favorites"
}
```

### Відповідь

```json
{
  "id": 1,
  "name": "My Favorites",
  "createdAt": "2026-09-26T10:00:00Z"
}
```

---

## Додавання треку до плейлиста

```http
POST /api/playlists/{playlistId}/tracks
```

Потрібна авторизація.

Підтримувані джерела треків:

```text
audius
youtube
soundcloud
jamendo
```

### Запит

```json
{
  "source": "audius",
  "externalId": "abkvg",
  "title": "Electronic Butterflies",
  "artist": "Seb Park",
  "artworkUrl": "https://example.com/cover.jpg",
  "durationMs": 117000
}
```

### Відповідь

```json
{
  "id": 2,
  "source": "audius",
  "externalId": "abkvg",
  "title": "Electronic Butterflies",
  "artist": "Seb Park",
  "artworkUrl": "https://example.com/cover.jpg",
  "durationMs": 117000,
  "addedAt": "2026-09-26T10:05:00Z"
}
```

Один і той самий трек не може бути двічі доданий до одного плейлиста.

---

## Видалення треку з плейлиста

```http
DELETE /api/playlists/{playlistId}/tracks/{trackId}
```

Потрібна авторизація.

У разі успішного видалення:

```text
204 No Content
```

---

## Видалення плейлиста

```http
DELETE /api/playlists/{id}
```

Потрібна авторизація.

У разі успішного видалення:

```text
204 No Content
```

---

# Улюблені треки

Улюблені треки можуть посилатися на такі джерела:

```text
audius
youtube
soundcloud
jamendo
```

## Отримання улюблених треків

```http
GET /api/favorites/tracks
```

Потрібна авторизація.

### Відповідь

```json
[
  {
    "id": 2,
    "source": "audius",
    "externalId": "abkvg",
    "title": "Electronic Butterflies",
    "artist": "Seb Park",
    "artworkUrl": "https://example.com/cover.jpg",
    "durationMs": 117000,
    "createdAt": "2026-09-26T10:05:00Z"
  }
]
```

---

## Додавання треку до улюблених

```http
POST /api/favorites/tracks
```

Потрібна авторизація.

### Запит

```json
{
  "source": "audius",
  "externalId": "abkvg",
  "title": "Electronic Butterflies",
  "artist": "Seb Park",
  "artworkUrl": "https://example.com/cover.jpg",
  "durationMs": 117000
}
```

Один і той самий трек не може бути доданий до улюблених одного користувача двічі.

---

## Видалення треку з улюблених

```http
DELETE /api/favorites/tracks/{id}
```

Потрібна авторизація.

У разі успішного видалення:

```text
204 No Content
```

---

# Радіо

Дані про інтернет-радіостанції отримуються через Radio Browser API.

Радіостанції обробляються окремо від звичайних музичних треків.

## Пошук радіостанцій

```http
GET /api/radio/search?q={query}&countryCode={countryCode}&genre={genre}&limit={limit}
```

Потрібна авторизація.

Можна передавати один або кілька фільтрів:

| Параметр | Опис |
|---|---|
| `q` | Пошук за назвою станції |
| `countryCode` | ISO-код країни, наприклад `UA`, `US`, `FR` |
| `genre` | Жанр або тег, наприклад `rock`, `jazz`, `news` |
| `limit` | Кількість станцій, від 1 до 50 |

### Приклад

```http
GET /api/radio/search?q=rock&limit=10
```

```http
GET /api/radio/search?countryCode=UA&genre=news&limit=10
```

### Відповідь

```json
[
  {
    "stationId": "01b61e49-18bd-486d-b0e1-cb51cbaf9a6d",
    "name": "Skyrock",
    "streamUrl": "http://example-stream-url",
    "logoUrl": "https://example.com/logo.png",
    "country": "France",
    "countryCode": "FR",
    "genre": "rap",
    "codec": "MP3",
    "bitrate": 128
  }
]
```

---

## Отримання радіостанцій за країною

```http
GET /api/radio/by-country/{countryCode}?limit={limit}
```

Потрібна авторизація.

### Приклад

```http
GET /api/radio/by-country/UA?limit=20
```

Повертає радіостанції з конкретної країни. Android-застосунок може використати `streamUrl` як джерело потоку для Media3/ExoPlayer.

---

## Отримання радіостанцій за жанром

```http
GET /api/radio/by-genre/{genre}?limit={limit}
```

Потрібна авторизація.

### Приклад

```http
GET /api/radio/by-genre/jazz?limit=20
```

Повертає радіостанції за жанром або тегом. Android-застосунок може використати `streamUrl` як джерело потоку для Media3/ExoPlayer.

---

## Отримання популярних радіостанцій

```http
GET /api/radio/popular?limit={limit}
```

Потрібна авторизація.

### Приклад

```http
GET /api/radio/popular?limit=10
```

Повертає список популярних радіостанцій на основі даних Radio Browser.

---

## Отримання улюблених радіостанцій

```http
GET /api/radio/favorites
```

Потрібна авторизація.

---

## Додавання радіостанції до улюблених

```http
POST /api/radio/favorites
```

Потрібна авторизація.

Для додавання достатньо передати UUID станції Radio Browser.

### Запит

```json
{
  "stationId": "01b61e49-18bd-486d-b0e1-cb51cbaf9a6d"
}
```

Бекенд самостійно отримує актуальні метадані станції через Radio Browser перед збереженням у базу даних.

---

## Видалення радіостанції з улюблених

```http
DELETE /api/radio/favorites/{id}
```

Потрібна авторизація.

У разі успішного видалення:

```text
204 No Content
```

---

# Джерела музики

Бекенд використовує такі ідентифікатори зовнішніх джерел музики:

| Джерело | Значення | Стан |
|---|---|---|
| Audius | `audius` | Реалізовано |
| SoundCloud | `soundcloud` | Реалізовано |
| Jamendo | `jamendo` | Реалізовано |
| YouTube | `youtube` | Заплановано |

## Audius

Audius використовується для:

- пошуку музики;
- отримання популярних треків;
- отримання метаданих треку;
- отримання аудіопотоку;
- збереження треків у плейлистах;
- збереження треків в улюблених.

## YouTube

Інтеграція YouTube планується окремо.

Для YouTube буде використовуватися офіційний механізм відтворення YouTube. YouTube не розглядається як пряме джерело аудіопотоку для Media3.

## SoundCloud

SoundCloud використовується для:

- пошуку playable треків;
- отримання метаданих треку;
- отримання аудіопотоку через backend proxy endpoint;
- збереження треків у плейлистах;
- збереження треків в улюблених.

## Jamendo

Jamendo використовується для:

- пошуку незалежної музики;
- отримання метаданих треку;
- отримання аудіопотоку;
- збереження треків у плейлистах;
- збереження треків в улюблених.

## Локальна музика

Локальні музичні файли обробляються безпосередньо Android-застосунком.

Для роботи з локальною музикою планується використання Android MediaStore та локальної бази Room.

Самі локальні аудіофайли не зберігаються у PostgreSQL на бекенді.

## Радіо

Радіостанції обробляються окремо через:

```text
/api/radio
```

---

# HTTP-коди відповідей

| Код | Значення |
|---|---|
| `200 OK` | Запит успішно виконано |
| `201 Created` | Ресурс успішно створено |
| `204 No Content` | Операцію виконано, тіло відповіді відсутнє |
| `400 Bad Request` | Некоректний запит |
| `401 Unauthorized` | JWT-токен відсутній або недійсний |
| `404 Not Found` | Ресурс не знайдено |
| `409 Conflict` | Ресурс уже існує або виник конфлікт |

---

# Технології бекенду

На поточному етапі використовуються:

- ASP.NET Core 10;
- PostgreSQL;
- Redis;
- Entity Framework Core;
- JWT Authentication;
- Audius API;
- Radio Browser API.

## Кешування

Redis використовується для кешування read-only запитів до зовнішніх музичних джерел:

| Дані | TTL |
|---|---|
| Audius search | 5 хвилин |
| Audius trending | 5 хвилин |
| Audius track metadata | 30 хвилин |
| SoundCloud search | 5 хвилин |
| SoundCloud track metadata | 30 хвилин |
| Jamendo search | 10 хвилин |
| Jamendo track metadata | 1 година |
| Radio Browser search | 10 хвилин |
| Radio Browser popular | 10 хвилин |
| Radio Browser station metadata | 24 години |

Аудіопотоки не кешуються на бекенді.

---

# Заплановані інтеграції

Наступні можливості ще не є частиною завершеного API:

- інтеграція YouTube;
- додаткове покращення потокового відтворення;
- підтримка Android-клієнта через Retrofit та Media3/ExoPlayer.
