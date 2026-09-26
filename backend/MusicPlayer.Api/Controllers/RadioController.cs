using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Radio;
using MusicPlayer.Api.Models;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/radio")]
[Authorize]
public class RadioController : ControllerBase
{
    private readonly AppDbContext _db;

    public RadioController(AppDbContext db)
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

    // GET /api/radio/favorites
    [HttpGet("favorites")]
    public async Task<IActionResult> GetFavorites()
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var stations = await _db.FavoriteRadioStations
            .AsNoTracking()
            .Where(r => r.UserId == userId)
            .OrderByDescending(r => r.CreatedAt)
            .Select(r => new
            {
                r.Id,
                r.StationId,
                r.Name,
                r.StreamUrl,
                r.LogoUrl,
                r.Country,
                r.Genre,
                r.CreatedAt
            })
            .ToListAsync();

        return Ok(stations);
    }

    // POST /api/radio/favorites
    [HttpPost("favorites")]
    public async Task<IActionResult> AddFavorite(
        AddFavoriteRadioRequest request)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var stationId = request.StationId.Trim();
        var name = request.Name.Trim();
        var streamUrl = request.StreamUrl.Trim();

        if (string.IsNullOrWhiteSpace(stationId) ||
            string.IsNullOrWhiteSpace(name) ||
            string.IsNullOrWhiteSpace(streamUrl))
        {
            return BadRequest(new
            {
                message = "StationId, Name and StreamUrl are required."
            });
        }

        var exists = await _db.FavoriteRadioStations.AnyAsync(r =>
            r.UserId == userId &&
            r.StationId == stationId);

        if (exists)
        {
            return Conflict(new
            {
                message = "Radio station is already in favorites."
            });
        }

        var station = new FavoriteRadioStation
        {
            UserId = userId.Value,
            StationId = stationId,
            Name = name,
            StreamUrl = streamUrl,
            LogoUrl = request.LogoUrl,
            Country = request.Country,
            Genre = request.Genre
        };

        _db.FavoriteRadioStations.Add(station);
        await _db.SaveChangesAsync();

        return Ok(new
        {
            station.Id,
            station.StationId,
            station.Name,
            station.StreamUrl,
            station.LogoUrl,
            station.Country,
            station.Genre,
            station.CreatedAt
        });
    }

    // DELETE /api/radio/favorites/5
    [HttpDelete("favorites/{id:int}")]
    public async Task<IActionResult> RemoveFavorite(int id)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var station = await _db.FavoriteRadioStations
            .SingleOrDefaultAsync(r =>
                r.Id == id &&
                r.UserId == userId);

        if (station is null)
        {
            return NotFound(new
            {
                message = "Favorite radio station not found."
            });
        }

        _db.FavoriteRadioStations.Remove(station);
        await _db.SaveChangesAsync();

        return NoContent();
    }
}