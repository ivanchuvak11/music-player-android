using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
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

        var tracks = await _jamendo.SearchAsync(q, limit);

        return Ok(tracks);
    }

    // GET /api/jamendo/tracks/{id}
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

        var track = await _jamendo.GetTrackAsync(id);

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
            await _jamendo.GetStreamAsync(id, cancellationToken);

        if (!response.IsSuccessStatusCode)
        {
            var errorContent =
                await response.Content.ReadAsStringAsync(cancellationToken);

            response.Dispose();

            return StatusCode(
                (int)response.StatusCode,
                new
                {
                    message = "Unable to get Jamendo stream.",
                    details = errorContent
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
