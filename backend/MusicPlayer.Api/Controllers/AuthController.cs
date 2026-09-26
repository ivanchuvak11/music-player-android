using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Auth;
using MusicPlayer.Api.Models;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
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
    [HttpPost("login")]
public async Task<ActionResult<AuthResponse>> Login(LoginRequest request)
{
    var email = request.Email.Trim().ToLowerInvariant();

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
        request.Password);

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