# Android Integration Tasks

Документ для Android-частини проекту. Тут описано, який функціонал треба реалізувати на Android, які backend endpoint-и вже є, і як це має працювати в застосунку.

## Базове підключення

Backend base URL для Android emulator:

```text
http://10.0.2.2:5116/api
```

Для фізичного телефона:

```text
http://<IP-комп'ютера>:5116/api
```

Для більшості endpoint-ів потрібен JWT:

```http
Authorization: Bearer <token>
```

Backend локально працює через:

```text
ASP.NET Core + PostgreSQL + Redis
```

## 1. Авторизація

Потрібно реалізувати:

- екран реєстрації;
- екран логіну;
- збереження JWT-токена;
- автоматичний вхід, якщо токен уже є;
- logout.

Backend endpoint-и:

```text
POST /api/auth/register
POST /api/auth/login
GET /api/users/me
```

Android має зберігати `token` в encrypted storage.

Для protected endpoint-ів треба передавати:

```http
Authorization: Bearer <token>
```

Також потрібно обробляти:

```text
401 Unauthorized -> очистити token і повернути на login
400 Bad Request -> показати message
409 Conflict -> показати message
404 Not Found -> показати message
```

## 2. Головний екран

Потрібно зробити головний екран зі списком або вкладками джерел:

- Radio;
- Jamendo;
- Audius, якщо будемо використовувати;
- Local music;
- YouTube, якщо потім додамо як external app/player.

Також потрібен загальний пошук або окремий пошук у кожному джерелі.

## 3. Radio

Radio працює через backend + Radio Browser API.

Потрібно реалізувати:

- популярні радіостанції;
- пошук радіо;
- фільтр по країні;
- фільтр по жанру;
- список станцій;
- запуск станції;
- додавання станції в favorites;
- видалення станції з favorites;
- екран улюблених радіостанцій.

Backend endpoint-и:

```text
GET /api/radio/popular?limit=20
GET /api/radio/search?q=rock&limit=20
GET /api/radio/search?q=rock&countryCode=UA&genre=news&limit=20
GET /api/radio/by-country/UA?limit=20
GET /api/radio/by-genre/jazz?limit=20
GET /api/radio/favorites
POST /api/radio/favorites
DELETE /api/radio/favorites/{id}
```

Для програвання брати `streamUrl` і передавати в Media3/ExoPlayer:

```text
MediaItem.fromUri(station.streamUrl)
```

Для додавання radio station в favorites достатньо:

```json
{
  "stationId": "961949f3-0601-11e8-ae97-52543be04c81"
}
```

Backend сам отримає metadata станції і збереже її.

## 4. Jamendo Music

Jamendo - це основне playable джерело online-треків, які можна грати в нашому Media3-плеєрі.

Потрібно реалізувати:

- пошук треків;
- список знайдених треків;
- відображення обкладинки;
- відображення назви;
- відображення артиста;
- відображення альбому;
- відображення тривалості;
- запуск треку;
- додавання треку в favorites;
- додавання треку в playlist.

Backend endpoint-и:

```text
GET /api/jamendo/search?q=electronic&limit=5
GET /api/jamendo/tracks/{id}
GET /api/jamendo/tracks/{id}/stream
```

Для Media3 краще використовувати backend stream endpoint:

```text
http://10.0.2.2:5116/api/jamendo/tracks/{externalId}/stream
```

Для stream endpoint теж потрібен:

```http
Authorization: Bearer <token>
```

Якщо Media3 буде складно передавати JWT header, можна тимчасово використовувати прямий `streamUrl` з Jamendo response.

## 5. Плеєр

Потрібно реалізувати:

- Play / Pause;
- Next / Previous;
- progress bar для треків;
- назву треку або станції;
- артиста або назву радіо;
- обкладинку або logo;
- loading state;
- error state, якщо stream не відкрився;
- mini-player внизу;
- full player screen.

Для Radio:

```text
MediaItem.fromUri(station.streamUrl)
```

Для Jamendo:

```text
MediaItem.fromUri("http://10.0.2.2:5116/api/jamendo/tracks/{externalId}/stream")
```

Для Audius:

```text
MediaItem.fromUri("http://10.0.2.2:5116/api/audius/tracks/{externalId}/stream")
```

## 6. Черга відтворення

Потрібно зробити queue:

- для Jamendo можна запускати список результатів пошуку як чергу;
- Next / Previous переходить по треках;
- для Radio queue може бути список станцій;
- для Local music queue може бути список локальних файлів.

## 7. Playlists

Потрібно реалізувати:

- список плейлистів;
- створення плейлиста;
- відкриття плейлиста;
- додавання треку в плейлист;
- видалення треку з плейлиста;
- видалення плейлиста;
- запуск треку з плейлиста.

Backend endpoint-и:

