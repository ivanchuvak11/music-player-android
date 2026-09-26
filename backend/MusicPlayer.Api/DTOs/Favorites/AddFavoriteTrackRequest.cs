namespace MusicPlayer.Api.DTOs.Favorites;

public class AddFavoriteTrackRequest
{
    public string Source { get; set; } = string.Empty;
    public string ExternalId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string? ArtworkUrl { get; set; }
    public long? DurationMs { get; set; }
}