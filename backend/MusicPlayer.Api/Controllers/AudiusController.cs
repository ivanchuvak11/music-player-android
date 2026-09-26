using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
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
    public async Task<IActionResult> Search(
        [FromQuery] string q,
        [FromQuery] int limit = 20)
    {
        if (string.IsNullOrWhiteSpace(q))
        {
            return BadRequest(new
            {
                message = "Search query is required."
            });
        }

        var tracks = await _audius.SearchAsync(q, limit);

        return Ok(tracks);
    }

    // GET /api/audius/trending?limit=10
    [HttpGet("trending")]
    public async Task<IActionResult> Trending(
        [FromQuery] int limit = 20)
    {
        var tracks = await _audius.GetTrendingAsync(limit);

        return Ok(tracks);
    }

    // GET /api/audius/tracks/{id}
    [HttpGet("tracks/{id}")]
    public async Task<IActionResult> GetTrack(string id)
    {
        if (string.IsNullOrWhiteSpace(id))
        {
            return BadRequest(new
            {
                message = "Track ID is required."
            });
        }

        var track = await _audius.GetTrackAsync(id);

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

        var response =
            await _audius.GetStreamAsync(id, cancellationToken);

        if (!response.IsSuccessStatusCode)
        {
            response.Dispose();

            return StatusCode(
                (int)response.StatusCode,
                new
                {
                    message = "Unable to get Audius stream."
                });
        }

        var stream =
            await response.Content.ReadAsStreamAsync(cancellationToken);

        var contentType =
            response.Content.Headers.ContentType?.MediaType
            ?? "audio/mpeg";

        return File(stream, contentType);
    }
}