using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Auth;
using MusicPlayer.Api.Models;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly PasswordHasher<User> _passwordHasher = new();

    public AuthController(AppDbContext db)
    {
        _db = db;
    }

    [HttpPost("register")]
    public async Task<IActionResult> Register(RegisterRequest request)
    {
        var email = request.Email.Trim().ToLowerInvariant();
        var username = request.Username.Trim();

        if (string.IsNullOrWhiteSpace(username) ||
            string.IsNullOrWhiteSpace(email) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return BadRequest(new { message = "All fields are required." });
        }

        if (request.Password.Length < 8)
        {
            return BadRequest(new
            {
                message = "Password must contain at least 8 characters."
            });
        }

        if (await _db.Users.AnyAsync(u => u.Email == email))
        {
            return Conflict(new { message = "Email is already registered." });
        }

        var user = new User
        {
            Username = username,
            Email = email
        };

        user.PasswordHash =
            _passwordHasher.HashPassword(user, request.Password);

        _db.Users.Add(user);
        await _db.SaveChangesAsync();

        return StatusCode(201, new
        {
            user.Id,
            user.Username,
            user.Email,
            user.CreatedAt
        });
    }
}