using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using MusicPlayer.Api.Infrastructure;

namespace MusicPlayer.Api.Services;

public sealed class SoundCloudTokenProvider
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly IConfiguration _configuration;
    private readonly SemaphoreSlim _refreshLock = new(1, 1);

    private string? _accessToken;
    private DateTimeOffset _expiresAt;

    public SoundCloudTokenProvider(
        IHttpClientFactory httpClientFactory,
        IConfiguration configuration)
    {
        _httpClientFactory = httpClientFactory;
        _configuration = configuration;
    }

    public async Task<string> GetAccessTokenAsync(
        CancellationToken cancellationToken = default)
    {
        var configuredToken = _configuration["SoundCloud:AccessToken"];

        if (!string.IsNullOrWhiteSpace(configuredToken))
            return configuredToken;

        if (HasValidCachedToken())
            return _accessToken!;

        await _refreshLock.WaitAsync(cancellationToken);

        try
        {
            if (HasValidCachedToken())
                return _accessToken!;

            var clientId = _configuration["SoundCloud:ClientId"];
            var clientSecret = _configuration["SoundCloud:ClientSecret"];

            if (string.IsNullOrWhiteSpace(clientId) ||
                string.IsNullOrWhiteSpace(clientSecret))
            {
                throw new ExternalServiceConfigurationException(
                    "SoundCloud ClientId and ClientSecret are not configured.");
            }

            using var request = new HttpRequestMessage(
                HttpMethod.Post,
                "https://secure.soundcloud.com/oauth/token");

            var credentials = Convert.ToBase64String(
                Encoding.UTF8.GetBytes($"{clientId}:{clientSecret}"));

            request.Headers.Authorization =
                new AuthenticationHeaderValue("Basic", credentials);
            request.Content = new FormUrlEncodedContent(
                new Dictionary<string, string>
                {
                    ["grant_type"] = "client_credentials"
                });

            using var response = await _httpClientFactory
                .CreateClient("SoundCloudAuth")
                .SendAsync(request, cancellationToken);

            response.EnsureSuccessStatusCode();

            await using var stream = await response.Content
                .ReadAsStreamAsync(cancellationToken);
            using var document = await JsonDocument.ParseAsync(
                stream,
                cancellationToken: cancellationToken);

            if (!document.RootElement.TryGetProperty("access_token", out var tokenElement) ||
                string.IsNullOrWhiteSpace(tokenElement.GetString()))
            {
                throw new HttpRequestException(
                    "SoundCloud token response did not contain an access token.");
            }

            var expiresIn = document.RootElement.TryGetProperty("expires_in", out var expiresElement) &&
                            expiresElement.TryGetInt32(out var seconds)
                ? seconds
                : 3600;

            _accessToken = tokenElement.GetString();
            _expiresAt = DateTimeOffset.UtcNow.AddSeconds(
                Math.Max(1, expiresIn - Math.Min(60, expiresIn / 2)));

            return _accessToken!;
        }
        finally
        {
            _refreshLock.Release();
        }
    }

    private bool HasValidCachedToken()
    {
        return !string.IsNullOrWhiteSpace(_accessToken) &&
               DateTimeOffset.UtcNow < _expiresAt;
    }
}
