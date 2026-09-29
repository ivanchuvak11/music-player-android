using System.Net.Http.Json;
using MusicPlayer.Api.DTOs.Radio;

namespace MusicPlayer.Api.Services;

public class RadioBrowserService
{
    private readonly HttpClient _httpClient;
    private readonly CacheService _cache;

    public RadioBrowserService(
        HttpClient httpClient,
        CacheService cache)
    {
        _httpClient = httpClient;
        _cache = cache;
    }

    public async Task<List<RadioStationDto>> SearchAsync(
        string query,
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        return await SearchAsync(
            query,
            countryCode: null,
            genre: null,
            limit,
            cancellationToken);
    }

    public async Task<List<RadioStationDto>> SearchAsync(
        string query,
        string? countryCode,
        string? genre,
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        query = query.Trim();
        countryCode = countryCode?.Trim().ToUpperInvariant();
        genre = genre?.Trim().ToLowerInvariant();

        if (string.IsNullOrWhiteSpace(query) &&
            string.IsNullOrWhiteSpace(countryCode) &&
            string.IsNullOrWhiteSpace(genre))
        {
            return new List<RadioStationDto>();
        }

        limit = Math.Clamp(limit, 1, 50);

        var cacheKey =
            "radio:search:" +
            $"q={NormalizeCachePart(query)}:" +
            $"country={NormalizeCachePart(countryCode)}:" +
            $"genre={NormalizeCachePart(genre)}:" +
            $"limit={limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(10),
            () => FetchSearchAsync(
                query,
                countryCode,
                genre,
                limit,
                cancellationToken),
            cancellationToken);
    }

    public async Task<List<RadioStationDto>> GetByCountryAsync(
        string countryCode,
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        countryCode = countryCode.Trim().ToUpperInvariant();

        if (string.IsNullOrWhiteSpace(countryCode))
            return new List<RadioStationDto>();

        return await SearchAsync(
            query: string.Empty,
            countryCode,
            genre: null,
            limit,
            cancellationToken);
    }

    public async Task<List<RadioStationDto>> GetByGenreAsync(
        string genre,
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        genre = genre.Trim().ToLowerInvariant();

        if (string.IsNullOrWhiteSpace(genre))
            return new List<RadioStationDto>();

        return await SearchAsync(
            query: string.Empty,
            countryCode: null,
            genre,
            limit,
            cancellationToken);
    }

    public async Task<RadioStationDto?> GetByIdAsync(
        string stationId,
        CancellationToken cancellationToken = default)
    {
        stationId = stationId.Trim();

        if (string.IsNullOrWhiteSpace(stationId))
            return null;

        var cacheKey =
            $"radio:station:{stationId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<RadioStationDto?>(
            cacheKey,
            TimeSpan.FromHours(24),
            () => FetchByIdAsync(stationId, cancellationToken),
            cancellationToken);
    }

    public async Task<List<RadioStationDto>> GetPopularAsync(
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        limit = Math.Clamp(limit, 1, 50);

        var cacheKey = $"radio:popular:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(10),
            () => FetchPopularAsync(limit, cancellationToken),
            cancellationToken);
    }

    private async Task<List<RadioStationDto>> FetchSearchAsync(
        string query,
        string? countryCode,
        string? genre,
        int limit,
        CancellationToken cancellationToken)
    {
        var queryParameters = new List<string>
        {
            $"limit={limit}",
            "hidebroken=true",
            "order=clickcount",
            "reverse=true"
        };

        if (!string.IsNullOrWhiteSpace(query))
        {
            queryParameters.Add(
                $"name={Uri.EscapeDataString(query)}");
        }

        if (!string.IsNullOrWhiteSpace(countryCode))
        {
            queryParameters.Add(
                $"countrycode={Uri.EscapeDataString(countryCode)}");
        }

        if (!string.IsNullOrWhiteSpace(genre))
        {
            queryParameters.Add(
                $"tag={Uri.EscapeDataString(genre)}");
        }

        var url = $"json/stations/search?{string.Join("&", queryParameters)}";

        var stations =
            await _httpClient.GetFromJsonAsync<List<RadioStationDto>>(
                url,
                cancellationToken);

        return stations ?? new List<RadioStationDto>();
    }

    private async Task<RadioStationDto?> FetchByIdAsync(
        string stationId,
        CancellationToken cancellationToken)
    {
        var encodedId = Uri.EscapeDataString(stationId);
        var url = $"json/stations/byuuid/{encodedId}";

        var stations =
            await _httpClient.GetFromJsonAsync<List<RadioStationDto>>(
                url,
                cancellationToken);

        return stations?.FirstOrDefault();
    }

    private async Task<List<RadioStationDto>> FetchPopularAsync(
        int limit,
        CancellationToken cancellationToken)
    {
        var url =
            $"json/stations/topclick/{limit}" +
            "?hidebroken=true";

        var stations =
            await _httpClient.GetFromJsonAsync<List<RadioStationDto>>(
                url,
                cancellationToken);

        return stations ?? new List<RadioStationDto>();
    }

    private static string NormalizeCachePart(string? value)
    {
        return string.IsNullOrWhiteSpace(value)
            ? "_"
            : value.Trim().ToLowerInvariant();
    }
}
