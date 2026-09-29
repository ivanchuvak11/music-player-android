using System.Net.Http.Headers;

namespace MusicPlayer.Api.Infrastructure;

public static class HttpRequestMessageExtensions
{
    public static void ApplyRangeHeader(
        this HttpRequestMessage request,
        string? rangeHeader)
    {
        if (!string.IsNullOrWhiteSpace(rangeHeader) &&
            RangeHeaderValue.TryParse(rangeHeader, out var range))
        {
            request.Headers.Range = range;
        }
    }
}
