# Music Player Android

Android music player supporting multiple music sources.

## Sources

- Audius
- YouTube / YouTube Music
- Internet Radio
- Local Music

## Tech Stack

### Android
Kotlin, Jetpack Compose, Media3, Retrofit, Room

### Backend
ASP.NET Core 10, PostgreSQL, Entity Framework Core, JWT

## Project Structure

- `android/` - Android application
- `backend/` - ASP.NET Core backend
- `docs/` - project documentation

## Backend Local Setup

Start PostgreSQL and Redis:

```powershell
docker compose up -d
```

If your Docker installation does not expose the `docker compose` plugin, use:

```powershell
docker-compose up -d
```

Create a local backend config from the example if it does not exist yet:

```powershell
Copy-Item backend/MusicPlayer.Api/appsettings.Development.example.json backend/MusicPlayer.Api/appsettings.Development.json
```

Apply Entity Framework migrations:

```powershell
dotnet ef database update --project backend/MusicPlayer.Api
```

Run the API:

```powershell
dotnet run --project backend/MusicPlayer.Api
```

Local API URLs:

- API base URL: `http://localhost:5116/api`
- Health check: `http://localhost:5116/api/health`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`

For Audius endpoints, set `Audius:ApiKey` in `backend/MusicPlayer.Api/appsettings.Development.json`.
For SoundCloud endpoints, set `SoundCloud:AccessToken` or `SoundCloud:ClientId` in `backend/MusicPlayer.Api/appsettings.Development.json`.
For Jamendo endpoints, set `Jamendo:ClientId` in `backend/MusicPlayer.Api/appsettings.Development.json`.

Redis is used by the backend to cache read-only Audius and Radio Browser responses during local development.

Radio endpoints for Android:

- `GET /api/radio/popular?limit=20`
- `GET /api/radio/search?q=rock&countryCode=UA&genre=news&limit=20`
- `GET /api/radio/by-country/UA?limit=20`
- `GET /api/radio/by-genre/jazz?limit=20`

The Android app should pass the returned `streamUrl` to Media3/ExoPlayer for playback.

SoundCloud endpoints:

- `GET /api/soundcloud/search?q=lofi&limit=20`
- `GET /api/soundcloud/tracks/{id}`
- `GET /api/soundcloud/tracks/{id}/stream`

SoundCloud results include attribution fields. The Android UI should show the uploader, SoundCloud as the source, and a link to the original `soundCloudUrl`.

Jamendo endpoints:

- `GET /api/jamendo/search?q=rock&limit=20`
- `GET /api/jamendo/tracks/{id}`
- `GET /api/jamendo/tracks/{id}/stream`

Jamendo results include a direct `streamUrl` and can be played by Media3/ExoPlayer.

## Team

- Ivan - Backend & Data
- Maksym - Android Core & Audio Engine
- Bohdan - Android UI, Design & QA
