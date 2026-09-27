# Music Player Architecture

## Stack

### Android
- Kotlin
- Jetpack Compose
- Material 3
- Media3 / ExoPlayer
- Retrofit
- Room
- Coroutines / StateFlow

### Backend
- ASP.NET Core 10
- Entity Framework Core
- PostgreSQL
- Redis
- JWT Authentication

## Backend Cache

Redis використовується для кешування read-only запитів до зовнішніх джерел:

- Audius search/trending: 5 хвилин
- Audius track metadata: 30 хвилин
- SoundCloud search: 5 хвилин
- SoundCloud track metadata: 30 хвилин
- Jamendo search: 10 хвилин
- Jamendo track metadata: 1 година
- Radio Browser search/popular: 10 хвилин
- Radio Browser station metadata: 24 години

## Music Sources

- Audius
- YouTube / YouTube Music
- SoundCloud
- Jamendo
- Internet Radio
- Local Music

## Team

### Ivan
Backend & Data

### Maksym
Android Core & Audio Engine

### Bohdan
Android UI, Design & QA
