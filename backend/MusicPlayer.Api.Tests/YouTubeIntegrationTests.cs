using Microsoft.Extensions.Caching.Distributed;
using Microsoft.Extensions.Logging.Abstractions;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Tests;

public class YouTubeIntegrationTests
{
    [Fact]
    public async Task SearchAsync_and_GetStreamUrlAsync_returns_playable_stream()
    {
        using var httpClient = new HttpClient();
        var cacheService = new CacheService(new FakeDistributedCache(), NullLogger<CacheService>.Instance);
        var logger = NullLogger<YouTubeService>.Instance;

        var ytService = new YouTubeService(httpClient, cacheService, logger);

        var results = await ytService.SearchAsync("Nirvana Smells Like Teen Spirit", limit: 1);

        Assert.NotEmpty(results);
        var track = results[0];
        Assert.Equal("youtube", track.Source);
        Assert.False(string.IsNullOrWhiteSpace(track.ExternalId));
        Assert.False(string.IsNullOrWhiteSpace(track.Title));

        var streamUrl = await ytService.GetStreamUrlAsync(track.ExternalId);
        Assert.False(string.IsNullOrWhiteSpace(streamUrl));
        Assert.StartsWith("https://", streamUrl);
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
