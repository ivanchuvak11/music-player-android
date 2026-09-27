using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
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

        var tracks = await _soundCloud.SearchAsync(q, limit);

        return Ok(tracks);
    }

    // GET /api/soundcloud/tracks/{id}
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

        var track = await _soundCloud.GetTrackAsync(id);

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
            await _soundCloud.GetStreamAsync(id, cancellationToken);

        if (!response.IsSuccessStatusCode)
        {
            var errorContent =
                await response.Content.ReadAsStringAsync(cancellationToken);

            response.Dispose();

            return StatusCode(
                (int)response.StatusCode,
                new
                {
                    message = "Unable to get SoundCloud stream.",
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
