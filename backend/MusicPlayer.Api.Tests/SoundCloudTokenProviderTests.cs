using System.Net;
using System.Text;
using Microsoft.Extensions.Configuration;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Tests;

public class SoundCloudTokenProviderTests
{
    [Fact]
    public async Task Client_credentials_token_is_reused_until_expiration()
    {
        var handler = new StubHandler();
        var factory = new StubHttpClientFactory(handler);
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["SoundCloud:ClientId"] = "client-id",
                ["SoundCloud:ClientSecret"] = "client-secret"
            })
            .Build();
        var provider = new SoundCloudTokenProvider(factory, configuration);

        var first = await provider.GetAccessTokenAsync();
        var second = await provider.GetAccessTokenAsync();

        Assert.Equal("test-token", first);
        Assert.Equal(first, second);
        Assert.Equal(1, handler.RequestCount);
        Assert.Equal("Basic", handler.LastAuthorizationScheme);
    }

    private sealed class StubHttpClientFactory : IHttpClientFactory
    {
        private readonly HttpMessageHandler _handler;

        public StubHttpClientFactory(HttpMessageHandler handler)
        {
            _handler = handler;
        }

        public HttpClient CreateClient(string name)
        {
            return new HttpClient(_handler, disposeHandler: false);
        }
    }

    private sealed class StubHandler : HttpMessageHandler
    {
        public int RequestCount { get; private set; }
        public string? LastAuthorizationScheme { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            RequestCount++;
            LastAuthorizationScheme = request.Headers.Authorization?.Scheme;

            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(
                    "{\"access_token\":\"test-token\",\"expires_in\":3600}",
                    Encoding.UTF8,
                    "application/json")
            });
        }
    }
}
