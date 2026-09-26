namespace MusicPlayer.Api.Models;

public class FavoriteRadioStation
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public User User { get; set; } = null!;

    // ID станції із зовнішнього Radio API
    public string StationId { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string StreamUrl { get; set; } = string.Empty;
    public string? LogoUrl { get; set; }
    public string? Country { get; set; }
    public string? Genre { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}