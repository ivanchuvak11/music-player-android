using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Stats;

namespace MusicPlayer.Api.Controllers;

/// <summary>
/// Контролер звітності та аналітики роботи системи за періоди (день, тиждень, місяць).
/// Реалізує вимоги Лабораторної роботи № 5 з обробки запитів для різних категорій користувачів.
/// </summary>
[ApiController]
[Route("api/[controller]")]
public class StatsController : ControllerBase
{
    private readonly AppDbContext _db;

    public StatsController(AppDbContext db)
    {
        _db = db;
    }

    private int? GetCurrentUserId()
    {
        var value = User.FindFirstValue(ClaimTypes.NameIdentifier);

        return int.TryParse(value, out var userId)
            ? userId
            : null;
    }

    private static (DateTime? StartDate, DateTime EndDate, string NormalizedPeriod) ResolvePeriod(string? period)
    {
        var now = DateTime.UtcNow;
        var normalized = period?.Trim().ToLowerInvariant() ?? "week";

        return normalized switch
        {
            "day" or "today" => (now.AddDays(-1), now, "day"),
            "week" or "7d" => (now.AddDays(-7), now, "week"),
            "month" or "30d" => (now.AddDays(-30), now, "month"),
            "all" or "all-time" => (null, now, "all"),
            _ => (now.AddDays(-7), now, "week")
        };
    }

