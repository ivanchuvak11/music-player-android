using Microsoft.AspNetCore.Mvc;
using Microsoft.Net.Http.Headers;

namespace MusicPlayer.Api.Infrastructure;

public sealed class UpstreamStreamResult : IActionResult
{
    private readonly HttpResponseMessage _upstreamResponse;

    public UpstreamStreamResult(HttpResponseMessage upstreamResponse)
    {
        _upstreamResponse = upstreamResponse;
    }

    public async Task ExecuteResultAsync(ActionContext context)
    {
        using (_upstreamResponse)
        {
            var response = context.HttpContext.Response;
            var cancellationToken = context.HttpContext.RequestAborted;

            response.StatusCode = (int)_upstreamResponse.StatusCode;

            CopyHeader(_upstreamResponse.Content.Headers.ContentType, value =>
                response.ContentType = value.ToString());
            CopyHeader(_upstreamResponse.Content.Headers.ContentLength, value =>
                response.ContentLength = value);
            CopyHeader(_upstreamResponse.Content.Headers.ContentRange, value =>
                response.Headers[HeaderNames.ContentRange] = value.ToString());

            if (_upstreamResponse.Headers.AcceptRanges.Count > 0)
            {
                response.Headers[HeaderNames.AcceptRanges] =
                    _upstreamResponse.Headers.AcceptRanges.ToArray();
            }

            if (_upstreamResponse.Headers.ETag is not null)
            {
                response.Headers[HeaderNames.ETag] =
                    _upstreamResponse.Headers.ETag.ToString();
            }

            await using var stream = await _upstreamResponse.Content
                .ReadAsStreamAsync(cancellationToken);

            await stream.CopyToAsync(response.Body, cancellationToken);
        }
    }

    private static void CopyHeader<T>(T? value, Action<T> copy)
        where T : class
    {
        if (value is not null)
            copy(value);
    }

    private static void CopyHeader(long? value, Action<long> copy)
    {
        if (value.HasValue)
            copy(value.Value);
    }
}
