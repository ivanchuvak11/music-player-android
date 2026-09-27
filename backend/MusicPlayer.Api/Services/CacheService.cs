using System.Text.Json;
using Microsoft.Extensions.Caching.Distributed;

namespace MusicPlayer.Api.Services;

public class CacheService
{
    private static readonly JsonSerializerOptions JsonOptions = new(
        JsonSerializerDefaults.Web);

    private readonly IDistributedCache _cache;
    private readonly ILogger<CacheService> _logger;

    public CacheService(
        IDistributedCache cache,
        ILogger<CacheService> logger)
    {
        _cache = cache;
        _logger = logger;
    }

    public async Task<T> GetOrCreateAsync<T>(
        string key,
        TimeSpan absoluteExpiration,
        Func<Task<T>> factory,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var cachedValue = await _cache.GetStringAsync(
                key,
                cancellationToken);

            if (!string.IsNullOrWhiteSpace(cachedValue))
            {
                var value = JsonSerializer.Deserialize<T>(
                    cachedValue,
                    JsonOptions);

                if (value is not null)
                    return value;
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(
                ex,
                "Unable to read cache entry {CacheKey}.",
                key);
        }

        var freshValue = await factory();

        try
        {
            var serializedValue = JsonSerializer.Serialize(
                freshValue,
                JsonOptions);

            await _cache.SetStringAsync(
                key,
                serializedValue,
                new DistributedCacheEntryOptions
                {
                    AbsoluteExpirationRelativeToNow = absoluteExpiration
                },
                cancellationToken);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(
                ex,
                "Unable to write cache entry {CacheKey}.",
                key);
        }

        return freshValue;
    }
}
