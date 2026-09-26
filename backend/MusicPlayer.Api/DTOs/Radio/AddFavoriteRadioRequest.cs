namespace MusicPlayer.Api.DTOs.Radio;

public class AddFavoriteRadioRequest
{
    public string StationId { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string StreamUrl { get; set; } = string.Empty;
    public string? LogoUrl { get; set; }
    public string? Country { get; set; }
    public string? Genre { get; set; }
}