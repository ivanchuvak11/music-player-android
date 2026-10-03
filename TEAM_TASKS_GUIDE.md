# Документ для команди: Необхідні доопрацювання та точки синхронізації

Цей документ описує зміни, реалізовані в ядрі Android застосунку (`feature/android-core`), а також завдання та вимоги для бекенду (Іван) і дизайну/UI (Богдан).

---

## 1. Що реалізовано в Android Core (Максим)

1. **Захист JWT токена (`SessionManager.kt`)**:
   - Підключено бібліотеку `androidx.security:security-crypto`.
   - Токен зберігається в `EncryptedSharedPreferences` з апаратним шифруванням через Android Keystore (AES256-GCM / MasterKey).
   - Додано плавний fallback на випадок старих або модифікованих пристроїв та автоміграцію зі старого незашифрованого сховища.

2. **Захист бази даних Room (`AppDatabase.kt`)**:
   - `fallbackToDestructiveMigration()` обмежено виключно дебаг-режимом (`ApplicationInfo.FLAG_DEBUGGABLE`).
   - У релізних збірках база захищена від несподіваного видалення користувацьких даних (плейлистів, обраного).

3. **MediaSession & External Access (`MusicPlayerService.kt`)**:
   - Реалізовано перевірку `ControllerInfo` в `onConnect()`.
   - Навушники, Bluetooth гарнітури (AVRCP), системні віджети Android і Android Auto мають безпечний контрольований доступ.

4. **Network Security Config (`network_security_config.xml`)**:
   - Глобальний cleartext HTTP закритий.
   - Створено білий список для стрімінгу інтернет-радіостанцій та локального середовища розробника (`10.0.2.2`, `localhost`).

5. **Єдине джерело URL конфігурації (`ServerConfig.kt`)**:
   - Усі посилання на стріми (Jamendo, Audius, SoundCloud) винесені в єдиний конфігуратор `ServerConfig`, усунуто хардкод в `AudioModel` та `NetworkClient`.

6. **Локалізація та кодування (`strings.xml`)**:
   - Базові UI-тексти та статуси переведені в ресурси рядків `res/values/strings.xml` у чистому форматі UTF-8.

7. **Мінімальна затримка старту треків (Instant Playback & Fast Buffering)**:
   - В `YouTubeExtractorService`: додано метод `getCachedStreamUrl()`, який миттєво повертає вже розпарсений URL стріму з кешу без блокування потоку.
   - В `AudioModel.toMediaItem()`: пряма підстановка розпарсеного CDN-посилання в `MediaItem`, що дозволяє ExoPlayer починати завантаження відразу, оминаючи синхронне перехоплення в `ResolvingDataSource`.
   - В `MusicPlayerService`: оптимізовано конфігурацію `DefaultLoadControl` — поріг `bufferForPlaybackMs` зменшено з 500мс до **250мс** (аудіо починає грати вже з першого отриманого чанка), а `bufferForPlaybackAfterRebufferMs` — до **500мс**.

8. **Архітектура Категорій, Жанрів, Виконавців та Готових Плейлистів (`CategoryModel.kt`, `CuratedMusicRepository.kt`)**:
   - `MusicGenre`: моделі жанрів з градієнтами та швидкими запитами ("Українська музика", "Поп", "Рок", "Чіл & Лоу-фай", "Хіп-хоп", "Електроніка", "Тренування", "Релакс & Сон", "Джаз").
   - `CuratedPlaylist`: готові тематичні добірки ("Топ Чарти України", "Chill & Lofi Beats", "Drive & Heavy Rock", "Gym Beast Mode", "Акустичний Затишок", "Retro Synthwave 80s").
   - `ArtistInfo`: агрегація виконавців з підрахунком треків та миттєвим формуванням черги.
   - `MainPlayerViewModel`: відкриті `StateFlow` (`musicGenres`, `curatedPlaylists`, `artists`) та методи запуску `playCuratedPlaylist()`, `playGenre()`, `playArtistTracks()`.

---

## 2. Що потрібно доопрацювати Бекенду (Іван)

### Пріоритет 1: Refresh Token для безпечної сесії [РЕАЛІЗОВАНО ✅]
* **Статус:** Виконано. Додано сутність `RefreshToken`, міграцію БД, ендпоінти `POST /api/auth/refresh` та `POST /api/auth/revoke`.
* **Що реалізовано:**
  - При реєстрації та вході (`/api/auth/login`) повертається пара: `accessToken` (термін 2 години) та довгоживучий `refreshToken` (30 днів) з ротацією токенів.
  - Реалізовано `POST /api/auth/refresh` для прозорого оновлення сесії через `OkHttp Authenticator`.
  - Реалізовано `POST /api/auth/revoke` для безпечного виходу (logout).

### Пріоритет 2: Публічний пошук треків без авторизації [РЕАЛІЗОВАНО ✅]
* **Статус:** Виконано. Додано атрибут `[AllowAnonymous]` на всі ендпоінти пошуку та стрімінгу публічних джерел (Jamendo, Audius, SoundCloud, Radio Browser та YouTube).
* **Що тепер доступно без авторизації:**
  - `GET /api/jamendo/search`, `GET /api/jamendo/tracks/{id}`, `GET /api/jamendo/tracks/{id}/stream`
  - `GET /api/youtube/search`, `GET /api/youtube/tracks/{id}`, `GET /api/youtube/tracks/{id}/stream`
  - `GET /api/audius/search`, `GET /api/audius/trending`, `GET /api/audius/tracks/{id}`, `GET /api/audius/tracks/{id}/stream`
  - `GET /api/radio/search`, `GET /api/radio/popular`, `GET /api/radio/by-country/{countryCode}`, `GET /api/radio/by-genre/{genre}`
  - `GET /api/soundcloud/search`, `GET /api/soundcloud/tracks/{id}`, `GET /api/soundcloud/tracks/{id}/stream`
