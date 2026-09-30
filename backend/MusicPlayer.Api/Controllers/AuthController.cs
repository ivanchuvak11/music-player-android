using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Auth;
using MusicPlayer.Api.Models;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[EnableRateLimiting("auth")]
public class AuthController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly JwtService _jwtService;
    private readonly PasswordHasher<User> _passwordHasher = new();

    public AuthController(
        AppDbContext db,
        JwtService jwtService)
    {
        _db = db;
        _jwtService = jwtService;
    }

    [HttpPost("register")]
    public async Task<IActionResult> Register(RegisterRequest request)
    {
        var username = request.Username?.Trim() ?? string.Empty;
        var email = request.Email?.Trim().ToLowerInvariant() ?? string.Empty;
        var password = request.Password ?? string.Empty;

        if (string.IsNullOrWhiteSpace(username) ||
            string.IsNullOrWhiteSpace(email) ||
            string.IsNullOrWhiteSpace(password))
        {
            return BadRequest(new
            {
                message = "Username, email and password are required."
            });
        }

        if (password.Length < 8)
        {
            return BadRequest(new
            {
                message = "Password must be at least 8 characters long."
            });
        }

        var alreadyExists = await _db.Users
            .AnyAsync(u => u.Email == email);

        if (alreadyExists)
        {
            return Conflict(new
            {
                message = "User with this email already exists."
            });
        }

        var user = new User
        {
            Username = username,
            Email = email
        };

        user.PasswordHash = _passwordHasher.HashPassword(
            user,
            password);

        _db.Users.Add(user);

        try
        {
            await _db.SaveChangesAsync();
        }
        catch (DbUpdateException ex) when (ex.IsUniqueConstraintViolation())
        {
            return Conflict(new
            {
                message = "User with this email already exists."
            });
        }

        return Created(
            "/api/users/me",
            new
            {
                user.Id,
                user.Username,
                user.Email,
                user.CreatedAt
            });
    }

    [HttpPost("login")]
    public async Task<ActionResult<AuthResponse>> Login(LoginRequest request)
    {
        var email = request.Email?.Trim().ToLowerInvariant() ?? string.Empty;
        var password = request.Password ?? string.Empty;

        if (string.IsNullOrWhiteSpace(email) || string.IsNullOrWhiteSpace(password))
        {
            return Unauthorized(new
            {
                message = "Invalid email or password."
            });
        }

        var user = await _db.Users
            .SingleOrDefaultAsync(u => u.Email == email);

        if (user is null)
        {
            return Unauthorized(new
            {
                message = "Invalid email or password."
            });
        }

        var result = _passwordHasher.VerifyHashedPassword(
            user,
            user.PasswordHash,
            password);

        if (result == PasswordVerificationResult.Failed)
        {
            return Unauthorized(new
            {
                message = "Invalid email or password."
            });
        }

        var token = _jwtService.GenerateToken(user);

        return Ok(new AuthResponse
        {
            Token = token,
            UserId = user.Id,
            Username = user.Username,
            Email = user.Email
        });
    }
}
