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
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<RefreshToken>(entity =>
        {
            entity.HasIndex(r => r.Token)
                .IsUnique();

            entity.Property(r => r.Token)
                .HasMaxLength(256);

            entity.HasOne(r => r.User)
                .WithMany(u => u.RefreshTokens)
                .HasForeignKey(r => r.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // Email повинен бути унікальним
        modelBuilder.Entity<User>()
            .HasIndex(u => u.Email)
            .IsUnique();

        modelBuilder.Entity<User>()
            .Property(u => u.Username)
            .HasMaxLength(100);

        modelBuilder.Entity<User>()
            .Property(u => u.Email)
            .HasMaxLength(320);

        modelBuilder.Entity<User>()
            .Property(u => u.PasswordHash)
            .HasMaxLength(1024);

        modelBuilder.Entity<Playlist>()
            .Property(p => p.Name)
            .HasMaxLength(200);

        modelBuilder.Entity<PlaylistTrack>()
            .Property(t => t.Source)
            .HasMaxLength(32);

        modelBuilder.Entity<PlaylistTrack>()
            .Property(t => t.ExternalId)
            .HasMaxLength(256);

        modelBuilder.Entity<PlaylistTrack>()
            .Property(t => t.Title)
            .HasMaxLength(500);

        modelBuilder.Entity<PlaylistTrack>()
            .Property(t => t.Artist)
            .HasMaxLength(300);

        modelBuilder.Entity<PlaylistTrack>()
            .Property(t => t.ArtworkUrl)
            .HasMaxLength(2048);

        modelBuilder.Entity<FavoriteTrack>()
            .Property(f => f.Source)
            .HasMaxLength(32);

        modelBuilder.Entity<FavoriteTrack>()
            .Property(f => f.ExternalId)
            .HasMaxLength(256);

        modelBuilder.Entity<FavoriteTrack>()
            .Property(f => f.Title)
            .HasMaxLength(500);

        modelBuilder.Entity<FavoriteTrack>()
            .Property(f => f.Artist)
            .HasMaxLength(300);

        modelBuilder.Entity<FavoriteTrack>()
            .Property(f => f.ArtworkUrl)
            .HasMaxLength(2048);

        modelBuilder.Entity<FavoriteRadioStation>()
            .Property(r => r.StationId)
            .HasMaxLength(128);

        modelBuilder.Entity<FavoriteRadioStation>()
            .Property(r => r.Name)
            .HasMaxLength(500);

        modelBuilder.Entity<FavoriteRadioStation>()
            .Property(r => r.StreamUrl)
            .HasMaxLength(2048);

        modelBuilder.Entity<FavoriteRadioStation>()
            .Property(r => r.LogoUrl)
            .HasMaxLength(2048);

        modelBuilder.Entity<FavoriteRadioStation>()
            .Property(r => r.Country)
            .HasMaxLength(128);

        modelBuilder.Entity<FavoriteRadioStation>()
            .Property(r => r.Genre)
            .HasMaxLength(500);

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
