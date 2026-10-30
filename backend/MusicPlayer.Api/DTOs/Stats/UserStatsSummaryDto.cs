namespace MusicPlayer.Api.DTOs.Stats;

public class UserStatsSummaryDto
{
    public string Period { get; set; } = string.Empty;
    public DateTime? StartDateUtc { get; set; }
    public DateTime EndDateUtc { get; set; }
    public int PlaylistsCreatedCount { get; set; }
    public int TracksAddedCount { get; set; }
    public int FavoritesAddedCount { get; set; }
    public int RadioFavoritesAddedCount { get; set; }
    public int TotalSavedItemsCount { get; set; }
    public long TotalDurationMs { get; set; }
    public double TotalDurationMinutes => Math.Round(TotalDurationMs / 60000.0, 1);
    public List<TopArtistDto> TopArtists { get; set; } = new();
    public List<SourceStatDto> SourceBreakdown { get; set; } = new();
    public List<RecentActivityItemDto> RecentActivity { get; set; } = new();
}

public class TopArtistDto
{
    public string Artist { get; set; } = string.Empty;
    public int TrackCount { get; set; }
}

public class SourceStatDto
{
    public string Source { get; set; } = string.Empty;
    public int Count { get; set; }
    public double Percentage { get; set; }
}

public class RecentActivityItemDto
{
    public string ActivityType { get; set; } = string.Empty; // "PlaylistTrack", "FavoriteTrack", "FavoriteRadio"
    public string Title { get; set; } = string.Empty;
    public string ArtistOrDetails { get; set; } = string.Empty;
    public string Source { get; set; } = string.Empty;
    public DateTime Timestamp { get; set; }
}
