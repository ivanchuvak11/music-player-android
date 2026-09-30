using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Caching.Distributed;
using Microsoft.Extensions.Logging.Abstractions;
using MusicPlayer.Api.Controllers;
using MusicPlayer.Api.DTOs.Cover;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Tests;

public class CoverServiceTests
{
    private readonly CoverService _coverService;

    public CoverServiceTests()
    {
        var httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(10)
        };
        httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("MusicPlayerAndroid/1.0");

        var cache = new CacheService(new FakeDistributedCache(), NullLogger<CacheService>.Instance);
        var logger = NullLogger<CoverService>.Instance;

        _coverService = new CoverService(httpClient, cache, logger);
    }

    [Fact]
    public async Task GetCoverAsync_returns_high_resolution_artwork_for_known_track()
    {
        var result = await _coverService.GetCoverAsync("Queen", "Bohemian Rhapsody");

        Assert.NotNull(result);
        Assert.False(string.IsNullOrWhiteSpace(result.ArtworkUrl));
        Assert.False(string.IsNullOrWhiteSpace(result.HighResArtworkUrl));
        Assert.Contains("600x600", result.HighResArtworkUrl);
        Assert.Contains("Queen", result.Artist, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task GetCoverAsync_with_empty_inputs_returns_null()
    {
        var result = await _coverService.GetCoverAsync("", "   ");
        Assert.Null(result);
    }

    [Fact]
    public async Task CoversController_Search_returns_ok_with_cover()
    {
        var controller = new CoversController(_coverService);
        var actionResult = await controller.Search("Daft Punk", "Get Lucky", null, CancellationToken.None);

        var okResult = Assert.IsType<OkObjectResult>(actionResult);
        var cover = Assert.IsType<ArtworkCoverDto>(okResult.Value);

        Assert.False(string.IsNullOrWhiteSpace(cover.ArtworkUrl));
    }

    [Fact]
    public async Task CoversController_GetImage_returns_redirect_result()
    {
        var controller = new CoversController(_coverService);
        var actionResult = await controller.GetImage("Nirvana", "Smells Like Teen Spirit", null, CancellationToken.None);

        var redirectResult = Assert.IsType<RedirectResult>(actionResult);
        Assert.False(string.IsNullOrWhiteSpace(redirectResult.Url));
        Assert.StartsWith("https://", redirectResult.Url);
    }

    [Fact]
    public async Task CoversController_with_no_query_returns_bad_request()
    {
        var controller = new CoversController(_coverService);
        var actionResult = await controller.Search(null, null, null, CancellationToken.None);

        Assert.IsType<BadRequestObjectResult>(actionResult);
    }

    private sealed class FakeDistributedCache : IDistributedCache
    {
        private readonly Dictionary<string, byte[]> _storage = new();
        public byte[]? Get(string key) => _storage.TryGetValue(key, out var val) ? val : null;
        public Task<byte[]?> GetAsync(string key, CancellationToken token = default) => Task.FromResult(Get(key));
        public void Refresh(string key) { }
        public Task RefreshAsync(string key, CancellationToken token = default) => Task.CompletedTask;
        public void Remove(string key) => _storage.Remove(key);
        public Task RemoveAsync(string key, CancellationToken token = default) { _storage.Remove(key); return Task.CompletedTask; }
        public void Set(string key, byte[] value, DistributedCacheEntryOptions options) => _storage[key] = value;
        public Task SetAsync(string key, byte[] value, DistributedCacheEntryOptions options, CancellationToken token = default) { _storage[key] = value; return Task.CompletedTask; }
    }
}
