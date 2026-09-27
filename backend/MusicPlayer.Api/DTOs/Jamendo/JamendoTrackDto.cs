namespace MusicPlayer.Api.DTOs.Jamendo;

public class JamendoTrackDto
{
    public string Source { get; set; } = "jamendo";
    public string ExternalId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string? ArtworkUrl { get; set; }
    public long? DurationMs { get; set; }
    public string? Album { get; set; }
    public string? LicenseUrl { get; set; }
    public string? JamendoUrl { get; set; }
    public string? StreamUrl { get; set; }
}
