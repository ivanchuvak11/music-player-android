namespace MusicPlayer.Api.DTOs.Cover;

public class ArtworkCoverDto
{
    public string? ArtworkUrl { get; set; }
    public string? HighResArtworkUrl { get; set; }
    public string? Artist { get; set; }
    public string? Album { get; set; }
    public string? Title { get; set; }
    public string Source { get; set; } = "itunes";
}
