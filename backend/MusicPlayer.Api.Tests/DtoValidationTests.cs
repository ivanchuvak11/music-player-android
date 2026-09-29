using System.ComponentModel.DataAnnotations;
using MusicPlayer.Api.DTOs.Auth;
using MusicPlayer.Api.DTOs.Playlists;

namespace MusicPlayer.Api.Tests;

public class DtoValidationTests
{
    [Fact]
    public void Register_rejects_invalid_email_and_short_password()
    {
        var request = new RegisterRequest
        {
            Username = "ivan",
            Email = "not-an-email",
            Password = "short"
        };

        var results = Validate(request);

        Assert.Contains(results, result =>
            result.MemberNames.Contains(nameof(RegisterRequest.Email)));
        Assert.Contains(results, result =>
            result.MemberNames.Contains(nameof(RegisterRequest.Password)));
    }

    [Fact]
    public void Playlist_track_rejects_negative_duration_and_oversized_title()
    {
        var request = new AddTrackRequest
        {
            Source = "jamendo",
            ExternalId = "123",
            Title = new string('x', 501),
            Artist = "Artist",
            DurationMs = -1
        };

        var results = Validate(request);

        Assert.Contains(results, result =>
            result.MemberNames.Contains(nameof(AddTrackRequest.Title)));
        Assert.Contains(results, result =>
            result.MemberNames.Contains(nameof(AddTrackRequest.DurationMs)));
    }

    private static List<ValidationResult> Validate(object instance)
    {
        var results = new List<ValidationResult>();
        Validator.TryValidateObject(
            instance,
            new ValidationContext(instance),
            results,
            validateAllProperties: true);
        return results;
    }
}
