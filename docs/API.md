# Music Player REST API

Backend REST API for the Music Player Android application.

## Base URL

Development:

```text
http://localhost:5116/api
```

Most endpoints require JWT authentication.

For protected endpoints send:

```http
Authorization: Bearer <token>
```

---

# Authentication

## Register

Creates a new user account.

```http
POST /api/auth/register
```

### Request

```json
{
  "username": "ivan",
  "email": "ivan@example.com",
  "password": "Test12345"
}
```

### Response

```json
{
  "id": 1,
  "username": "ivan",
  "email": "ivan@example.com",
  "createdAt": "2026-09-26T10:00:00Z"
}
```

---

## Login

Authenticates a user and returns a JWT token.

```http
POST /api/auth/login
```

### Request

```json
{
  "email": "ivan@example.com",
  "password": "Test12345"
}
```

### Response

```json
{
  "token": "<jwt-token>",
  "userId": 1,
  "username": "ivan",
  "email": "ivan@example.com"
}
```

The Android application should use the returned token for protected API requests.

---

# User

## Get Current User

Returns the currently authenticated user.

```http
GET /api/users/me
```

Authentication required.

### Response

```json
{
  "id": 1,
  "username": "ivan",
  "email": "ivan@example.com",
  "createdAt": "2026-09-26T10:00:00Z"
}
```

---

# Playlists

## Get User Playlists

```http
GET /api/playlists
```

Authentication required.

### Response

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

## Get Playlist

```http
GET /api/playlists/{id}
```

Authentication required.

### Example

```http
GET /api/playlists/1
```

### Response

```json
{
  "id": 1,
  "name": "My Favorites",
  "createdAt": "2026-09-26T10:00:00Z",
  "tracks": [
    {
      "id": 1,
      "source": "soundcloud",
      "externalId": "123456",
      "title": "Example Track",
      "artist": "Example Artist",
      "artworkUrl": "https://example.com/cover.jpg",
      "durationMs": 215000,
      "addedAt": "2026-09-26T10:05:00Z"
    }
  ]
}
```

---

## Create Playlist

```http
POST /api/playlists
```

Authentication required.

### Request

```json
{
  "name": "My Favorites"
}
```

---

## Add Track to Playlist

```http
POST /api/playlists/{playlistId}/tracks
```

Authentication required.

Supported track sources:

```text
soundcloud
youtube
```

### Request

```json
{
  "source": "soundcloud",
  "externalId": "123456",
  "title": "Example Track",
  "artist": "Example Artist",
  "artworkUrl": "https://example.com/cover.jpg",
  "durationMs": 215000
}
```

---

## Remove Track from Playlist

```http
DELETE /api/playlists/{playlistId}/tracks/{trackId}
```

Authentication required.

Successful response:

```text
204 No Content
```

---

## Delete Playlist

```http
DELETE /api/playlists/{id}
```

Authentication required.

Successful response:

```text
204 No Content
```

---

# Favorite Tracks

Favorite tracks can currently reference:

```text
soundcloud
youtube
```

## Get Favorite Tracks

```http
GET /api/favorites/tracks
```

Authentication required.

---

## Add Favorite Track

```http
POST /api/favorites/tracks
```

Authentication required.

### Request

```json
{
  "source": "youtube",
  "externalId": "video-id",
  "title": "Example Track",
  "artist": "Example Artist",
  "artworkUrl": "https://example.com/cover.jpg",
  "durationMs": 180000
}
```

---

## Remove Favorite Track

```http
DELETE /api/favorites/tracks/{id}
```

Authentication required.

Successful response:

```text
204 No Content
```

---

# Radio

Radio station data is provided by Radio Browser.

## Search Radio Stations

```http
GET /api/radio/search?q={query}&limit={limit}
```

Authentication required.

### Example

```http
GET /api/radio/search?q=rock&limit=10
```

### Response

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

## Get Popular Radio Stations

```http
GET /api/radio/popular?limit={limit}
```

Authentication required.

### Example

```http
GET /api/radio/popular?limit=10
```

Returns popular radio stations ordered using Radio Browser popularity data.

---

## Get Favorite Radio Stations

```http
GET /api/radio/favorites
```

Authentication required.

---

## Add Favorite Radio Station

```http
POST /api/radio/favorites
```

Authentication required.

Only the Radio Browser station UUID is required.

### Request

```json
{
  "stationId": "01b61e49-18bd-486d-b0e1-cb51cbaf9a6d"
}
```

The backend retrieves the current station metadata from Radio Browser before storing the favorite.

---

## Remove Favorite Radio Station

```http
DELETE /api/radio/favorites/{id}
```

Authentication required.

Successful response:

```text
204 No Content
```

---

# Track Sources

The backend currently supports the following remote track source identifiers:

| Source | Value |
|---|---|
| SoundCloud | `soundcloud` |
| YouTube / YouTube Music | `youtube` |

Radio stations are handled separately through the `/api/radio` endpoints.

Local music files are handled directly by the Android application and are not stored in the backend music library.

---

# HTTP Status Codes

| Status | Meaning |
|---|---|
| `200 OK` | Request completed successfully |
| `201 Created` | Resource created successfully |
| `204 No Content` | Resource deleted successfully |
| `400 Bad Request` | Invalid request |
| `401 Unauthorized` | Missing or invalid authentication |
| `404 Not Found` | Resource was not found |
| `409 Conflict` | Resource already exists |

---

# Backend Stack

- ASP.NET Core 10
- PostgreSQL
- Entity Framework Core
- JWT authentication
- Radio Browser API

## Planned Integrations

- SoundCloud
- YouTube / YouTube Music