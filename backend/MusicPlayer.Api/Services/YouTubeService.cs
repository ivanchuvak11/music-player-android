using System.Net.Http.Json;
using MusicPlayer.Api.DTOs.YouTube;
using MusicPlayer.Api.Infrastructure;
using YoutubeExplode;
using YoutubeExplode.Common;
using YoutubeExplode.Search;
using YoutubeExplode.Videos;
using YoutubeExplode.Videos.Streams;

namespace MusicPlayer.Api.Services;

public class YouTubeService
{
    private readonly YoutubeClient _youtube;
    private readonly HttpClient _httpClient;
    private readonly CacheService _cache;
    private readonly ILogger<YouTubeService> _logger;

    public YouTubeService(
        HttpClient httpClient,
        CacheService cache,
        ILogger<YouTubeService> logger)
    {
        _httpClient = httpClient;
        _youtube = new YoutubeClient(httpClient);
        _cache = cache;
        _logger = logger;
    }

    public async Task<List<YouTubeTrackDto>> SearchAsync(
        string query,
        int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(query))
            return new List<YouTubeTrackDto>();

        query = query.Trim();
        limit = Math.Clamp(limit, 1, 50);

        var cacheKey = $"youtube:search:{query.ToLowerInvariant()}:{limit}";

        return await _cache.GetOrCreateAsync(
            cacheKey,
            TimeSpan.FromMinutes(10),
            () => FetchSearchAsync(query, limit, cancellationToken),
            cancellationToken);
    }

    public async Task<YouTubeTrackDto?> GetTrackAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        trackId = trackId.Trim();

        var cacheKey = $"youtube:track:{trackId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<YouTubeTrackDto?>(
            cacheKey,
            TimeSpan.FromHours(1),
            () => FetchTrackAsync(trackId, cancellationToken),
            cancellationToken);
    }

    public async Task<string?> GetStreamUrlAsync(
        string trackId,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            return null;

        trackId = trackId.Trim();

        var cacheKey = $"youtube:stream:{trackId.ToLowerInvariant()}";

        return await _cache.GetOrCreateAsync<string?>(
            cacheKey,
            TimeSpan.FromHours(4),
            () => FetchStreamUrlAsync(trackId, cancellationToken),
            cancellationToken);
    }

    public async Task<HttpResponseMessage> GetStreamAsync(
        string trackId,
        string? rangeHeader,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(trackId))
            throw new ArgumentException("Track ID is required.", nameof(trackId));

        trackId = trackId.Trim();

        var streamUrl = await GetStreamUrlAsync(trackId, cancellationToken);

        if (string.IsNullOrWhiteSpace(streamUrl))
        {
            return new HttpResponseMessage(System.Net.HttpStatusCode.NotFound)
            {
                Content = JsonContent.Create(new
                {
                    message = "YouTube stream URL could not be resolved."
                })
            };
        }

        using var request = new HttpRequestMessage(HttpMethod.Get, streamUrl);
        request.ApplyRangeHeader(rangeHeader);

        return await _httpClient.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
    }

    private async Task<List<YouTubeTrackDto>> FetchSearchAsync(
        string query,
        int limit,
        CancellationToken cancellationToken)
    {
        var tracks = new List<YouTubeTrackDto>();

        await foreach (var result in _youtube.Search.GetVideosAsync(query, cancellationToken))
        {
            tracks.Add(MapFromSearchResult(result));

            if (tracks.Count >= limit)
                break;
        }

        return tracks;
    }

    private async Task<YouTubeTrackDto?> FetchTrackAsync(
        string trackId,
        CancellationToken cancellationToken)
    {
        try
        {
            var video = await _youtube.Videos.GetAsync(trackId, cancellationToken);
            return MapFromVideo(video);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to fetch YouTube video {TrackId}.", trackId);
            return null;
        }
    }

    private async Task<string?> FetchStreamUrlAsync(
        string trackId,
        CancellationToken cancellationToken)
    {
        try
        {
            var streamManifest = await _youtube.Videos.Streams
                .GetManifestAsync(trackId, cancellationToken);

            var audioStream = streamManifest
                .GetAudioOnlyStreams()
                .GetWithHighestBitrate();

            return audioStream?.Url;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to resolve YouTube audio stream for {TrackId}.", trackId);
            return null;
        }
    }

    private static YouTubeTrackDto MapFromSearchResult(VideoSearchResult result)
    {
        var id = result.Id.Value;
        var artworkUrl = result.Thumbnails.TryGetWithHighestResolution()?.Url ??
                         result.Thumbnails.FirstOrDefault()?.Url;

        return new YouTubeTrackDto
        {
            ExternalId = id,
            Title = result.Title,
            Artist = result.Author.ChannelTitle,
            ArtworkUrl = artworkUrl,
            DurationMs = result.Duration.HasValue ? (long)result.Duration.Value.TotalMilliseconds : null,
            YouTubeUrl = result.Url,
            StreamUrl = $"/api/youtube/tracks/{id}/stream"
        };
    }

    private static YouTubeTrackDto MapFromVideo(Video video)
    {
        var id = video.Id.Value;
        var artworkUrl = video.Thumbnails.TryGetWithHighestResolution()?.Url ??
                         video.Thumbnails.FirstOrDefault()?.Url;

        return new YouTubeTrackDto
        {
            ExternalId = id,
            Title = video.Title,
            Artist = video.Author.ChannelTitle,
            ArtworkUrl = artworkUrl,
            DurationMs = video.Duration.HasValue ? (long)video.Duration.Value.TotalMilliseconds : null,
            YouTubeUrl = video.Url,
            StreamUrl = $"/api/youtube/tracks/{id}/stream"
        };
    }
}
