using Microsoft.Extensions.Caching.Distributed;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Tests;

public class AudiusIntegrationTests
{
    [Fact]
    public async Task SearchAsync_and_GetTrendingAsync_work_without_api_key()
    {
        using var httpClient = new HttpClient
        {
            BaseAddress = new Uri("https://api.audius.co/v1/"),
            Timeout = TimeSpan.FromSeconds(15)
        };

        var config = new ConfigurationBuilder().Build(); // No Audius:ApiKey configured
        var cache = new CacheService(new FakeDistributedCache(), NullLogger<CacheService>.Instance);

        var audiusService = new AudiusService(httpClient, config, cache);

        var trendingTracks = await audiusService.GetTrendingAsync(limit: 2);

        Assert.NotEmpty(trendingTracks);
        var track = trendingTracks[0];
        Assert.Equal("audius", track.Source);
        Assert.False(string.IsNullOrWhiteSpace(track.ExternalId));
        Assert.False(string.IsNullOrWhiteSpace(track.Title));
        Assert.False(string.IsNullOrWhiteSpace(track.StreamUrl));
        Assert.StartsWith("/api/audius/tracks/", track.StreamUrl);
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
