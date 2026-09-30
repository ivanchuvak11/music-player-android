using Microsoft.Extensions.Caching.Distributed;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Tests;

public class SoundCloudIntegrationTests
{
    [Fact]
    public async Task GetClientIdAsync_discovers_valid_client_id()
    {
        var factory = new SimpleHttpClientFactory();
        var config = new ConfigurationBuilder().Build();
        var tokenProvider = new SoundCloudTokenProvider(factory, config);

        var clientId = await tokenProvider.GetClientIdAsync();

        Assert.False(string.IsNullOrWhiteSpace(clientId));
        Assert.Equal(32, clientId.Length);
    }

    [Fact]
    public async Task SearchAsync_and_GetStreamAsync_returns_playable_stream()
    {
        var factory = new SimpleHttpClientFactory();
        var config = new ConfigurationBuilder().Build();
        var tokenProvider = new SoundCloudTokenProvider(factory, config);

        using var httpClient = factory.CreateClient("SoundCloudService");
        httpClient.BaseAddress = new Uri("https://api-v2.soundcloud.com/");

        var cacheService = new CacheService(new FakeDistributedCache(), NullLogger<CacheService>.Instance);
        var logger = NullLogger<SoundCloudService>.Instance;

        var soundCloudService = new SoundCloudService(
            httpClient,
            factory,
            tokenProvider,
            cacheService,
            logger);

        var results = await soundCloudService.SearchAsync("lofi beats", limit: 2);

        Assert.NotEmpty(results);
        var track = results[0];
        Assert.Equal("soundcloud", track.Source);
        Assert.False(string.IsNullOrWhiteSpace(track.ExternalId));
        Assert.False(string.IsNullOrWhiteSpace(track.Title));
        Assert.False(string.IsNullOrWhiteSpace(track.StreamUrl));

        using var streamResponse = await soundCloudService.GetStreamAsync(track.ExternalId, rangeHeader: null);
        Assert.True(streamResponse.IsSuccessStatusCode);
        Assert.True(streamResponse.Content.Headers.ContentLength > 0 || streamResponse.Content.Headers.ContentType != null);
    }

    private sealed class SimpleHttpClientFactory : IHttpClientFactory
    {
        public HttpClient CreateClient(string name)
        {
            var client = new HttpClient();
            client.DefaultRequestHeaders.UserAgent.ParseAdd(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            return client;
        }
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
