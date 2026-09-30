using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Playlists;
using MusicPlayer.Api.Models;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class PlaylistsController : ControllerBase
{
    private readonly AppDbContext _db;

    public PlaylistsController(AppDbContext db)
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

    // GET /api/playlists
    [HttpGet]
    public async Task<IActionResult> GetPlaylists()
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var playlists = await _db.Playlists
            .AsNoTracking()
            .Where(p => p.UserId == userId)
            .OrderByDescending(p => p.CreatedAt)
            .Select(p => new
            {
                p.Id,
                p.Name,
                p.CreatedAt,
                TrackCount = p.Tracks.Count
            })
            .ToListAsync();

        return Ok(playlists);
    }

    // GET /api/playlists/5
    [HttpGet("{id:int}")]
    public async Task<IActionResult> GetPlaylist(int id)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var playlist = await _db.Playlists
            .AsNoTracking()
            .Where(p => p.Id == id && p.UserId == userId)
            .Select(p => new
            {
                p.Id,
                p.Name,
                p.CreatedAt,

                Tracks = p.Tracks
                    .OrderBy(t => t.AddedAt)
                    .Select(t => new
                    {
                        t.Id,
                        t.Source,
                        t.ExternalId,
                        t.Title,
                        t.Artist,
                        t.ArtworkUrl,
                        t.DurationMs,
                        t.AddedAt
                    })
                    .ToList()
            })
            .SingleOrDefaultAsync();

        if (playlist is null)
            return NotFound(new { message = "Playlist not found." });

        return Ok(playlist);
    }

    // POST /api/playlists
    [HttpPost]
    public async Task<IActionResult> CreatePlaylist(
        CreatePlaylistRequest request)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var name = request.Name?.Trim() ?? string.Empty;

        if (string.IsNullOrWhiteSpace(name))
        {
            return BadRequest(new
            {
                message = "Playlist name is required."
            });
        }

        var playlist = new Playlist
        {
            Name = name,
            UserId = userId.Value
        };

        _db.Playlists.Add(playlist);
        await _db.SaveChangesAsync();

        return CreatedAtAction(
            nameof(GetPlaylist),
            new { id = playlist.Id },
            new
            {
                playlist.Id,
                playlist.Name,
                playlist.CreatedAt
            });
    }

    // POST /api/playlists/5/tracks
    [HttpPost("{id:int}/tracks")]
    public async Task<IActionResult> AddTrack(
        int id,
        AddTrackRequest request)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var playlist = await _db.Playlists
            .SingleOrDefaultAsync(
                p => p.Id == id &&
                     p.UserId == userId);

        if (playlist is null)
        {
            return NotFound(new
            {
                message = "Playlist not found."
            });
        }

        var source = request.Source?.Trim().ToLowerInvariant() ?? string.Empty;

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

        var externalId = request.ExternalId?.Trim() ?? string.Empty;
        var title = request.Title?.Trim() ?? string.Empty;
        var artist = request.Artist?.Trim() ?? string.Empty;

        if (string.IsNullOrWhiteSpace(externalId) ||
            string.IsNullOrWhiteSpace(title))
        {
            return BadRequest(new
            {
                message = "ExternalId and Title are required."
            });
        }

        var alreadyExists = await _db.PlaylistTracks.AnyAsync(t =>
            t.PlaylistId == id &&
            t.Source == source &&
            t.ExternalId == externalId);

        if (alreadyExists)
        {
            return Conflict(new
            {
                message = "Track is already in this playlist."
            });
        }

        var track = new PlaylistTrack
        {
            PlaylistId = id,
            Source = source,
            ExternalId = externalId,
            Title = title,
            Artist = artist,
            ArtworkUrl = request.ArtworkUrl,
            DurationMs = request.DurationMs
        };

        _db.PlaylistTracks.Add(track);

        try
        {
            await _db.SaveChangesAsync();
        }
        catch (DbUpdateException ex) when (ex.IsUniqueConstraintViolation())
        {
            return Conflict(new
            {
                message = "Track is already in this playlist."
            });
        }

        return Ok(new
        {
            track.Id,
            track.Source,
            track.ExternalId,
            track.Title,
            track.Artist,
            track.ArtworkUrl,
            track.DurationMs,
            track.AddedAt
        });
    }

    // DELETE /api/playlists/5/tracks/10
    [HttpDelete("{playlistId:int}/tracks/{trackId:int}")]
    public async Task<IActionResult> RemoveTrack(
        int playlistId,
        int trackId)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var track = await _db.PlaylistTracks
            .SingleOrDefaultAsync(t =>
                t.Id == trackId &&
                t.PlaylistId == playlistId &&
                t.Playlist.UserId == userId);

        if (track is null)
        {
            return NotFound(new
            {
                message = "Track not found."
            });
        }

        _db.PlaylistTracks.Remove(track);
        await _db.SaveChangesAsync();

        return NoContent();
    }

    // DELETE /api/playlists/5
    [HttpDelete("{id:int}")]
    public async Task<IActionResult> DeletePlaylist(int id)
    {
        var userId = GetCurrentUserId();

        if (userId is null)
            return Unauthorized();

        var playlist = await _db.Playlists
            .SingleOrDefaultAsync(
                p => p.Id == id &&
                     p.UserId == userId);

        if (playlist is null)
        {
            return NotFound(new
            {
                message = "Playlist not found."
            });
        }

        _db.Playlists.Remove(playlist);
        await _db.SaveChangesAsync();

        return NoContent();
    }
}
