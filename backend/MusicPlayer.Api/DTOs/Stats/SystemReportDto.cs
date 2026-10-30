namespace MusicPlayer.Api.DTOs.Stats;

public class SystemReportDto
{
    public string Period { get; set; } = string.Empty;
    public DateTime? StartDateUtc { get; set; }
    public DateTime EndDateUtc { get; set; }
    public int TotalUsers { get; set; }
    public int NewUsersInPeriod { get; set; }
    public int TotalPlaylists { get; set; }
    public int NewPlaylistsInPeriod { get; set; }
    public int TotalPlaylistTracks { get; set; }
    public int PlaylistTracksAddedInPeriod { get; set; }
    public int TotalFavoriteTracks { get; set; }
    public int FavoriteTracksAddedInPeriod { get; set; }
    public int TotalFavoriteRadioStations { get; set; }
    public List<TopArtistDto> PlatformTopArtists { get; set; } = new();
    public List<SourceStatDto> PlatformSourceDistribution { get; set; } = new();
}

public class PublicOverviewDto
{
    public string Status { get; set; } = "Online";
    public DateTime ServerTimeUtc { get; set; } = DateTime.UtcNow;
    public int SupportedAudioSourcesCount { get; set; } = 4;
    public List<string> SupportedSources { get; set; } = new()
    {
        "YouTube Music",
        "SoundCloud",
        "Internet Radio",
        "Local Storage"
    };
    public int TotalPublicPlaylistsCount { get; set; }
    public int TotalPublicTracksCount { get; set; }
}
