namespace MusicPlayer.Api.DTOs.Audius;

public class AudiusTrackDto
{
    public string Source { get; set; } = "audius";
    public string ExternalId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string? ArtworkUrl { get; set; }
    public long? DurationMs { get; set; }
}