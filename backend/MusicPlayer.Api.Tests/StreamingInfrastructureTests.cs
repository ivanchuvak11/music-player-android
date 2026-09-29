using System.Net;
using System.Net.Http.Headers;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using MusicPlayer.Api.Infrastructure;

namespace MusicPlayer.Api.Tests;

public class StreamingInfrastructureTests
{
    [Fact]
    public void ApplyRangeHeader_copies_valid_range()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "https://example.test/audio");

        request.ApplyRangeHeader("bytes=100-199");

        Assert.Equal("bytes=100-199", request.Headers.Range?.ToString());
    }

    [Fact]
    public async Task UpstreamStreamResult_preserves_partial_content_headers_and_body()
    {
        using var content = new ByteArrayContent([1, 2, 3]);
        content.Headers.ContentType = new MediaTypeHeaderValue("audio/mpeg");
        content.Headers.ContentRange = new ContentRangeHeaderValue(100, 102, 1000);

        var upstream = new HttpResponseMessage(HttpStatusCode.PartialContent)
        {
            Content = content
        };
        upstream.Headers.AcceptRanges.Add("bytes");

        var httpContext = new DefaultHttpContext();
        httpContext.Response.Body = new MemoryStream();
        var actionContext = new ActionContext { HttpContext = httpContext };

        await new UpstreamStreamResult(upstream).ExecuteResultAsync(actionContext);

        Assert.Equal(StatusCodes.Status206PartialContent, httpContext.Response.StatusCode);
        Assert.Equal("audio/mpeg", httpContext.Response.ContentType);
        Assert.Equal("bytes 100-102/1000", httpContext.Response.Headers.ContentRange);
        Assert.Equal("bytes", httpContext.Response.Headers.AcceptRanges);
        Assert.Equal([1, 2, 3], ((MemoryStream)httpContext.Response.Body).ToArray());
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized, StatusCodes.Status502BadGateway)]
    [InlineData(HttpStatusCode.TooManyRequests, StatusCodes.Status503ServiceUnavailable)]
    [InlineData(HttpStatusCode.RequestedRangeNotSatisfiable, StatusCodes.Status416RangeNotSatisfiable)]
    public void Upstream_status_does_not_leak_provider_auth_failures(
        HttpStatusCode upstream,
        int expected)
    {
        Assert.Equal(expected, UpstreamStatusMapper.ToClientStatus(upstream));
    }
}
