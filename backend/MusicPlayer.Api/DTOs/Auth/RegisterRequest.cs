using System.ComponentModel.DataAnnotations;

namespace MusicPlayer.Api.DTOs.Auth;

public class RegisterRequest
{
    [Required]
    [StringLength(100, MinimumLength = 1)]
    public string Username { get; set; } = string.Empty;

    [Required]
    [EmailAddress]
    [StringLength(320)]
    public string Email { get; set; } = string.Empty;

    [Required]
    [StringLength(256, MinimumLength = 8)]
    public string Password { get; set; } = string.Empty;
}
