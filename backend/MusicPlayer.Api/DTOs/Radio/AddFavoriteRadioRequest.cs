using System.ComponentModel.DataAnnotations;

namespace MusicPlayer.Api.DTOs.Radio;

public class AddFavoriteRadioRequest
{
    [Required]
    [StringLength(128)]
    public string StationId { get; set; } = string.Empty;
}
