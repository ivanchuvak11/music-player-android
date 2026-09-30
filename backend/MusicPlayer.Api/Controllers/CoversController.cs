using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/covers")]
[AllowAnonymous]
public class CoversController : ControllerBase
{
    private readonly CoverService _coverService;

    public CoversController(CoverService coverService)
    {
        _coverService = coverService;
    }

    // GET /api/covers/search?artist=Queen&title=Bohemian+Rhapsody
    [HttpGet("search")]
    public async Task<IActionResult> Search(
        [FromQuery] string? artist,
        [FromQuery] string? title,
        [FromQuery] string? q,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(artist) &&
            string.IsNullOrWhiteSpace(title) &&
            string.IsNullOrWhiteSpace(q))
        {
            return BadRequest(new
            {
                message = "At least artist, title, or query parameter is required."
            });
        }

        var cover = await _coverService.GetCoverAsync(artist, title, q, cancellationToken);
        if (cover is null)
        {
            return NotFound(new
            {
                message = "Artwork cover not found."
            });
        }

        return Ok(cover);
    }

    // GET /api/covers/image?artist=Queen&title=Bohemian+Rhapsody
    // Direct redirect to high-resolution image for Jetpack Compose AsyncImage / Glide / Coil
    [HttpGet("image")]
    public async Task<IActionResult> GetImage(
        [FromQuery] string? artist,
        [FromQuery] string? title,
        [FromQuery] string? q,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(artist) &&
            string.IsNullOrWhiteSpace(title) &&
            string.IsNullOrWhiteSpace(q))
        {
            return BadRequest(new
            {
                message = "At least artist, title, or query parameter is required."
            });
        }

        var cover = await _coverService.GetCoverAsync(artist, title, q, cancellationToken);
        if (cover is null || string.IsNullOrWhiteSpace(cover.ArtworkUrl))
        {
            return NotFound(new
            {
                message = "Artwork cover image not found."
            });
        }

        return Redirect(cover.HighResArtworkUrl ?? cover.ArtworkUrl);
    }
}
