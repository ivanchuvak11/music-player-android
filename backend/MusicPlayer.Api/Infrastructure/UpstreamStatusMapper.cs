using System.Net;

namespace MusicPlayer.Api.Infrastructure;

public static class UpstreamStatusMapper
{
    public static int ToClientStatus(HttpStatusCode upstreamStatus)
    {
        return upstreamStatus switch
        {
            HttpStatusCode.NotFound => StatusCodes.Status404NotFound,
            HttpStatusCode.RequestedRangeNotSatisfiable =>
                StatusCodes.Status416RangeNotSatisfiable,
            HttpStatusCode.RequestTimeout or HttpStatusCode.GatewayTimeout =>
                StatusCodes.Status504GatewayTimeout,
            HttpStatusCode.TooManyRequests =>
                StatusCodes.Status503ServiceUnavailable,
            _ => StatusCodes.Status502BadGateway
        };
    }
}
