using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using MusicPlayer.Api.DTOs.SoundCloud;

namespace MusicPlayer.Api.Services;

public class SoundCloudService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;
    private readonly CacheService _cache;

    public SoundCloudService(
        HttpClient httpClient,
        IConfiguration configuration,
        CacheService cache)
    {
        _httpClient = httpClient;
        _configuration = configuration;
        _cache = cache;
    }

    public async Task<List<SoundCloudTrackDto>> SearchAsync(
        string query,
        int limit = 20)
    {
        query = query.Trim();

        if (string.IsNullOrWhiteSpace(query))
            return new List<SoundCloudTrackDto>();

        limit = Math.Clamp(limit, 1, 50);

        var cacheKey =
            $"soundcloud:search:{query.ToLowerInvariant()}:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(5),
            () => FetchSearchAsync(query, limit));
    }

    public async Task<SoundCloudTrackDto?> GetTrackAsync(string trackId)
    {
        trackId = trackId.Trim();

        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        var cacheKey = $"soundcloud:track:{trackId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<SoundCloudTrackDto?>(
            cacheKey,
            TimeSpan.FromMinutes(30),
            () => FetchTrackAsync(trackId));
    }

    public async Task<HttpResponseMessage> GetStreamAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        trackId = trackId.Trim();

        if (string.IsNullOrWhiteSpace(trackId))
            throw new ArgumentException("Track ID is required.");

        using var metadataRequest = CreateSoundCloudRequest(
            $"tracks/{Uri.EscapeDataString(trackId)}",
            acceptJson: true);

        using var metadataResponse = await _httpClient.SendAsync(
            metadataRequest,
            cancellationToken);

        if (!metadataResponse.IsSuccessStatusCode)
        {
            var errorContent =
                await metadataResponse.Content.ReadAsStringAsync(cancellationToken);

            return new HttpResponseMessage(metadataResponse.StatusCode)
            {
                Content = new StringContent(errorContent)
            };
        }

        using var stream =
            await metadataResponse.Content.ReadAsStreamAsync(cancellationToken);

        using var document =
            await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);

        if (!IsPlayable(document.RootElement))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.Forbidden)
            {
                Content = JsonContent.Create(new
                {
                    message = "SoundCloud track is not playable off-platform."
                })
            };
        }

        var streamUrl = GetNullableString(
            document.RootElement,
            "stream_url");

        if (string.IsNullOrWhiteSpace(streamUrl))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.NotFound)
            {
                Content = JsonContent.Create(new
                {
                    message = "SoundCloud stream URL is not available."
                })
            };
        }

        var streamRequest = CreateSoundCloudRequest(
            streamUrl,
            acceptJson: false);

        return await _httpClient.SendAsync(
            streamRequest,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
    }

    private async Task<List<SoundCloudTrackDto>> FetchSearchAsync(
        string query,
        int limit)
    {
        var url =
            $"tracks?q={Uri.EscapeDataString(query)}" +
            "&access=playable" +
            $"&limit={limit}" +
            "&linked_partitioning=true";

        using var request = CreateSoundCloudRequest(
            url,
            acceptJson: true);
        using var response = await _httpClient.SendAsync(request);

        response.EnsureSuccessStatusCode();

        using var stream =
            await response.Content.ReadAsStreamAsync();

        using var document =
            await JsonDocument.ParseAsync(stream);

        var result = new List<SoundCloudTrackDto>();

        var tracks = document.RootElement.ValueKind == JsonValueKind.Array
            ? document.RootElement.EnumerateArray()
            : document.RootElement.TryGetProperty("collection", out var collection)
                ? collection.EnumerateArray()
                : Enumerable.Empty<JsonElement>();

        foreach (var track in tracks)
        {
            if (IsPlayable(track))
            {
                result.Add(ParseTrack(track));
            }
        }

        return result;
    }

    private async Task<SoundCloudTrackDto?> FetchTrackAsync(string trackId)
    {
        using var request = CreateSoundCloudRequest(
            $"tracks/{Uri.EscapeDataString(trackId)}",
            acceptJson: true);

        using var response = await _httpClient.SendAsync(request);

        if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
            return null;

        response.EnsureSuccessStatusCode();

        using var stream =
            await response.Content.ReadAsStreamAsync();

        using var document =
            await JsonDocument.ParseAsync(stream);

        if (!IsPlayable(document.RootElement))
            return null;

        return ParseTrack(document.RootElement);
    }

    private HttpRequestMessage CreateSoundCloudRequest(
        string url,
        bool acceptJson)
    {
        var accessToken = _configuration["SoundCloud:AccessToken"];
        var clientId = _configuration["SoundCloud:ClientId"];

        if (string.IsNullOrWhiteSpace(accessToken) &&
            string.IsNullOrWhiteSpace(clientId))
        {
            throw new InvalidOperationException(
                "SoundCloud AccessToken or ClientId is not configured.");
        }

        if (string.IsNullOrWhiteSpace(accessToken) &&
            !string.IsNullOrWhiteSpace(clientId))
        {
            url = AppendQueryParameter(url, "client_id", clientId);
        }

        var request = new HttpRequestMessage(HttpMethod.Get, url);

        request.Headers.Accept.Add(
            new MediaTypeWithQualityHeaderValue(
                acceptJson ? "application/json" : "*/*"));

        if (!string.IsNullOrWhiteSpace(accessToken))
        {
            request.Headers.Authorization =
                new AuthenticationHeaderValue("OAuth", accessToken);
        }

        return request;
    }

    private static SoundCloudTrackDto ParseTrack(JsonElement track)
    {
        var id = GetString(track, "id");
        var title = GetString(track, "title");
        var artist = string.Empty;

        if (track.TryGetProperty("user", out var user))
        {
            artist =
                GetNullableString(user, "username") ??
                GetNullableString(user, "full_name") ??
                string.Empty;
        }

        return new SoundCloudTrackDto
        {
            ExternalId = id,
            Title = title,
            Artist = artist,
            ArtworkUrl = GetNullableString(track, "artwork_url"),
            DurationMs = GetLong(track, "duration"),
            Genre = GetNullableString(track, "genre"),
            SoundCloudUrl = GetNullableString(track, "permalink_url"),
            StreamUrl = string.IsNullOrWhiteSpace(id)
                ? null
                : $"/api/soundcloud/tracks/{id}/stream"
        };
    }

    private static bool IsPlayable(JsonElement track)
    {
        var access = GetNullableString(track, "access");
        var streamUrl = GetNullableString(track, "stream_url");

        return string.Equals(access, "playable", StringComparison.OrdinalIgnoreCase) &&
               !string.IsNullOrWhiteSpace(streamUrl);
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

    private static string AppendQueryParameter(
        string url,
        string name,
        string value)
    {
        var separator = url.Contains('?') ? '&' : '?';

        return url +
               separator +
               Uri.EscapeDataString(name) +
               "=" +
               Uri.EscapeDataString(value);
    }
}
