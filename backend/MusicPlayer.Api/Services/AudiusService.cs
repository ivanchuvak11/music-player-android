using System.Net.Http.Headers;
using System.Text.Json;
using MusicPlayer.Api.DTOs.Audius;

namespace MusicPlayer.Api.Services;

public class AudiusService
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _configuration;

    public AudiusService(
        HttpClient httpClient,
        IConfiguration configuration)
    {
        _httpClient = httpClient;
        _configuration = configuration;
    }

    public async Task<List<AudiusTrackDto>> SearchAsync(
        string query,
        int limit = 20)
    {
        query = query.Trim();

        if (string.IsNullOrWhiteSpace(query))
            return new List<AudiusTrackDto>();

        limit = Math.Clamp(limit, 1, 50);

        var apiKey = _configuration["Audius:ApiKey"];

        if (string.IsNullOrWhiteSpace(apiKey))
            throw new InvalidOperationException(
                "Audius API key is not configured.");

        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            $"tracks/search?query={Uri.EscapeDataString(query)}");

        request.Headers.Authorization =
            new AuthenticationHeaderValue("Bearer", apiKey);

        using var response = await _httpClient.SendAsync(request);

        response.EnsureSuccessStatusCode();

        using var stream =
            await response.Content.ReadAsStreamAsync();

        using var document =
            await JsonDocument.ParseAsync(stream);

        var result = new List<AudiusTrackDto>();

        if (!document.RootElement.TryGetProperty("data", out var data))
            return result;

        foreach (var track in data.EnumerateArray().Take(limit))
        {
          result.Add(ParseTrack(track));
        }

        return result;
    }

    public async Task<List<AudiusTrackDto>> GetTrendingAsync(
    int limit = 20)
    {
    limit = Math.Clamp(limit, 1, 50);

    var apiKey = _configuration["Audius:ApiKey"];

    if (string.IsNullOrWhiteSpace(apiKey))
        throw new InvalidOperationException(
            "Audius API key is not configured.");

    using var request = new HttpRequestMessage(
        HttpMethod.Get,
        $"tracks/trending?limit={limit}");

    request.Headers.Authorization =
        new AuthenticationHeaderValue("Bearer", apiKey);

    using var response = await _httpClient.SendAsync(request);

    response.EnsureSuccessStatusCode();

    using var stream =
        await response.Content.ReadAsStreamAsync();

    using var document =
        await JsonDocument.ParseAsync(stream);

    var result = new List<AudiusTrackDto>();

    if (!document.RootElement.TryGetProperty("data", out var data))
        return result;

    foreach (var track in data.EnumerateArray())
    {
        result.Add(ParseTrack(track));
    }

    return result;
    }

    public async Task<AudiusTrackDto?> GetTrackAsync(string trackId)
    {
    trackId = trackId.Trim();

    if (string.IsNullOrWhiteSpace(trackId))
        return null;

    var apiKey = _configuration["Audius:ApiKey"];

    if (string.IsNullOrWhiteSpace(apiKey))
        throw new InvalidOperationException(
            "Audius API key is not configured.");

    using var request = new HttpRequestMessage(
        HttpMethod.Get,
        $"tracks/{Uri.EscapeDataString(trackId)}");

    request.Headers.Authorization =
        new AuthenticationHeaderValue("Bearer", apiKey);

    using var response = await _httpClient.SendAsync(request);

    if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
        return null;

    response.EnsureSuccessStatusCode();

    using var stream =
        await response.Content.ReadAsStreamAsync();

    using var document =
        await JsonDocument.ParseAsync(stream);

    if (!document.RootElement.TryGetProperty("data", out var data))
        return null;

    return ParseTrack(data);
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

    public async Task<HttpResponseMessage> GetStreamAsync(
    string trackId,
    CancellationToken cancellationToken = default)
    {
    trackId = trackId.Trim();

    if (string.IsNullOrWhiteSpace(trackId))
        throw new ArgumentException("Track ID is required.");

    var apiKey = _configuration["Audius:ApiKey"];

    if (string.IsNullOrWhiteSpace(apiKey))
        throw new InvalidOperationException(
            "Audius API key is not configured.");

    var request = new HttpRequestMessage(
        HttpMethod.Get,
        $"tracks/{Uri.EscapeDataString(trackId)}/stream");

    request.Headers.Authorization =
        new AuthenticationHeaderValue("Bearer", apiKey);

    var response = await _httpClient.SendAsync(
        request,
        HttpCompletionOption.ResponseHeadersRead,
        cancellationToken);

    return response;
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
        DurationMs = durationMs
    };
    }
}