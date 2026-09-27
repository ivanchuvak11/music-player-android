namespace MusicPlayer.Api.DTOs.SoundCloud;

public class SoundCloudTrackDto
{
    public string Source { get; set; } = "soundcloud";
    public string ExternalId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string? ArtworkUrl { get; set; }
    public long? DurationMs { get; set; }
    public string? Genre { get; set; }
    public string? SoundCloudUrl { get; set; }
    public string? StreamUrl { get; set; }
}
