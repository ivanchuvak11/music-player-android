namespace MusicPlayer.Api.Infrastructure;

public sealed class ExternalServiceConfigurationException : Exception
{
    public ExternalServiceConfigurationException(string message)
        : base(message)
    {
    }
}
