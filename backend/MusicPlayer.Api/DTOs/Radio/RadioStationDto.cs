using System.Text.Json.Serialization;

namespace MusicPlayer.Api.DTOs.Radio;

public class RadioStationDto
{
    [JsonPropertyName("stationuuid")]
    public string StationUuid { get; set; } = string.Empty;

    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty;

    private string _streamUrl = string.Empty;

    [JsonPropertyName("url_resolved")]
    public string StreamUrl
    {
        get => !string.IsNullOrWhiteSpace(_streamUrl) ? _streamUrl : (UrlFallback ?? string.Empty);
        set => _streamUrl = value;
    }

    [JsonPropertyName("url")]
    public string? UrlFallback { get; set; }

    [JsonPropertyName("favicon")]
    public string? LogoUrl { get; set; }

    [JsonPropertyName("country")]
    public string? Country { get; set; }

    [JsonPropertyName("countrycode")]
    public string? CountryCode { get; set; }

    [JsonPropertyName("tags")]
    public string? Tags { get; set; }

    [JsonPropertyName("codec")]
    public string? Codec { get; set; }

    [JsonPropertyName("bitrate")]
    public int Bitrate { get; set; }
}