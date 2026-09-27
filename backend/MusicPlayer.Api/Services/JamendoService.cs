using System.Text.Json;
using MusicPlayer.Api.DTOs.Jamendo;

namespace MusicPlayer.Api.Services;

public class JamendoService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;
    private readonly CacheService _cache;

    public JamendoService(
        HttpClient httpClient,
        IConfiguration configuration,
        CacheService cache)
    {
        _httpClient = httpClient;
        _configuration = configuration;
        _cache = cache;
    }

    public async Task<List<JamendoTrackDto>> SearchAsync(
        string query,
        int limit = 20)
    {
        query = query.Trim();

        if (string.IsNullOrWhiteSpace(query))
            return new List<JamendoTrackDto>();

        limit = Math.Clamp(limit, 1, 50);

        var cacheKey =
            $"jamendo:search:{query.ToLowerInvariant()}:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(10),
            () => FetchSearchAsync(query, limit));
    }

    public async Task<JamendoTrackDto?> GetTrackAsync(string trackId)
    {
        trackId = trackId.Trim();

        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        var cacheKey = $"jamendo:track:{trackId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<JamendoTrackDto?>(
            cacheKey,
            TimeSpan.FromHours(1),
            () => FetchTrackAsync(trackId));
    }

    public async Task<HttpResponseMessage> GetStreamAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        var track = await GetTrackAsync(trackId);

        if (track is null)
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.NotFound)
            {
                Content = JsonContent.Create(new
                {
                    message = "Jamendo track not found."
                })
            };
        }

        if (string.IsNullOrWhiteSpace(track.StreamUrl))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.NotFound)
            {
                Content = JsonContent.Create(new
                {
                    message = "Jamendo stream URL is not available."
                })
            };
        }

        return await _httpClient.GetAsync(
            track.StreamUrl,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
    }

    private async Task<List<JamendoTrackDto>> FetchSearchAsync(
        string query,
        int limit)
    {
        var url =
            "tracks/?" +
            $"client_id={Uri.EscapeDataString(GetClientId())}" +
            "&format=json" +
            $"&limit={limit}" +
            "&audioformat=mp32" +
            "&type=single+albumtrack" +
            "&include=licenses+musicinfo" +
            $"&search={Uri.EscapeDataString(query)}";

        using var stream =
            await _httpClient.GetStreamAsync(url);

        using var document =
            await JsonDocument.ParseAsync(stream);

        EnsureSuccessfulResponse(document.RootElement);

        return ParseTracks(document.RootElement);
    }

    private async Task<JamendoTrackDto?> FetchTrackAsync(string trackId)
    {
        var url =
            "tracks/?" +
            $"client_id={Uri.EscapeDataString(GetClientId())}" +
            "&format=json" +
            "&limit=1" +
            "&audioformat=mp32" +
            "&include=licenses+musicinfo" +
            $"&id={Uri.EscapeDataString(trackId)}";

        using var stream =
            await _httpClient.GetStreamAsync(url);

        using var document =
            await JsonDocument.ParseAsync(stream);

        EnsureSuccessfulResponse(document.RootElement);

        return ParseTracks(document.RootElement).FirstOrDefault();
    }

    private string GetClientId()
    {
        var clientId = _configuration["Jamendo:ClientId"];

        if (string.IsNullOrWhiteSpace(clientId))
        {
            throw new InvalidOperationException(
                "Jamendo ClientId is not configured.");
        }

        return clientId;
    }

    private static List<JamendoTrackDto> ParseTracks(JsonElement root)
    {
        var result = new List<JamendoTrackDto>();

        if (!root.TryGetProperty("results", out var results) ||
            results.ValueKind != JsonValueKind.Array)
        {
            return result;
        }

        foreach (var track in results.EnumerateArray())
        {
            var id = GetString(track, "id");
            var audioUrl = GetNullableString(track, "audio");

            if (string.IsNullOrWhiteSpace(id) ||
                string.IsNullOrWhiteSpace(audioUrl))
            {
                continue;
            }

            var durationSeconds = GetLong(track, "duration");

            result.Add(new JamendoTrackDto
            {
                ExternalId = id,
                Title = GetString(track, "name"),
                Artist = GetString(track, "artist_name"),
                ArtworkUrl =
                    GetNullableString(track, "album_image") ??
                    GetNullableString(track, "image"),
                DurationMs = durationSeconds.HasValue
                    ? durationSeconds.Value * 1000
                    : null,
                Album = GetNullableString(track, "album_name"),
                LicenseUrl = GetNullableString(track, "license_ccurl"),
                JamendoUrl = GetNullableString(track, "shareurl"),
                StreamUrl = audioUrl
            });
        }

        return result;
    }

    private static void EnsureSuccessfulResponse(JsonElement root)
    {
        if (!root.TryGetProperty("headers", out var headers))
            return;

        var status = GetNullableString(headers, "status");

        if (string.Equals(status, "success", StringComparison.OrdinalIgnoreCase))
            return;

        var errorMessage =
            GetNullableString(headers, "error_message") ??
            "Jamendo API request failed.";

        throw new InvalidOperationException(errorMessage);
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
