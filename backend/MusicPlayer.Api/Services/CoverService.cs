using System.Text.Json;
using MusicPlayer.Api.DTOs.Cover;

namespace MusicPlayer.Api.Services;

public class CoverService
{
    private readonly HttpClient _httpClient;
    private readonly CacheService _cache;
    private readonly ILogger<CoverService> _logger;

    public CoverService(
        HttpClient httpClient,
        CacheService cache,
        ILogger<CoverService> logger)
    {
        _httpClient = httpClient;
        _cache = cache;
        _logger = logger;
    }

    public async Task<ArtworkCoverDto?> GetCoverAsync(
        string? artist,
        string? title,
        string? query = null,
        CancellationToken cancellationToken = default)
    {
        var searchTerm = BuildSearchTerm(artist, title, query);
        if (string.IsNullOrWhiteSpace(searchTerm))
            return null;

        var cacheKey = $"cover:search:{searchTerm.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<ArtworkCoverDto?>(
            cacheKey,
            TimeSpan.FromDays(7),
            () => FetchCoverFromProvidersAsync(searchTerm, cancellationToken),
            cancellationToken);
    }

    private static string BuildSearchTerm(string? artist, string? title, string? query)
    {
        artist = artist?.Trim();
        title = title?.Trim();
        query = query?.Trim();

        if (!string.IsNullOrWhiteSpace(artist) && !string.IsNullOrWhiteSpace(title))
            return $"{artist} {title}";

        if (!string.IsNullOrWhiteSpace(title))
            return title;

        if (!string.IsNullOrWhiteSpace(artist))
            return artist;

        return query ?? string.Empty;
    }

    private async Task<ArtworkCoverDto?> FetchCoverFromProvidersAsync(
        string searchTerm,
        CancellationToken cancellationToken)
    {
        // 1. Try iTunes Search API (fast, high quality square artwork, free)
        try
        {
            var iTunesResult = await FetchFromITunesAsync(searchTerm, cancellationToken);
            if (iTunesResult is not null)
                return iTunesResult;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to fetch album cover from iTunes for '{SearchTerm}'.", searchTerm);
        }

        // 2. Fallback to Deezer API
        try
        {
            var deezerResult = await FetchFromDeezerAsync(searchTerm, cancellationToken);
            if (deezerResult is not null)
                return deezerResult;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to fetch album cover from Deezer for '{SearchTerm}'.", searchTerm);
        }

        return null;
    }

    private async Task<ArtworkCoverDto?> FetchFromITunesAsync(
        string searchTerm,
        CancellationToken cancellationToken)
    {
        var url = $"https://itunes.apple.com/search?term={Uri.EscapeDataString(searchTerm)}&media=music&entity=song&limit=1";
        using var response = await _httpClient.GetAsync(url, cancellationToken);

        if (!response.IsSuccessStatusCode)
            return null;

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);

        if (!doc.RootElement.TryGetProperty("results", out var results) ||
            results.ValueKind != JsonValueKind.Array ||
            results.GetArrayLength() == 0)
        {
            return null;
        }

        var first = results[0];
        var artworkUrl100 = GetNullableString(first, "artworkUrl100");
        if (string.IsNullOrWhiteSpace(artworkUrl100))
            return null;

        // Upgrade 100x100 to 600x600 resolution
        var highResUrl = artworkUrl100.Replace("100x100bb.jpg", "600x600bb.jpg")
                                      .Replace("100x100bb", "600x600bb");

        return new ArtworkCoverDto
        {
            ArtworkUrl = artworkUrl100,
            HighResArtworkUrl = highResUrl,
            Artist = GetNullableString(first, "artistName"),
            Album = GetNullableString(first, "collectionName"),
            Title = GetNullableString(first, "trackName"),
            Source = "itunes"
        };
    }

    private async Task<ArtworkCoverDto?> FetchFromDeezerAsync(
        string searchTerm,
        CancellationToken cancellationToken)
    {
        var url = $"https://api.deezer.com/search?q={Uri.EscapeDataString(searchTerm)}&limit=1";
        using var response = await _httpClient.GetAsync(url, cancellationToken);

        if (!response.IsSuccessStatusCode)
            return null;

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);

        if (!doc.RootElement.TryGetProperty("data", out var data) ||
            data.ValueKind != JsonValueKind.Array ||
            data.GetArrayLength() == 0)
        {
            return null;
        }

        var first = data[0];
        string? artworkUrl = null;
        string? highResUrl = null;
        string? albumName = null;

        if (first.TryGetProperty("album", out var album))
        {
            artworkUrl = GetNullableString(album, "cover_medium");
            highResUrl = GetNullableString(album, "cover_xl") ?? GetNullableString(album, "cover_big");
            albumName = GetNullableString(album, "title");
        }

        if (string.IsNullOrWhiteSpace(artworkUrl) && first.TryGetProperty("artist", out var artistObj))
        {
            artworkUrl = GetNullableString(artistObj, "picture_medium");
            highResUrl = GetNullableString(artistObj, "picture_xl") ?? GetNullableString(artistObj, "picture_big");
        }

        if (string.IsNullOrWhiteSpace(artworkUrl))
            return null;

        var artistName = (string?)null;
        if (first.TryGetProperty("artist", out var a))
        {
            artistName = GetNullableString(a, "name");
        }

        return new ArtworkCoverDto
        {
            ArtworkUrl = artworkUrl,
            HighResArtworkUrl = highResUrl ?? artworkUrl,
            Artist = artistName,
            Album = albumName,
            Title = GetNullableString(first, "title"),
            Source = "deezer"
        };
    }

    private static string? GetNullableString(JsonElement element, string propertyName)
    {
        return element.TryGetProperty(propertyName, out var prop) &&
               prop.ValueKind == JsonValueKind.String
            ? prop.GetString()
            : null;
    }
}
