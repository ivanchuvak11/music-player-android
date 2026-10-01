using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using MusicPlayer.Api.DTOs.SoundCloud;
using MusicPlayer.Api.Infrastructure;

namespace MusicPlayer.Api.Services;

public class SoundCloudService
{
    private readonly HttpClient _httpClient;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly SoundCloudTokenProvider _tokenProvider;
    private readonly CacheService _cache;
    private readonly ILogger<SoundCloudService> _logger;

    public SoundCloudService(
        HttpClient httpClient,
        IHttpClientFactory httpClientFactory,
        SoundCloudTokenProvider tokenProvider,
        CacheService cache,
        ILogger<SoundCloudService> logger)
    {
        _httpClient = httpClient;
        _httpClientFactory = httpClientFactory;
        _tokenProvider = tokenProvider;
        _cache = cache;
        _logger = logger;
    }

    public async Task<List<SoundCloudTrackDto>> SearchAsync(
        string query,
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(query))
            return new List<SoundCloudTrackDto>();

        query = query.Trim();
        limit = Math.Clamp(limit, 1, 50);

        var cacheKey = $"soundcloud:search:{query.ToLowerInvariant()}:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(5),
            async () =>
            {
                try
                {
                    return await FetchSearchAsync(query, limit, cancellationToken);
                }
                catch (ExternalServiceConfigurationException)
                {
                    // SoundCloud credentials not provided in dev environment
                    return new List<SoundCloudTrackDto>();
                }
                catch (Exception)
                {
                    return new List<SoundCloudTrackDto>();
                }
            },
            cancellationToken);
    }

    public async Task<SoundCloudTrackDto?> GetTrackAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        trackId = trackId.Trim();
        var cacheKey = $"soundcloud:track:{trackId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<SoundCloudTrackDto?>(
            cacheKey,
            TimeSpan.FromMinutes(30),
            () => FetchTrackAsync(trackId, cancellationToken),
            cancellationToken);
    }

    public async Task<string?> GetStreamUrlAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        trackId = trackId.Trim();

        return await _cache.GetOrCreateAsync<string?>(
            $"soundcloud:stream:{trackId}",
            TimeSpan.FromHours(4),
            () => ResolveStreamUrlAsync(trackId, cancellationToken),
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

        // Retrieve or resolve direct audio stream URL with 4h cache
        var streamUrl = await _cache.GetOrCreateAsync<string?>(
            $"soundcloud:stream:{trackId}",
            TimeSpan.FromHours(4),
            () => ResolveStreamUrlAsync(trackId, cancellationToken),
            cancellationToken);

        if (string.IsNullOrWhiteSpace(streamUrl))
        {
            return new HttpResponseMessage(HttpStatusCode.NotFound)
            {
                Content = JsonContent.Create(new
                {
                    message = "SoundCloud stream URL is not available."
                })
            };
        }

        var streamClient = _httpClientFactory.CreateClient("SoundCloudAuth");
        var streamRequest = new HttpRequestMessage(HttpMethod.Get, streamUrl);
        streamRequest.ApplyRangeHeader(rangeHeader);

        var response = await streamClient.SendAsync(
            streamRequest,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);

        // If direct stream URL expired or forbidden, invalidate stream cache and retry once
        if (response.StatusCode == HttpStatusCode.Forbidden || response.StatusCode == HttpStatusCode.Gone)
        {
            response.Dispose();
            await _cache.RemoveAsync($"soundcloud:stream:{trackId}");

            var freshStreamUrl = await ResolveStreamUrlAsync(trackId, cancellationToken);
            if (string.IsNullOrWhiteSpace(freshStreamUrl))
            {
                return new HttpResponseMessage(HttpStatusCode.NotFound)
                {
                    Content = JsonContent.Create(new
                    {
                        message = "SoundCloud stream URL is no longer valid."
                    })
                };
            }

            var retryRequest = new HttpRequestMessage(HttpMethod.Get, freshStreamUrl);
            retryRequest.ApplyRangeHeader(rangeHeader);

            return await streamClient.SendAsync(
                retryRequest,
                HttpCompletionOption.ResponseHeadersRead,
                cancellationToken);
        }

        return response;
    }

    private async Task<List<SoundCloudTrackDto>> FetchSearchAsync(
        string query,
        int limit,
        CancellationToken cancellationToken)
    {
        var clientId = await _tokenProvider.GetClientIdAsync(cancellationToken);
        var url = $"search/tracks?q={Uri.EscapeDataString(query)}&client_id={clientId}&limit={limit}";

        var response = await _httpClient.GetAsync(url, cancellationToken);

        // If unauthorized, refresh client ID once and retry
        if (response.StatusCode == HttpStatusCode.Unauthorized)
        {
            response.Dispose();
            _tokenProvider.InvalidateClientId();
            clientId = await _tokenProvider.GetClientIdAsync(cancellationToken);
            url = $"search/tracks?q={Uri.EscapeDataString(query)}&client_id={clientId}&limit={limit}";
            response = await _httpClient.GetAsync(url, cancellationToken);
        }

        response.EnsureSuccessStatusCode();

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);

        var result = new List<SoundCloudTrackDto>();
        var root = document.RootElement;

        var tracks = root.ValueKind == JsonValueKind.Array
            ? root.EnumerateArray()
            : root.TryGetProperty("collection", out var collection) && collection.ValueKind == JsonValueKind.Array
                ? collection.EnumerateArray()
                : Enumerable.Empty<JsonElement>();

        foreach (var track in tracks)
        {
            var parsed = ParseTrack(track);
            if (parsed is not null)
            {
                result.Add(parsed);
            }
        }

        return result;
    }

    private async Task<SoundCloudTrackDto?> FetchTrackAsync(
        string trackId,
        CancellationToken cancellationToken)
    {
        var clientId = await _tokenProvider.GetClientIdAsync(cancellationToken);
        var url = $"tracks/{Uri.EscapeDataString(trackId)}?client_id={clientId}";

        var response = await _httpClient.GetAsync(url, cancellationToken);

        if (response.StatusCode == HttpStatusCode.Unauthorized)
        {
            response.Dispose();
            _tokenProvider.InvalidateClientId();
            clientId = await _tokenProvider.GetClientIdAsync(cancellationToken);
            url = $"tracks/{Uri.EscapeDataString(trackId)}?client_id={clientId}";
            response = await _httpClient.GetAsync(url, cancellationToken);
        }

        if (response.StatusCode == HttpStatusCode.NotFound)
            return null;

        response.EnsureSuccessStatusCode();

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);

        return ParseTrack(document.RootElement);
    }

    private async Task<string?> ResolveStreamUrlAsync(
        string trackId,
        CancellationToken cancellationToken)
    {
        var clientId = await _tokenProvider.GetClientIdAsync(cancellationToken);
        var url = $"tracks/{Uri.EscapeDataString(trackId)}?client_id={clientId}";

        var response = await _httpClient.GetAsync(url, cancellationToken);
        if (response.StatusCode == HttpStatusCode.Unauthorized)
        {
            response.Dispose();
            _tokenProvider.InvalidateClientId();
            clientId = await _tokenProvider.GetClientIdAsync(cancellationToken);
            url = $"tracks/{Uri.EscapeDataString(trackId)}?client_id={clientId}";
            response = await _httpClient.GetAsync(url, cancellationToken);
        }

        if (!response.IsSuccessStatusCode)
            return null;

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);

        if (!document.RootElement.TryGetProperty("media", out var media) ||
            !media.TryGetProperty("transcodings", out var transcodings) ||
            transcodings.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        string? progressiveUrl = null;
        string? fallbackUrl = null;

        foreach (var transcoding in transcodings.EnumerateArray())
        {
            if (!transcoding.TryGetProperty("url", out var transUrlProp))
                continue;

            var transUrl = transUrlProp.GetString();
            if (string.IsNullOrWhiteSpace(transUrl))
                continue;

            if (transcoding.TryGetProperty("format", out var format) &&
                format.TryGetProperty("protocol", out var protocolProp) &&
                string.Equals(protocolProp.GetString(), "progressive", StringComparison.OrdinalIgnoreCase))
            {
                progressiveUrl = transUrl;
                break;
            }

            fallbackUrl ??= transUrl;
        }

        var targetUrl = progressiveUrl ?? fallbackUrl;
        if (string.IsNullOrWhiteSpace(targetUrl))
            return null;

        var authClient = _httpClientFactory.CreateClient("SoundCloudAuth");
        var resolveUrl = $"{targetUrl}?client_id={clientId}";
        var resolveResponse = await authClient.GetAsync(resolveUrl, cancellationToken);

        if (!resolveResponse.IsSuccessStatusCode)
            return null;

        await using var resolveStream = await resolveResponse.Content.ReadAsStreamAsync(cancellationToken);
        using var resolveDoc = await JsonDocument.ParseAsync(resolveStream, cancellationToken: cancellationToken);

        return resolveDoc.RootElement.TryGetProperty("url", out var finalUrlProp)
            ? finalUrlProp.GetString()
            : null;
    }

    private static SoundCloudTrackDto? ParseTrack(JsonElement track)
    {
        var id = GetString(track, "id");
        var title = GetString(track, "title");

        if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(title))
            return null;

        var artist = string.Empty;
        var avatarUrl = (string?)null;

        if (track.TryGetProperty("user", out var user))
        {
            artist =
                GetNullableString(user, "username") ??
                GetNullableString(user, "full_name") ??
                string.Empty;

            avatarUrl = GetNullableString(user, "avatar_url");
        }

        var artwork = GetNullableString(track, "artwork_url") ?? avatarUrl;

        // Upgrade SoundCloud artwork thumbnail to higher resolution if possible (replace -large with -t500x500)
        if (!string.IsNullOrWhiteSpace(artwork) && artwork.Contains("-large."))
        {
            artwork = artwork.Replace("-large.", "-t500x500.");
        }

        return new SoundCloudTrackDto
        {
            ExternalId = id,
            Title = title,
            Artist = artist,
            ArtworkUrl = artwork,
            DurationMs = GetLong(track, "duration"),
            Genre = GetNullableString(track, "genre"),
            SoundCloudUrl = GetNullableString(track, "permalink_url"),
            StreamUrl = $"/api/soundcloud/tracks/{id}/stream"
        };
    }

    private static string GetString(
        JsonElement element,
        string propertyName)
    {
        if (!element.TryGetProperty(propertyName, out var property))
            return string.Empty;

        return property.ValueKind switch
        {
            JsonValueKind.Number => property.GetRawText(),
            JsonValueKind.String => property.GetString() ?? string.Empty,
            _ => string.Empty
        };
    }

    private static string? GetNullableString(
        JsonElement element,
        string propertyName)
    {
        return element.TryGetProperty(propertyName, out var property) &&
               property.ValueKind == JsonValueKind.String
            ? property.GetString()
            : null;
    }

    private static long? GetLong(
        JsonElement element,
        string propertyName)
    {
        return element.TryGetProperty(propertyName, out var property) &&
               property.TryGetInt64(out var value)
            ? value
            : null;
    }
}
