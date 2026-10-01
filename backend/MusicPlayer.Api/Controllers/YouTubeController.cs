using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using MusicPlayer.Api.Infrastructure;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/youtube")]
[Authorize]
public class YouTubeController : ControllerBase
{
    private readonly YouTubeService _youtube;

    public YouTubeController(YouTubeService youtube)
    {
        _youtube = youtube;
    }

    // GET /api/youtube/search?q=rock&limit=10
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

        var tracks = await _youtube.SearchAsync(q, limit, cancellationToken);

        return Ok(tracks);
    }

    // GET /api/youtube/tracks/{id}
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

        var track = await _youtube.GetTrackAsync(id, cancellationToken);

        if (track is null)
        {
            return NotFound(new
            {
                message = "YouTube track not found."
            });
        }

        return Ok(track);
    }

    // GET /api/youtube/tracks/{id}/stream
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

        var streamUrl = await _youtube.GetStreamUrlAsync(id, cancellationToken);
        if (string.IsNullOrWhiteSpace(streamUrl))
        {
            return NotFound(new
            {
                message = "Unable to get YouTube stream."
            });
        }

        return Redirect(streamUrl);
    }
}
