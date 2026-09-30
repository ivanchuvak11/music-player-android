using System.Net;

namespace MusicPlayer.Api.Infrastructure;

public static class NetscapeCookieParser
{
    /// <summary>
    /// Parses a Netscape/Mozilla cookies.txt formatted string into a collection of System.Net.Cookie objects.
    /// Supports standard cookies.txt exports from browser extensions (including #HttpOnly_ prefixes).
    /// </summary>
    public static List<Cookie> Parse(string content)
    {
        var cookies = new List<Cookie>();
        if (string.IsNullOrWhiteSpace(content))
            return cookies;

        using var reader = new StringReader(content);
        string? line;

        while ((line = reader.ReadLine()) != null)
        {
            line = line.Trim();
            if (string.IsNullOrWhiteSpace(line))
                continue;

            // Handle #HttpOnly_ prefix used by Netscape export tools
            if (line.StartsWith("#HttpOnly_", StringComparison.OrdinalIgnoreCase))
            {
                line = line["#HttpOnly_".Length..];
            }
            else if (line.StartsWith('#'))
            {
                continue;
            }

            var parts = line.Split('\t');
            if (parts.Length < 7)
                continue;

            var rawDomain = parts[0].Trim();
            var path = string.IsNullOrWhiteSpace(parts[2]) ? "/" : parts[2].Trim();
            var isSecure = string.Equals(parts[3].Trim(), "TRUE", StringComparison.OrdinalIgnoreCase);
            var name = parts[5].Trim();
            var value = parts[6].Trim();

            if (string.IsNullOrWhiteSpace(name))
                continue;

            try
            {
                var cleanDomain = rawDomain.TrimStart('.');
                var cookie = new Cookie(name, value, path, cleanDomain)
                {
                    Secure = isSecure
                };
                cookies.Add(cookie);
            }
            catch
            {
                // Ignore individual invalid or malformed cookies
            }
        }

        return cookies;
    }
}
