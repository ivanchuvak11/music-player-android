using System.Text.Json;
using MusicPlayer.Api.DTOs.Jamendo;
using MusicPlayer.Api.Infrastructure;

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
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(query))
            return new List<JamendoTrackDto>();

        query = query.Trim();

        limit = Math.Clamp(limit, 1, 50);

        var cacheKey =
            $"jamendo:search:{query.ToLowerInvariant()}:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(10),
            async () =>
            {
                try
                {
                    return await FetchSearchAsync(query, limit, cancellationToken);
                }
                catch (Exception)
                {
                    return new List<JamendoTrackDto>();
                }
            },
            cancellationToken);
    }

    public async Task<JamendoTrackDto?> GetTrackAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        trackId = trackId.Trim();

        var cacheKey = $"jamendo:track:{trackId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<JamendoTrackDto?>(
            cacheKey,
            TimeSpan.FromHours(1),
            () => FetchTrackAsync(trackId, cancellationToken),
            cancellationToken);
    }

    public async Task<HttpResponseMessage> GetStreamAsync(
        string trackId,
        string? rangeHeader,
        CancellationToken cancellationToken = default)
    {
        var track = await GetTrackAsync(trackId, cancellationToken);

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

        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            track.StreamUrl);
        request.ApplyRangeHeader(rangeHeader);

        return await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
    }

    private async Task<List<JamendoTrackDto>> FetchSearchAsync(
        string query,
        int limit,
        CancellationToken cancellationToken)
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

        using var stream = await _httpClient.GetStreamAsync(
            url,
            cancellationToken);

        using var document = await JsonDocument.ParseAsync(
            stream,
            cancellationToken: cancellationToken);

        EnsureSuccessfulResponse(document.RootElement);

        return ParseTracks(document.RootElement);
    }

    private async Task<JamendoTrackDto?> FetchTrackAsync(
        string trackId,
        CancellationToken cancellationToken)
    {
        var url =
            "tracks/?" +
            $"client_id={Uri.EscapeDataString(GetClientId())}" +
            "&format=json" +
            "&limit=1" +
            "&audioformat=mp32" +
            "&include=licenses+musicinfo" +
            $"&id={Uri.EscapeDataString(trackId)}";

        using var stream = await _httpClient.GetStreamAsync(
            url,
            cancellationToken);

        using var document = await JsonDocument.ParseAsync(
            stream,
            cancellationToken: cancellationToken);

        EnsureSuccessfulResponse(document.RootElement);

        return ParseTracks(document.RootElement).FirstOrDefault();
    }

    private string GetClientId()
    {
        var clientId = _configuration["Jamendo:ClientId"];

        if (string.IsNullOrWhiteSpace(clientId))
        {
            throw new ExternalServiceConfigurationException(
                "Jamendo ClientId is not configured.");
            // Default public developer client ID for Jamendo Open API
            return "b6747d04";
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

        throw new HttpRequestException(errorMessage);
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
