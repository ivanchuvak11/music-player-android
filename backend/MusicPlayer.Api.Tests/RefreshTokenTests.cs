using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using MusicPlayer.Api.Controllers;
using MusicPlayer.Api.Data;
using MusicPlayer.Api.DTOs.Auth;
using MusicPlayer.Api.Models;
using MusicPlayer.Api.Services;

namespace MusicPlayer.Api.Tests;

public class RefreshTokenTests
{
    private readonly IConfiguration _config;
    private readonly JwtService _jwtService;

    public RefreshTokenTests()
    {
        _config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Jwt:Key"] = "test-secret-key-at-least-32-characters-long!",
                ["Jwt:Issuer"] = "MusicPlayer.Api",
                ["Jwt:Audience"] = "MusicPlayer.Android"
            })
            .Build();

        _jwtService = new JwtService(_config);
    }

    private AppDbContext CreateDbContext(string dbName)
    {
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase(databaseName: dbName)
            .Options;

        return new AppDbContext(options);
    }

    [Fact]
    public async Task Login_creates_and_returns_access_and_refresh_tokens()
    {
        using var db = CreateDbContext(Guid.NewGuid().ToString());
        var hasher = new PasswordHasher<User>();
        var user = new User
        {
            Username = "testuser",
            Email = "test@example.com"
        };
        user.PasswordHash = hasher.HashPassword(user, "password123");
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var controller = new AuthController(db, _jwtService);
        var result = await controller.Login(new LoginRequest
        {
            Email = "test@example.com",
            Password = "password123"
        });

        var okResult = Assert.IsType<OkObjectResult>(result.Result);
        var response = Assert.IsType<AuthResponse>(okResult.Value);

        Assert.False(string.IsNullOrWhiteSpace(response.Token));
        Assert.False(string.IsNullOrWhiteSpace(response.AccessToken));
        Assert.False(string.IsNullOrWhiteSpace(response.RefreshToken));
        Assert.Equal(7200, response.ExpiresIn);

        var savedToken = await db.RefreshTokens.SingleOrDefaultAsync(r => r.Token == response.RefreshToken);
        Assert.NotNull(savedToken);
        Assert.True(savedToken.IsActive);
        Assert.Equal(user.Id, savedToken.UserId);
    }

    [Fact]
    public async Task Refresh_with_valid_token_rotates_and_invalidates_previous_token()
    {
        using var db = CreateDbContext(Guid.NewGuid().ToString());
        var user = new User
        {
            Username = "testuser",
            Email = "test@example.com",
            PasswordHash = "hash"
        };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var initialRefreshToken = _jwtService.GenerateRefreshToken();
        db.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            Token = initialRefreshToken,
            ExpiresAt = DateTime.UtcNow.AddDays(30)
        });
        await db.SaveChangesAsync();

        var controller = new AuthController(db, _jwtService);
        var refreshResult = await controller.Refresh(new RefreshTokenRequest
        {
            RefreshToken = initialRefreshToken
        });

        var okResult = Assert.IsType<OkObjectResult>(refreshResult.Result);
        var response = Assert.IsType<AuthResponse>(okResult.Value);

        Assert.False(string.IsNullOrWhiteSpace(response.Token));
        Assert.False(string.IsNullOrWhiteSpace(response.RefreshToken));
        Assert.NotEqual(initialRefreshToken, response.RefreshToken);

        // Previous token must be revoked
        var oldToken = await db.RefreshTokens.SingleAsync(r => r.Token == initialRefreshToken);
        Assert.NotNull(oldToken.RevokedAt);
        Assert.False(oldToken.IsActive);

        // Replay attack: trying to use the old token again must be rejected with 401
        var replayResult = await controller.Refresh(new RefreshTokenRequest
        {
            RefreshToken = initialRefreshToken
        });
        Assert.IsType<UnauthorizedObjectResult>(replayResult.Result);
    }

    [Fact]
    public async Task Revoke_invalidates_refresh_token()
    {
        using var db = CreateDbContext(Guid.NewGuid().ToString());
        var user = new User
        {
            Username = "testuser",
            Email = "test@example.com",
            PasswordHash = "hash"
        };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var tokenString = _jwtService.GenerateRefreshToken();
        db.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            Token = tokenString,
            ExpiresAt = DateTime.UtcNow.AddDays(30)
        });
        await db.SaveChangesAsync();

        var controller = new AuthController(db, _jwtService);
        var revokeResult = await controller.Revoke(new RefreshTokenRequest
        {
            RefreshToken = tokenString
        });

        Assert.IsType<OkObjectResult>(revokeResult);

        var tokenInDb = await db.RefreshTokens.SingleAsync(r => r.Token == tokenString);
        Assert.NotNull(tokenInDb.RevokedAt);
        Assert.False(tokenInDb.IsActive);
    }

    [Fact]
    public void GenerateRefreshToken_produces_unique_high_entropy_tokens()
    {
        var token1 = _jwtService.GenerateRefreshToken();
        var token2 = _jwtService.GenerateRefreshToken();

        Assert.False(string.IsNullOrWhiteSpace(token1));
        Assert.False(string.IsNullOrWhiteSpace(token2));
        Assert.NotEqual(token1, token2);
        Assert.True(token1.Length >= 40);
    }
}