```text
GET /api/playlists
POST /api/playlists
GET /api/playlists/{id}
POST /api/playlists/{playlistId}/tracks
DELETE /api/playlists/{playlistId}/tracks/{trackId}
DELETE /api/playlists/{id}
```

Body для створення playlist:

```json
{
  "name": "My Playlist"
}
```

Body для додавання Jamendo track у playlist:

```json
{
  "source": "jamendo",
  "externalId": "1270449",
  "title": "dub sequences",
  "artist": "Elektrojudas",
  "artworkUrl": "https://usercontent.jamendo.com?type=album&id=151752&width=300&trackid=1270449",
  "durationMs": 255000
}
```

Підтримувані `source`:

```text
audius
jamendo
soundcloud
youtube
```

## 8. Favorite Tracks

Потрібно реалізувати:

- екран улюблених треків;
- додавання треку в улюблені;
- видалення треку з улюблених;
- запуск улюбленого треку.

Backend endpoint-и:

```text
GET /api/favorites/tracks
POST /api/favorites/tracks
DELETE /api/favorites/tracks/{id}
```

Body такий самий, як для додавання треку в playlist.

## 9. Local Music

Це Android-частина окремо від backend.

Потрібно реалізувати:

- просканувати локальні аудіофайли;
- показати список локальних треків;
- програвати локальні файли через Media3;
- зберігати локальну бібліотеку в Room;
- оновлювати список після зміни файлів;
- обробляти permissions для доступу до media files.

## 10. Offline / Cache

Потрібно:

- локальна музика має працювати офлайн;
- для online sources показувати помилку без інтернету;
- metadata можна кешувати в Room;
- YouTube не кешувати;
- Jamendo/radio audio cache робити тільки якщо команда окремо вирішить, що це потрібно.

Backend уже кешує зовнішні read-only запити в Redis:

```text
Radio search/popular
Radio station metadata
Jamendo search
Jamendo track metadata
Audius search/trending
Audius track metadata
```

## 11. YouTube / YouTube Music
 
Підтримка YouTube реалізована на бекенді через чистий аудіопотік (без реклами, з кешуванням у Redis на 4 години).

Backend endpoint-и:

```text
GET /api/youtube/search?q=rock&limit=20
GET /api/youtube/tracks/{id}
GET /api/youtube/tracks/{id}/stream
```

Для Media3 аудіопотік запускається так само, як Jamendo:

```text
MediaItem.fromUri("http://10.0.2.2:5116/api/youtube/tracks/{externalId}/stream")
```

Особливості:
- Працює фонове відтворення у `MusicPlayerService`;
- Працює еквалайзер `AudioEffectsManager` та Bass Boost;
- Працює додавання в плейлисти та Favorites (`source: "youtube"`, `externalId: videoId`).
- Потік не містить реклами.

## 12. Settings

Потрібно реалізувати:

- logout;
- перемикання теми;
- можливо вибір країни за замовчуванням для radio;
- можливо очистку локального cache;
- можливо показ поточного користувача.

## 13. Audius

Audius на backend є, але працює тільки якщо налаштований `Audius:ApiKey`.

Endpoint-и:

```text
GET /api/audius/search?q=electronic&limit=20
GET /api/audius/trending?limit=20
GET /api/audius/tracks/{id}
GET /api/audius/tracks/{id}/stream
```

Якщо Audius буде в UI, його можна підключати так само, як Jamendo:

- search;
- list;
- play;
- add to playlist;
- add to favorites.

## 14. SoundCloud

SoundCloud-код на backend є, але для реальної роботи потрібні `SoundCloud:ClientId` і `SoundCloud:ClientSecret`. Backend сам отримує та повторно використовує короткоживучий access token.

Endpoint-и:

```text
GET /api/soundcloud/search?q=lofi&limit=20
GET /api/soundcloud/tracks/{id}
GET /api/soundcloud/tracks/{id}/stream
```

Якщо API access немає, SoundCloud краще вважати optional/planned.

## 15. Error / Empty / Loading states

Потрібно зробити нормальні UI states:

- loading для search;
- loading для player;
- empty state, якщо нічого не знайдено;
- error state, якщо API повернув помилку;
- no internet state;
- unauthorized state;
- stream failed state.

## Мінімальний Demo Flow

Спочатку треба зробити мінімальний робочий сценарій:

```text
Login
-> Jamendo search
-> Play track
-> Radio by country
-> Play station
-> Add track to favorites
-> Add radio to favorites
-> Create playlist
-> Add track to playlist
-> Play track from playlist
```

Цього достатньо, щоб показати повний цикл застосунку.

## Що backend уже перевірено

Перевірено локально:

- Docker працює;
- PostgreSQL працює;
- Redis працює;
- `/api/health` повертає healthy;
- register/login працюють;
- JWT працює;
- `/api/users/me` працює;
- playlists працюють;
- radio by country працює;
- Redis cache для radio працює;
- Jamendo search працює;
- Jamendo stream endpoint повертає реальний mp3-файл.

