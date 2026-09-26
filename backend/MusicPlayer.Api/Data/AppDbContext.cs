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
    public DbSet<Playlist> Playlists => Set<Playlist>();
    public DbSet<PlaylistTrack> PlaylistTracks => Set<PlaylistTrack>();
    public DbSet<FavoriteTrack> FavoriteTracks => Set<FavoriteTrack>();
    public DbSet<FavoriteRadioStation> FavoriteRadioStations =>
        Set<FavoriteRadioStation>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // Email повинен бути унікальним
        modelBuilder.Entity<User>()
            .HasIndex(u => u.Email)
            .IsUnique();

        // User -> Playlists
        modelBuilder.Entity<Playlist>()
            .HasOne(p => p.User)
            .WithMany(u => u.Playlists)
            .HasForeignKey(p => p.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Playlist -> Tracks
        modelBuilder.Entity<PlaylistTrack>()
            .HasOne(t => t.Playlist)
            .WithMany(p => p.Tracks)
            .HasForeignKey(t => t.PlaylistId)
            .OnDelete(DeleteBehavior.Cascade);

        // User -> Favorite tracks
        modelBuilder.Entity<FavoriteTrack>()
            .HasOne(f => f.User)
            .WithMany(u => u.FavoriteTracks)
            .HasForeignKey(f => f.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Не дозволяємо один трек двічі додати в улюблені
        modelBuilder.Entity<FavoriteTrack>()
            .HasIndex(f => new
            {
                f.UserId,
                f.Source,
                f.ExternalId
            })
            .IsUnique();

        // User -> Favorite radio stations
        modelBuilder.Entity<FavoriteRadioStation>()
            .HasOne(r => r.User)
            .WithMany(u => u.FavoriteRadioStations)
            .HasForeignKey(r => r.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Одна станція не повинна двічі бути в улюблених
        modelBuilder.Entity<FavoriteRadioStation>()
            .HasIndex(r => new
            {
                r.UserId,
                r.StationId
            })
            .IsUnique();

        // Один трек не повинен двічі бути в одному плейлисті
        modelBuilder.Entity<PlaylistTrack>()
            .HasIndex(t => new
            {
                t.PlaylistId,
                t.Source,
                t.ExternalId
            })
            .IsUnique();
    }
}