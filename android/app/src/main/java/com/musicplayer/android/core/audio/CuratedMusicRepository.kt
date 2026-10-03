package com.musicplayer.android.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository providing pre-configured music categories, curated ready-made playlists,
 * and artist aggregations for Android Core.
 */
object CuratedMusicRepository {

    /**
     * Standard curated genres / mood categories with search queries and colors.
     */
    val predefinedGenres: List<MusicGenre> = listOf(
        MusicGenre(
            id = "ua_hits",
            name = "Українська музика",
            searchQuery = "українські хіти топ",
            iconEmoji = "🇺🇦",
            gradientColors = listOf(0xFF0D47A1, 0xFFFBC02D)
        ),
        MusicGenre(
            id = "pop",
            name = "Поп-музика",
            searchQuery = "pop music hits",
            iconEmoji = "✨",
            gradientColors = listOf(0xFF8E24AA, 0xFFFF4081)
        ),
        MusicGenre(
            id = "rock",
            name = "Рок & Альтернатива",
            searchQuery = "rock hits classics",
            iconEmoji = "🎸",
            gradientColors = listOf(0xFFBF360C, 0xFFFF7043)
        ),
        MusicGenre(
            id = "hiphop",
            name = "Хіп-хоп / Реп",
            searchQuery = "hip hop rap",
            iconEmoji = "🎤",
            gradientColors = listOf(0xFF263238, 0xFF37474F)
        ),
        MusicGenre(
            id = "lofi",
            name = "Чіл & Лоу-фай",
            searchQuery = "lofi hip hop chill beats",
            iconEmoji = "☕",
            gradientColors = listOf(0xFF4A148C, 0xFF7B1FA2)
        ),
        MusicGenre(
            id = "edm",
            name = "Електроніка / Dance",
            searchQuery = "electronic edm dance club",
            iconEmoji = "⚡",
            gradientColors = listOf(0xFF006064, 0xFF00E5FF)
        ),
        MusicGenre(
            id = "workout",
            name = "Тренування / Енергія",
            searchQuery = "workout motivation bass boosted",
            iconEmoji = "🔥",
            gradientColors = listOf(0xFFE65100, 0xFFFF9800)
        ),
        MusicGenre(
            id = "relax",
            name = "Релакс & Сон",
            searchQuery = "ambient relaxing deep sleep meditation",
            iconEmoji = "🌙",
            gradientColors = listOf(0xFF1A237E, 0xFF3949AB)
        ),
        MusicGenre(
            id = "jazz",
            name = "Джаз & Блюз",
            searchQuery = "jazz blues cafe classics",
            iconEmoji = "🎷",
            gradientColors = listOf(0xFF3E2723, 0xFF8D6E63)
        )
    )

    /**
     * Standard ready-made curated playlists (Плейлисти для будь-якого настрою).
     */
    val predefinedPlaylists: List<CuratedPlaylist> = listOf(
        CuratedPlaylist(
            id = "curated_top_ua",
            title = "Топ Чарти України",
            description = "Найгарячіші новинки та улюблені хіти українського простору",
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80",
            gradientColors = listOf(0xFF1E88E5, 0xFFFFD54F),
            searchQuery = "топ українських пісень"
        ),
        CuratedPlaylist(
            id = "curated_chill_lofi",
            title = "Chill & Lofi Beats",
            description = "Затишний ритм для роботи, кодингу, читання та навчання",
            coverUrl = "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=600&q=80",
            gradientColors = listOf(0xFF6A1B9A, 0xFFAB47BC),
            searchQuery = "lofi chill beats for study coding"
        ),
        CuratedPlaylist(
            id = "curated_rock_drive",
            title = "Drive & Heavy Rock",
            description = "Вибухова енергія, гітарні рифи та драйв для дороги",
            coverUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600&q=80",
            gradientColors = listOf(0xFFD84315, 0xFFFF8A65),
            searchQuery = "drive heavy rock energy playlist"
        ),
        CuratedPlaylist(
            id = "curated_gym_power",
            title = "Gym Beast Mode",
            description = "Потужний біт, максимальна концентрація та мотивація",
            coverUrl = "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=600&q=80",
            gradientColors = listOf(0xFFC2185B, 0xFFE91E63),
            searchQuery = "workout gym motivation bass boosted"
        ),
        CuratedPlaylist(
            id = "curated_acoustic_relax",
            title = "Акустичний Затишок",
            description = "Тепле живе звучання гітари та спокійні мелодії",
            coverUrl = "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=600&q=80",
            gradientColors = listOf(0xFF4E342E, 0xFFBCAAA4),
            searchQuery = "acoustic guitar warm relax chill"
        ),
        CuratedPlaylist(
            id = "curated_retro_synth",
            title = "Retro Synthwave 80s",
            description = "Неонова естетика, аналогові синтезатори та нічні дороги",
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&q=80",
            gradientColors = listOf(0xFF00838F, 0xFF80DEEA),
            searchQuery = "synthwave retrowave 80s electronic"
        )
    )

    /**
     * Aggregates and groups any list of AudioTrack by artist name,
     * sorting artists by track count descending.
     */
    fun extractArtists(tracks: List<AudioTrack>): List<ArtistInfo> {
        if (tracks.isEmpty()) return emptyList()

        return tracks
            .filter { it.artist.isNotBlank() && it.artist != "<unknown>" && it.artist != "Unknown Artist" }
            .groupBy { it.artist.trim() }
            .map { (artistName, artistTracks) ->
                val bestArtwork = artistTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                ArtistInfo(
                    name = artistName,
                    artworkUrl = bestArtwork,
                    trackCount = artistTracks.size,
                    tracks = artistTracks
                )
            }
            .sortedByDescending { it.trackCount }
    }
}
