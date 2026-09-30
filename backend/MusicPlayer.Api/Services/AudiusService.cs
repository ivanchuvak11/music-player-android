using System.Net.Http.Headers;
using System.Text.Json;
using MusicPlayer.Api.DTOs.Audius;
using MusicPlayer.Api.Infrastructure;

namespace MusicPlayer.Api.Services;

public class AudiusService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;
    private readonly CacheService _cache;

    public AudiusService(
        HttpClient httpClient,
        IConfiguration configuration,
        CacheService cache)
    {
        _httpClient = httpClient;
        _configuration = configuration;
        _cache = cache;
    }

    public async Task<List<AudiusTrackDto>> SearchAsync(
        string query,
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(query))
            return new List<AudiusTrackDto>();

        query = query.Trim();

        limit = Math.Clamp(limit, 1, 50);

        var cacheKey =
            $"audius:search:{query.ToLowerInvariant()}:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(5),
            () => FetchSearchAsync(query, limit, cancellationToken),
            cancellationToken);
    }

    public async Task<List<AudiusTrackDto>> GetTrendingAsync(
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        limit = Math.Clamp(limit, 1, 50);

        var cacheKey = $"audius:trending:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(5),
            () => FetchTrendingAsync(limit, cancellationToken),
            cancellationToken);
    }

    public async Task<AudiusTrackDto?> GetTrackAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        trackId = trackId.Trim();

        var cacheKey = $"audius:track:{trackId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<AudiusTrackDto?>(
            cacheKey,
            TimeSpan.FromMinutes(30),
            () => FetchTrackAsync(trackId, cancellationToken),
            cancellationToken);
    }

    public async Task<HttpResponseMessage> GetStreamAsync(
        string trackId,
        string? rangeHeader,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            throw new ArgumentException("Track ID is required.", nameof(trackId));

        trackId = trackId.Trim();

        using var request = CreateAudiusRequest(
            $"tracks/{Uri.EscapeDataString(trackId)}/stream");
        request.ApplyRangeHeader(rangeHeader);

        var response = await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);

        return response;
    }

    private async Task<List<AudiusTrackDto>> FetchSearchAsync(
        string query,
        int limit,
        CancellationToken cancellationToken)
    {
        using var request = CreateAudiusRequest(
            $"tracks/search?query={Uri.EscapeDataString(query)}&limit={limit}");

        using var response = await _httpClient.SendAsync(
            request,
            cancellationToken);

        response.EnsureSuccessStatusCode();

        using var stream =
            await response.Content.ReadAsStreamAsync(cancellationToken);

        using var document =
            await JsonDocument.ParseAsync(
                stream,
                cancellationToken: cancellationToken);

        var result = new List<AudiusTrackDto>();

        if (!document.RootElement.TryGetProperty("data", out var data))
            return result;

        foreach (var track in data.EnumerateArray().Take(limit))
        {
            result.Add(ParseTrack(track));
        }

        return result;
    }

    private async Task<List<AudiusTrackDto>> FetchTrendingAsync(
        int limit,
        CancellationToken cancellationToken)
    {
        using var request = CreateAudiusRequest(
            $"tracks/trending?limit={limit}");

        using var response = await _httpClient.SendAsync(
            request,
            cancellationToken);

        response.EnsureSuccessStatusCode();

        using var stream =
            await response.Content.ReadAsStreamAsync(cancellationToken);

        using var document =
            await JsonDocument.ParseAsync(
                stream,
                cancellationToken: cancellationToken);

        var result = new List<AudiusTrackDto>();

        if (!document.RootElement.TryGetProperty("data", out var data))
            return result;

        foreach (var track in data.EnumerateArray())
        {
            result.Add(ParseTrack(track));
        }

        return result;
    }

    private async Task<AudiusTrackDto?> FetchTrackAsync(
        string trackId,
        CancellationToken cancellationToken)
    {
        using var request = CreateAudiusRequest(
            $"tracks/{Uri.EscapeDataString(trackId)}");

        using var response = await _httpClient.SendAsync(
            request,
            cancellationToken);

        if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
            return null;

        response.EnsureSuccessStatusCode();

        using var stream =
            await response.Content.ReadAsStreamAsync(cancellationToken);

        using var document =
            await JsonDocument.ParseAsync(
                stream,
                cancellationToken: cancellationToken);

        if (!document.RootElement.TryGetProperty("data", out var data))
            return null;

        return ParseTrack(data);
    }

    private HttpRequestMessage CreateAudiusRequest(string url)
    {
        var apiKey = _configuration["Audius:ApiKey"];

        if (string.IsNullOrWhiteSpace(apiKey))
            throw new ExternalServiceConfigurationException(
                "Audius API key is not configured.");

        var request = new HttpRequestMessage(HttpMethod.Get, url);

        request.Headers.Authorization =
            new AuthenticationHeaderValue("Bearer", apiKey);

        return request;
    }

    private static string GetString(
        JsonElement element,
        string propertyName)
    {
        return element.TryGetProperty(propertyName, out var property)
            ? property.GetString() ?? string.Empty
            : string.Empty;
    }

    private static string? GetNullableString(
        JsonElement element,
        string propertyName)
    {
        return element.TryGetProperty(propertyName, out var property)
            ? property.GetString()
            : null;
    }

    private static AudiusTrackDto ParseTrack(JsonElement track)
    {
        var id = GetString(track, "id");
        var title = GetString(track, "title");

        var artist = string.Empty;

        if (track.TryGetProperty("user", out var user))
            artist = GetString(user, "name");

        string? artworkUrl = null;

        if (track.TryGetProperty("artwork", out var artwork) &&
            artwork.ValueKind == JsonValueKind.Object)
        {
            artworkUrl =
                GetNullableString(artwork, "480x480") ??
                GetNullableString(artwork, "150x150");
        }

        long? durationMs = null;

        if (track.TryGetProperty("duration", out var duration) &&
            duration.TryGetInt64(out var durationSeconds))
        {
            durationMs = durationSeconds * 1000;
        }

        return new AudiusTrackDto
        {
            ExternalId = id,
            Title = title,
            Artist = artist,
            ArtworkUrl = artworkUrl,
            DurationMs = durationMs,
            StreamUrl = string.IsNullOrWhiteSpace(id)
                ? null
                : $"/api/audius/tracks/{id}/stream"
        };
    }
}
