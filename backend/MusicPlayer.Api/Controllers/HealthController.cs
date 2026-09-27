using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Data;

namespace MusicPlayer.Api.Controllers;

[ApiController]
[Route("api/health")]
public class HealthController : ControllerBase
{
    private readonly AppDbContext _db;

    public HealthController(AppDbContext db)
    {
        _db = db;
    }

    [HttpGet]
    public async Task<IActionResult> Get(CancellationToken cancellationToken)
    {
        var databaseAvailable =
            await _db.Database.CanConnectAsync(cancellationToken);

        if (!databaseAvailable)
        {
            return StatusCode(
                StatusCodes.Status503ServiceUnavailable,
                new
                {
                    status = "unhealthy",
                    database = "unavailable"
                });
        }

        return Ok(new
        {
            status = "healthy",
            database = "available"
        });
    }
}
