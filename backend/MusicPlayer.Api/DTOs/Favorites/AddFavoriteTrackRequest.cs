using System.ComponentModel.DataAnnotations;

namespace MusicPlayer.Api.DTOs.Favorites;

public class AddFavoriteTrackRequest
{
    [Required]
    [StringLength(32)]
    public string Source { get; set; } = string.Empty;

    [Required]
    [StringLength(256)]
    public string ExternalId { get; set; } = string.Empty;

    [Required]
    [StringLength(500)]
    public string Title { get; set; } = string.Empty;

    [StringLength(300)]
    public string Artist { get; set; } = string.Empty;

    [Url]
    [StringLength(2048)]
    public string? ArtworkUrl { get; set; }

    [Range(0, long.MaxValue)]
    public long? DurationMs { get; set; }
}
