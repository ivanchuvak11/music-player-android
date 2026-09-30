namespace MusicPlayer.Api.DTOs.Auth;

public class AuthResponse
{
    public string Token { get; set; } = string.Empty;
    public string AccessToken
    {
        get => Token;
        set => Token = value;
    }
    public string? RefreshToken { get; set; }
    public long ExpiresIn { get; set; } = 7200;
    public int UserId { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
}