    /// <summary>
    /// Категорія: Зареєстрований користувач.
    /// Повертає персональний звіт активності користувача за обраний період (день, тиждень, місяць).
    /// </summary>
    [HttpGet("user-summary")]
    [Authorize]
    [ProducesResponseType(typeof(UserStatsSummaryDto), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> GetUserSummary([FromQuery] string? period = "week")
    {
        var userId = GetCurrentUserId();
        if (userId is null)
        {
            return Unauthorized(new { message = "Необхідна авторизація для отримання персонального звіту." });
        }

        var (startDate, endDate, normalizedPeriod) = ResolvePeriod(period);

        // 1. Плейлисти користувача
        var userPlaylistsQuery = _db.Playlists
            .AsNoTracking()
            .Where(p => p.UserId == userId);

        var playlistsCreatedInPeriod = await userPlaylistsQuery
            .Where(p => startDate == null || p.CreatedAt >= startDate)
            .CountAsync();

        var userPlaylistIds = await userPlaylistsQuery
            .Select(p => p.Id)
            .ToListAsync();

        // 2. Треки у плейлистах за період
        var playlistTracksQuery = _db.PlaylistTracks
            .AsNoTracking()
            .Where(t => userPlaylistIds.Contains(t.PlaylistId));

        if (startDate != null)
        {
            playlistTracksQuery = playlistTracksQuery.Where(t => t.AddedAt >= startDate);
        }

        var playlistTracks = await playlistTracksQuery
            .Select(t => new
            {
                t.Title,
                t.Artist,
                t.Source,
                DurationMs = t.DurationMs ?? 0L,
                Timestamp = t.AddedAt
            })
            .ToListAsync();

        // 3. Улюблені треки за період
        var favoritesQuery = _db.FavoriteTracks
            .AsNoTracking()
            .Where(f => f.UserId == userId);

        if (startDate != null)
        {
            favoritesQuery = favoritesQuery.Where(f => f.CreatedAt >= startDate);
        }

        var favoriteTracks = await favoritesQuery
            .Select(f => new
            {
                f.Title,
                f.Artist,
                f.Source,
                DurationMs = f.DurationMs ?? 0L,
                Timestamp = f.CreatedAt
            })
            .ToListAsync();

        // 4. Улюблені радіостанції за період
        var radioQuery = _db.FavoriteRadioStations
            .AsNoTracking()
            .Where(r => r.UserId == userId);

        if (startDate != null)
        {
            radioQuery = radioQuery.Where(r => r.CreatedAt >= startDate);
        }

        var favoriteRadios = await radioQuery
            .Select(r => new
            {
                r.Name,
                Country = r.Country ?? "UA",
                Timestamp = r.CreatedAt
            })
            .ToListAsync();

        // Агрегація тривалості
        long totalDurationMs = playlistTracks.Sum(t => t.DurationMs) +
                              favoriteTracks.Sum(f => f.DurationMs);

        // Агрегація топових артистів
        var allTracks = playlistTracks
            .Select(t => new { t.Title, t.Artist, t.Source, t.DurationMs, t.Timestamp, Type = "PlaylistTrack" })
            .Concat(favoriteTracks.Select(f => new { f.Title, f.Artist, f.Source, f.DurationMs, f.Timestamp, Type = "FavoriteTrack" }))
            .ToList();

        var topArtists = allTracks
            .Where(t => !string.IsNullOrWhiteSpace(t.Artist))
            .GroupBy(t => t.Artist.Trim(), StringComparer.OrdinalIgnoreCase)
            .OrderByDescending(g => g.Count())
            .Take(5)
            .Select(g => new TopArtistDto
            {
                Artist = g.Key,
                TrackCount = g.Count()
            })
            .ToList();

        // Агрегація джерел (YouTube, SoundCloud, Radio тощо)
        var totalItems = allTracks.Count + favoriteRadios.Count;
        var sourceBreakdown = new List<SourceStatDto>();

        if (totalItems > 0)
        {
            var rawSources = allTracks
                .Select(t => string.IsNullOrWhiteSpace(t.Source) ? "Unknown" : t.Source)
                .Concat(favoriteRadios.Select(_ => "Radio"));

            sourceBreakdown = rawSources
                .GroupBy(s => s)
                .Select(g => new SourceStatDto
                {
                    Source = g.Key,
                    Count = g.Count(),
                    Percentage = Math.Round((double)g.Count() / totalItems * 100.0, 1)
                })
                .OrderByDescending(s => s.Count)
                .ToList();
        }

        // Журнал останніх активностей
        var recentActivity = allTracks
            .Select(t => new RecentActivityItemDto
            {
                ActivityType = t.Type,
                Title = t.Title,
                ArtistOrDetails = t.Artist,
                Source = t.Source,
                Timestamp = t.Timestamp
            })
            .Concat(favoriteRadios.Select(r => new RecentActivityItemDto
            {
                ActivityType = "FavoriteRadio",
                Title = r.Name,
                ArtistOrDetails = $"Країна: {r.Country}",
                Source = "Radio",
                Timestamp = r.Timestamp
            }))
            .OrderByDescending(a => a.Timestamp)
            .Take(10)
            .ToList();

        var summary = new UserStatsSummaryDto
        {
            Period = normalizedPeriod,
            StartDateUtc = startDate,
            EndDateUtc = endDate,
            PlaylistsCreatedCount = playlistsCreatedInPeriod,
            TracksAddedCount = playlistTracks.Count,
            FavoritesAddedCount = favoriteTracks.Count,
            RadioFavoritesAddedCount = favoriteRadios.Count,
            TotalSavedItemsCount = totalItems,
            TotalDurationMs = totalDurationMs,
            TopArtists = topArtists,
            SourceBreakdown = sourceBreakdown,
            RecentActivity = recentActivity
        };

        return Ok(summary);
    }

    /// <summary>
    /// Категорія: Адміністратор / Зведена звітність платформи.
    /// Формує аналітичний звіт щодо навантаження та активності користувачів системи за період (день, тиждень, місяць).
    /// </summary>
    [HttpGet("system-report")]
    [AllowAnonymous]
    [ProducesResponseType(typeof(SystemReportDto), StatusCodes.Status200OK)]
    public async Task<IActionResult> GetSystemReport([FromQuery] string? period = "week")
    {
        var (startDate, endDate, normalizedPeriod) = ResolvePeriod(period);

        // 1. Користувачі
        var totalUsers = await _db.Users.CountAsync();
        var newUsersInPeriod = await _db.Users
            .Where(u => startDate == null || u.CreatedAt >= startDate)
            .CountAsync();

        // 2. Плейлисти
        var totalPlaylists = await _db.Playlists.CountAsync();
        var newPlaylistsInPeriod = await _db.Playlists
            .Where(p => startDate == null || p.CreatedAt >= startDate)
            .CountAsync();

        // 3. Треки у плейлистах
        var totalPlaylistTracks = await _db.PlaylistTracks.CountAsync();
        var playlistTracksInPeriod = await _db.PlaylistTracks
            .Where(t => startDate == null || t.AddedAt >= startDate)
            .CountAsync();

        // 4. Улюблені треки
        var totalFavorites = await _db.FavoriteTracks.CountAsync();
        var favoritesInPeriod = await _db.FavoriteTracks
            .Where(f => startDate == null || f.CreatedAt >= startDate)
            .CountAsync();

        // 5. Улюблені радіостанції
        var totalRadios = await _db.FavoriteRadioStations.CountAsync();

        // Топ артисти по системі (за обраний період або загально)
        var platformTracksQuery = _db.PlaylistTracks
            .AsNoTracking()
            .Where(t => !string.IsNullOrEmpty(t.Artist));

        if (startDate != null)
        {
            platformTracksQuery = platformTracksQuery.Where(t => t.AddedAt >= startDate);
        }

        var topArtists = await platformTracksQuery
            .GroupBy(t => t.Artist)
            .OrderByDescending(g => g.Count())
            .Take(5)
            .Select(g => new TopArtistDto
            {
                Artist = g.Key,
                TrackCount = g.Count()
            })
            .ToListAsync();

        // Загальносистемний розподіл джерел
        var totalTracksCount = totalPlaylistTracks + totalFavorites;
        var sourceDistribution = new List<SourceStatDto>();

        if (totalTracksCount > 0)
        {
            var playlistSourceStats = await _db.PlaylistTracks
                .AsNoTracking()
                .GroupBy(t => t.Source)
                .Select(g => new { Source = g.Key, Count = g.Count() })
                .ToListAsync();

            sourceDistribution = playlistSourceStats
                .Select(s => new SourceStatDto
                {
                    Source = string.IsNullOrWhiteSpace(s.Source) ? "Other" : s.Source,
                    Count = s.Count,
                    Percentage = Math.Round((double)s.Count / totalPlaylistTracks * 100.0, 1)
                })
                .OrderByDescending(s => s.Count)
                .ToList();
        }

        var report = new SystemReportDto
        {
            Period = normalizedPeriod,
            StartDateUtc = startDate,
            EndDateUtc = endDate,
            TotalUsers = totalUsers,
            NewUsersInPeriod = newUsersInPeriod,
            TotalPlaylists = totalPlaylists,
            NewPlaylistsInPeriod = newPlaylistsInPeriod,
            TotalPlaylistTracks = totalPlaylistTracks,
            PlaylistTracksAddedInPeriod = playlistTracksInPeriod,
            TotalFavoriteTracks = totalFavorites,
            FavoriteTracksAddedInPeriod = favoritesInPeriod,
            TotalFavoriteRadioStations = totalRadios,
            PlatformTopArtists = topArtists,
            PlatformSourceDistribution = sourceDistribution
        };

        return Ok(report);
    }

    /// <summary>
    /// Категорія: Гість (Guest).
    /// Публічний огляд можливостей та каталогу платформи без авторизації.
    /// </summary>
    [HttpGet("overview")]
    [AllowAnonymous]
    [ProducesResponseType(typeof(PublicOverviewDto), StatusCodes.Status200OK)]
    public async Task<IActionResult> GetPublicOverview()
    {
        var totalPlaylists = await _db.Playlists.CountAsync();
        var totalTracks = await _db.PlaylistTracks.CountAsync();

        var overview = new PublicOverviewDto
        {
            Status = "Online",
            ServerTimeUtc = DateTime.UtcNow,
            SupportedAudioSourcesCount = 4,
            SupportedSources = new List<string>
            {
                "YouTube Music",
                "SoundCloud",
                "Internet Radio",
                "Local Storage"
            },
            TotalPublicPlaylistsCount = totalPlaylists,
            TotalPublicTracksCount = totalTracks
        };

        return Ok(overview);
    }
}
