using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Mvc;
using System.Text.Json;

namespace MusicPlayer.Api.Infrastructure;

public sealed class ApiExceptionHandler : IExceptionHandler
{
    private readonly ILogger<ApiExceptionHandler> _logger;

    public ApiExceptionHandler(ILogger<ApiExceptionHandler> logger)
    {
        _logger = logger;
    }

    public async ValueTask<bool> TryHandleAsync(
        HttpContext httpContext,
        Exception exception,
        CancellationToken cancellationToken)
    {
        if (httpContext.RequestAborted.IsCancellationRequested)
            return false;

        var (statusCode, title) = exception switch
        {
            ExternalServiceConfigurationException =>
                (StatusCodes.Status503ServiceUnavailable, "External service is not configured"),
            TaskCanceledException =>
                (StatusCodes.Status504GatewayTimeout, "External service timed out"),
            HttpRequestException =>
                (StatusCodes.Status502BadGateway, "External service request failed"),
            JsonException =>
                (StatusCodes.Status502BadGateway, "External service returned invalid data"),
            _ =>
                (StatusCodes.Status500InternalServerError, "An unexpected server error occurred")
        };

        _logger.LogError(
            exception,
            "Request {Method} {Path} failed with status {StatusCode}.",
            httpContext.Request.Method,
            httpContext.Request.Path,
            statusCode);

        httpContext.Response.StatusCode = statusCode;

        await httpContext.Response.WriteAsJsonAsync(
            new ProblemDetails
            {
                Status = statusCode,
                Title = title,
                Detail = statusCode == StatusCodes.Status500InternalServerError
                    ? null
                    : "The request could not be completed. Please try again later."
            },
            cancellationToken);

        return true;
    }
}
