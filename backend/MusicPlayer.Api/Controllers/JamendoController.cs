using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using MusicPlayer.Api.Infrastructure;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/jamendo")]
[Authorize]
public class JamendoController : ControllerBase
{
    private readonly JamendoService _jamendo;

    public JamendoController(JamendoService jamendo)
    {
        _jamendo = jamendo;
    }

    // GET /api/jamendo/search?q=rock&limit=10
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

        var tracks = await _jamendo.SearchAsync(q, limit, cancellationToken);

        return Ok(tracks);
    }

    // GET /api/jamendo/tracks/{id}
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

        var track = await _jamendo.GetTrackAsync(id, cancellationToken);

        if (track is null)
        {
            return NotFound(new
            {
                message = "Jamendo track not found."
            });
        }

        return Ok(track);
    }

    // GET /api/jamendo/tracks/{id}/stream
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

        var response = await _jamendo.GetStreamAsync(
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
                    message = "Unable to get Jamendo stream."
                });
        }

        return new UpstreamStreamResult(response);
    }
}
