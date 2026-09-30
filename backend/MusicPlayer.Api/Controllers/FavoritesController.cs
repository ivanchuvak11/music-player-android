using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Favorites;
using MusicPlayer.Api.Models;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/favorites")]
[Authorize]
public class FavoritesController : ControllerBase
{
    private readonly AppDbContext _db;

    public FavoritesController(AppDbContext db)
    {
        _db = db;
    }

    private int? GetCurrentUserId()
    {
        var value = User.FindFirstValue(ClaimTypes.NameIdentifier);

        return int.TryParse(value, out var userId)
            ? userId
            : null;
    }

    // GET /api/favorites/tracks
    [HttpGet("tracks")]
    public async Task<IActionResult> GetFavoriteTracks()
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var tracks = await _db.FavoriteTracks
            .AsNoTracking()
            .Where(f => f.UserId == userId)
            .OrderByDescending(f => f.CreatedAt)
            .Select(f => new
            {
                f.Id,
                f.Source,
                f.ExternalId,
                f.Title,
                f.Artist,
                f.ArtworkUrl,
                f.DurationMs,
                f.CreatedAt
            })
            .ToListAsync();

        return Ok(tracks);
    }

    // POST /api/favorites/tracks
    [HttpPost("tracks")]
    public async Task<IActionResult> AddFavoriteTrack(
        AddFavoriteTrackRequest request)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var source = request.Source?.Trim().ToLowerInvariant() ?? string.Empty;
        var externalId = request.ExternalId?.Trim() ?? string.Empty;
        var title = request.Title?.Trim() ?? string.Empty;
        var artist = request.Artist?.Trim() ?? string.Empty;

        if (source != "audius" &&
            source != "youtube" &&
            source != "soundcloud" &&
            source != "jamendo")
        {
            return BadRequest(new
            {
                message = "Source must be audius, youtube, soundcloud or jamendo."
            });
        }

        if (string.IsNullOrWhiteSpace(externalId) ||
            string.IsNullOrWhiteSpace(title))
        {
            return BadRequest(new
            {
                message = "ExternalId and Title are required."
            });
        }

        var alreadyExists = await _db.FavoriteTracks.AnyAsync(f =>
            f.UserId == userId &&
            f.Source == source &&
            f.ExternalId == externalId);

        if (alreadyExists)
        {
            return Conflict(new
            {
                message = "Track is already in favorites."
            });
        }

        var favorite = new FavoriteTrack
        {
            UserId = userId.Value,
            Source = source,
            ExternalId = externalId,
            Title = title,
            Artist = artist,
            ArtworkUrl = request.ArtworkUrl,
            DurationMs = request.DurationMs
        };

        _db.FavoriteTracks.Add(favorite);

        try
        {
            await _db.SaveChangesAsync();
        }
        catch (DbUpdateException ex) when (ex.IsUniqueConstraintViolation())
        {
            return Conflict(new
            {
                message = "Track is already in favorites."
            });
        }

        return Ok(new
        {
            favorite.Id,
            favorite.Source,
            favorite.ExternalId,
            favorite.Title,
            favorite.Artist,
            favorite.ArtworkUrl,
            favorite.DurationMs,
            favorite.CreatedAt
        });
    }

    // DELETE /api/favorites/tracks/5
    [HttpDelete("tracks/{id:int}")]
    public async Task<IActionResult> RemoveFavoriteTrack(int id)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var favorite = await _db.FavoriteTracks
            .SingleOrDefaultAsync(f =>
                f.Id == id &&
                f.UserId == userId);

        if (favorite is null)
        {
            return NotFound(new
            {
                message = "Favorite track not found."
            });
        }

        _db.FavoriteTracks.Remove(favorite);
        await _db.SaveChangesAsync();

        return NoContent();
    }
}
