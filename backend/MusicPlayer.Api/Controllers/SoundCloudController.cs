using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using MusicPlayer.Api.Infrastructure;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/soundcloud")]
[Authorize]
public class SoundCloudController : ControllerBase
{
    private readonly SoundCloudService _soundCloud;

    public SoundCloudController(SoundCloudService soundCloud)
    {
        _soundCloud = soundCloud;
    }

    // GET /api/soundcloud/search?q=lofi&limit=10
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

        var tracks = await _soundCloud.SearchAsync(
            q,
            limit,
            cancellationToken);

        return Ok(tracks);
    }

    // GET /api/soundcloud/tracks/{id}
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

        var track = await _soundCloud.GetTrackAsync(id, cancellationToken);

        if (track is null)
        {
            return NotFound(new
            {
                message = "SoundCloud track not found or not playable."
            });
        }

        return Ok(track);
    }

    // GET /api/soundcloud/tracks/{id}/stream
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

        var response = await _soundCloud.GetStreamAsync(
            id,
            Request.Headers.Range.ToString(),
            cancellationToken);

        if (!response.IsSuccessStatusCode)
        {
            return StatusCode((int)response.StatusCode, new
            {
                message = "Unable to stream SoundCloud audio."
            });
        }

        return new UpstreamStreamResult(response);
    }
}
