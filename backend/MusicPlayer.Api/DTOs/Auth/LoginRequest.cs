using System.ComponentModel.DataAnnotations;

namespace MusicPlayer.Api.DTOs.Auth;

public class LoginRequest
{
    [Required]
    [EmailAddress]
    [StringLength(320)]
    public string Email { get; set; } = string.Empty;

    [Required]
    [StringLength(256)]
    public string Password { get; set; } = string.Empty;
}
