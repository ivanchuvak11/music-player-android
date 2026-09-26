using System.Net.Http.Json;
using MusicPlayer.Api.DTOs.Radio;

namespace MusicPlayer.Api.Services;

public class RadioBrowserService
{
    private readonly HttpClient _httpClient;

    public RadioBrowserService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<List<RadioStationDto>> SearchAsync(
        string query,
        int limit = 20)
    {
        query = query.Trim();

        if (string.IsNullOrWhiteSpace(query))
            return new List<RadioStationDto>();

        limit = Math.Clamp(limit, 1, 50);

        var encodedQuery = Uri.EscapeDataString(query);

        var url =
            $"json/stations/search?name={encodedQuery}" +
            $"&limit={limit}" +
            "&hidebroken=true" +
            "&order=clickcount" +
            "&reverse=true";

        var stations =
            await _httpClient.GetFromJsonAsync<List<RadioStationDto>>(url);

        return stations ?? new List<RadioStationDto>();
    }

    public async Task<RadioStationDto?> GetByIdAsync(string stationId)
    {
    stationId = stationId.Trim();

    if (string.IsNullOrWhiteSpace(stationId))
        return null;

    var encodedId = Uri.EscapeDataString(stationId);
    var url = $"json/stations/byuuid/{encodedId}";
    var stations =
        await _httpClient.GetFromJsonAsync<List<RadioStationDto>>(url);

    return stations?.FirstOrDefault();
    }
    public async Task<List<RadioStationDto>> GetPopularAsync(int limit = 20)
    {
    limit = Math.Clamp(limit, 1, 50);

    var url =
        $"json/stations/topclick/{limit}" +
        "?hidebroken=true";

    var stations =
        await _httpClient.GetFromJsonAsync<List<RadioStationDto>>(url);

    return stations ?? new List<RadioStationDto>();
    }
}