* **Що залишається під захистом JWT (`[Authorize]`):**
  - Плейлисти користувача (`/api/playlists/**`)
  - Улюблені треки (`/api/favorites/**`)
  - Улюблені радіостанції (`/api/radio/favorites/**`)
  - Профіль поточного користувача (`/api/users/me`)

### Пріоритет 3: Повернення прямого посилання на стрім (Direct Stream URL) [РЕАЛІЗОВАНО ✅]
* **Статус:** Виконано.
* **Що зроблено:**
  - Усі DTO (Jamendo, Audius, YouTube, SoundCloud, Radio) повертають валідне поле `streamUrl`.
  - В `AudiusTrackDto` додано поле `streamUrl` (`/api/audius/tracks/{id}/stream`).
  - У Jamendo повертається пряме аудіопосилання з Jamendo CDN.

### Пріоритет 4: Пошук та віддача обкладинок альбомів (Album Artwork) [РЕАЛІЗОВАНО ✅]
* **Статус:** Виконано.
* **Що реалізовано:**
  - Створено `CoverService` з інтеграцією iTunes Search API (висока якість 600x600) та автоматичним фолбеком на Deezer API.
  - Додано `GET /api/covers/search?artist={artist}&title={title}&q={query}` (повертає метадані з посиланням на обкладинку).
  - Додано `GET /api/covers/image?artist={artist}&title={title}&q={query}` (прямий 302 Redirect на високоякісне зображення для Coil/Glide/AsyncImage в Jetpack Compose).
  - Результати кешуються на 7 днів.

### Пріоритет 5: Динамічні готові плейлисти та топ виконавців [В ЧЕРЗІ 📋]
* **Що потрібно реалізувати на бекенді:**
  - `GET /api/curated/playlists` — список актуальних готових плейлистів (назва, опис, обкладинка, список треків з YouTube/Audius).
  - `GET /api/curated/genres` — список актуальних музичних категорій та тегів.
  - `GET /api/artists/top` — список популярних виконавців тижня для головного екрана.

---

## 3. Що потрібно доопрацювати Дизайну / UI (Богдан)

### Пріоритет 1: Відображення Категорій, Виконавців та Готових Плейлистів (UI) [НОВЕ 🎨]
* **Опис:** Вся логіка даних та ViewModel вже підготовлені Максимом в ядрі (`MainPlayerViewModel`). Потрібно лише оформити красивий UI/Compose шар.
* **Що зробити:**
  - **Карусель/Сітка Категорій (Жанрів)**:
    - Використовувати `viewModel.musicGenres`.
    - Зробити картки з красивими градієнтами `genre.gradientColors`, емодзі `genre.iconEmoji` та назвою `genre.name`.
    - При кліку викликати `viewModel.playGenre(genre)`.
  - **Секція «Готові Плейлисти» (Curated Playlists)**:
    - Підписатися на `viewModel.curatedPlaylists.collectAsState()`.
    - Горизонтальний скрол великих карток із закругленими кутами (Card/AsyncImage з обкладинкою `playlist.coverUrl`, заголовок і опис).
    - При кліку викликати `viewModel.playCuratedPlaylist(playlist)`.
  - **Вкладка або блок «Виконавці» (Artists)**:
    - Підписатися на `viewModel.artists.collectAsState()`.
    - Круглі аватари виконавців (CircleShape) із назвою та кількістю доступних треків `artist.trackCount`.
    - При натисканні розкривати список треків виконавця або викликати `viewModel.playArtistTracks(artist)`.

### Пріоритет 2: Використання ресурсів рядків замість хардкоду
* **Проблема:** У файлах екранів Compose рядки інтерфейсу прописані напряму кирилицею в лапках, що створює ризик спотворення кодування на різних ОС.
* **Що зробити:**
  - Використовувати `stringResource(R.string.btn_play)`, `stringResource(R.string.search_placeholder)` тощо.
  - Поповнювати `res/values/strings.xml`.

### Пріоритет 3: Розділення `MainActivity.kt` на окремі Composable файли
* **Проблема:** `MainActivity.kt` містить понад 2100 рядків коду, де змішані стан плеєра, діалоги еквалайзера, списки треків та авторизація.
* **Що зробити:**
  - Створити окрему папку `ui/screens/` або `ui/components/`:
    - `NowPlayingSheet.kt` (екран поточного треку та прогрес-бар)
    - `EqualizerDialog.kt` (панель еквалайзера та пресетів)
    - `PlaylistsScreen.kt` (створення та перегляд плейлистів)
    - `AuthDialog.kt` (вхід/реєстрація)

---

## 4. Статус збірки та тестів
- Всі Unit-тести пройдені успішно (`BUILD SUCCESSFUL`).
- `gradlew.bat` стабілізовано з коректним кодом повернення.
- Збірка Release APK валідується без помилок ProGuard/R8.
