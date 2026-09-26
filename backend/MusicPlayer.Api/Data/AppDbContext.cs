using Microsoft.EntityFrameworkCore;
using MusicPlayer.Api.Models;

namespace MusicPlayer.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options)
        : base(options)
    {
    }
    public DbSet<User> Users => Set<User>();
}