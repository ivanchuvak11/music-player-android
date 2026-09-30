namespace MusicPlayer.Api.DTOs.YouTube;

public class YouTubeTrackDto
{
    public string Source { get; set; } = "youtube";
    public string ExternalId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string? ArtworkUrl { get; set; }
    public long? DurationMs { get; set; }
    public string? YouTubeUrl { get; set; }
    public string? StreamUrl { get; set; }
}
