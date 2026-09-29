using System.ComponentModel.DataAnnotations;

namespace MusicPlayer.Api.DTOs.Playlists;

public class CreatePlaylistRequest
{
    [Required]
    [StringLength(200, MinimumLength = 1)]
    public string Name { get; set; } = string.Empty;
}
