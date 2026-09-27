namespace MusicPlayer.Api.Models;

public class PlaylistTrack
{
    public int Id { get; set; }
    public int PlaylistId { get; set; }
    public Playlist Playlist { get; set; } = null!;
    // "audius", "youtube", "soundcloud" або "jamendo"
    public string Source { get; set; } = string.Empty;
    // ID треку у Audius або YouTube
    public string ExternalId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string? ArtworkUrl { get; set; }
    public long? DurationMs { get; set; }
    public DateTime AddedAt { get; set; } = DateTime.UtcNow;
}
