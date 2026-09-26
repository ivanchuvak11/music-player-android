using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Radio;
using MusicPlayer.Api.Models;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/radio")]
[Authorize]
public class RadioController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly RadioBrowserService _radioBrowser;
 
    public RadioController(
    AppDbContext db,
    RadioBrowserService radioBrowser)
{
    _db = db;
    _radioBrowser = radioBrowser;
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

    if (string.IsNullOrWhiteSpace(stationId))
    {
        return BadRequest(new
        {
            message = "StationId is required."
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

    var radioStation =
        await _radioBrowser.GetByIdAsync(stationId);

    if (radioStation is null ||
        string.IsNullOrWhiteSpace(radioStation.StreamUrl))
    {
        return NotFound(new
        {
            message = "Radio station not found."
        });
    }

    var station = new FavoriteRadioStation
    {
        UserId = userId.Value,
        StationId = radioStation.StationUuid,
        Name = radioStation.Name,
        StreamUrl = radioStation.StreamUrl,
        LogoUrl = radioStation.LogoUrl,
        Country = radioStation.Country,
        Genre = radioStation.Tags
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

    // GET /api/radio/search?q=rock
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

    var stations = await _radioBrowser.SearchAsync(q, limit);

    var result = stations
        .Where(s =>
            !string.IsNullOrWhiteSpace(s.StationUuid) &&
            !string.IsNullOrWhiteSpace(s.StreamUrl))
        .Select(s => new
        {
            StationId = s.StationUuid,
            s.Name,
            s.StreamUrl,
            s.LogoUrl,
            s.Country,
            s.CountryCode,
            Genre = s.Tags,
            s.Codec,
            s.Bitrate
        });

    return Ok(result);
}

// GET /api/radio/popular?limit=20
[HttpGet("popular")]
public async Task<IActionResult> GetPopular(
    [FromQuery] int limit = 20)
{
    var stations = await _radioBrowser.GetPopularAsync(limit);

    var result = stations
        .Where(s =>
            !string.IsNullOrWhiteSpace(s.StationUuid) &&
            !string.IsNullOrWhiteSpace(s.StreamUrl))
        .Select(s => new
        {
            StationId = s.StationUuid,
            s.Name,
            s.StreamUrl,
            s.LogoUrl,
            s.Country,
            s.CountryCode,
            Genre = s.Tags,
            s.Codec,
            s.Bitrate
        });

    return Ok(result);
}
}