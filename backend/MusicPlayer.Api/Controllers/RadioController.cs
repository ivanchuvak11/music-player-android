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

    // GET /api/radio/search?q=rock&countryCode=UA&genre=jazz
    [HttpGet("search")]
    public async Task<IActionResult> Search(
        [FromQuery] string? q,
        [FromQuery] string? countryCode,
        [FromQuery] string? genre,
        [FromQuery] int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(q) &&
            string.IsNullOrWhiteSpace(countryCode) &&
            string.IsNullOrWhiteSpace(genre))
        {
            return BadRequest(new
            {
                message = "Search query, countryCode or genre is required."
            });
        }

        var stations = await _radioBrowser.SearchAsync(
            q ?? string.Empty,
            countryCode,
            genre,
            limit,
            cancellationToken);

        return Ok(ToRadioStationResponse(stations));
    }

    // GET /api/radio/popular?limit=20
    [HttpGet("popular")]
    public async Task<IActionResult> GetPopular(
        [FromQuery] int limit = 20,
        CancellationToken cancellationToken = default)
    {
        var stations = await _radioBrowser.GetPopularAsync(
            limit,
            cancellationToken);

        return Ok(ToRadioStationResponse(stations));
    }

    // GET /api/radio/by-country/UA?limit=20
    [HttpGet("by-country/{countryCode}")]
    public async Task<IActionResult> GetByCountry(
        string countryCode,
        [FromQuery] int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(countryCode))
        {
            return BadRequest(new
            {
                message = "Country code is required."
            });
        }

        var stations = await _radioBrowser.GetByCountryAsync(
            countryCode,
            limit,
            cancellationToken);

        return Ok(ToRadioStationResponse(stations));
    }

    // GET /api/radio/by-genre/jazz?limit=20
    [HttpGet("by-genre/{genre}")]
    public async Task<IActionResult> GetByGenre(
        string genre,
        [FromQuery] int limit = 20,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(genre))
        {
            return BadRequest(new
            {
                message = "Genre is required."
            });
        }

        var stations = await _radioBrowser.GetByGenreAsync(
            genre,
            limit,
            cancellationToken);

        return Ok(ToRadioStationResponse(stations));
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
        AddFavoriteRadioRequest request,
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var stationId = request.StationId?.Trim() ?? string.Empty;

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
            await _radioBrowser.GetByIdAsync(stationId, cancellationToken);

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

        try
        {
            await _db.SaveChangesAsync();
        }
        catch (DbUpdateException ex) when (ex.IsUniqueConstraintViolation())
        {
            return Conflict(new
            {
                message = "Radio station is already in favorites."
            });
        }

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

    private static IEnumerable<object> ToRadioStationResponse(
        IEnumerable<RadioStationDto> stations)
    {
        return stations
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
    }
}
