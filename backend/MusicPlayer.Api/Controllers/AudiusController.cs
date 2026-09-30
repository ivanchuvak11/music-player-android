using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using MusicPlayer.Api.Infrastructure;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/audius")]
[Authorize]
public class AudiusController : ControllerBase
{
    private readonly AudiusService _audius;

    public AudiusController(AudiusService audius)
    {
        _audius = audius;
    }

    // GET /api/audius/search?q=electronic&limit=10
    [HttpGet("search")]
    [AllowAnonymous]
    public async Task<IActionResult> Search(
        [FromQuery] string q,
        [FromQuery] int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(q))
        {
            return BadRequest(new
            {
                message = "Search query is required."
            });
        }

        var tracks = await _audius.SearchAsync(q, limit, cancellationToken);

        return Ok(tracks);
    }

    // GET /api/audius/trending?limit=10
    [HttpGet("trending")]
    [AllowAnonymous]
    public async Task<IActionResult> Trending(
        [FromQuery] int limit = 20,
        CancellationToken cancellationToken = default)
    {
        var tracks = await _audius.GetTrendingAsync(limit, cancellationToken);

        return Ok(tracks);
    }

    // GET /api/audius/tracks/{id}
    [HttpGet("tracks/{id}")]
    [AllowAnonymous]
    public async Task<IActionResult> GetTrack(
        string id,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id))
        {
            return BadRequest(new
            {
                message = "Track ID is required."
            });
        }

        var track = await _audius.GetTrackAsync(id, cancellationToken);

        if (track is null)
        {
            return NotFound(new
            {
                message = "Audius track not found."
            });
        }

        return Ok(track);
    }

    // GET /api/audius/tracks/{id}/stream
    [HttpGet("tracks/{id}/stream")]
    [AllowAnonymous]
    [EnableRateLimiting("stream")]
    public async Task<IActionResult> Stream(
        string id,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id))
        {
            return BadRequest(new
            {
                message = "Track ID is required."
            });
        }

        var response = await _audius.GetStreamAsync(
            id,
            Request.Headers.Range.ToString(),
            cancellationToken);

        if (!response.IsSuccessStatusCode)
        {
            var statusCode = UpstreamStatusMapper.ToClientStatus(
                response.StatusCode);
            response.Dispose();

            return StatusCode(
                statusCode,
                new
                {
                    message = "Unable to get Audius stream."
                });
        }

        return new UpstreamStreamResult(response);
    }
}
