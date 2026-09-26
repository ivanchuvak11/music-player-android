namespace MusicPlayer.Api.Models;

public class FavoriteTrack
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public User User { get; set; } = null!;
    public string Source { get; set; } = string.Empty;
    public string ExternalId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string? ArtworkUrl { get; set; }
    public long? DurationMs { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}