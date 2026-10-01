using System.Net;
using MusicPlayer.Api.Infrastructure;

namespace MusicPlayer.Api.Tests;

public class NetscapeCookieParserTests
{
    [Fact]
    public void Parse_valid_netscape_cookies_returns_cookie_list()
    {
        var raw = """
            # Netscape HTTP Cookie File
            # This is a generated file! Do not edit.

            .youtube.com	TRUE	/	TRUE	1790808350	VISITOR_INFO1_LIVE	test_visitor_val
            #HttpOnly_.youtube.com	TRUE	/	TRUE	1790808350	LOGIN_INFO	test_login_val
            .google.com	TRUE	/	FALSE	1790808350	SID	test_sid_val
            """;

        var cookies = NetscapeCookieParser.Parse(raw);

        Assert.Equal(3, cookies.Count);

        var c1 = cookies.Find(c => c.Name == "VISITOR_INFO1_LIVE");
        Assert.NotNull(c1);
        Assert.Equal("test_visitor_val", c1.Value);
        Assert.Equal("youtube.com", c1.Domain);
        Assert.True(c1.Secure);

        var c2 = cookies.Find(c => c.Name == "LOGIN_INFO");
        Assert.NotNull(c2);
        Assert.Equal("test_login_val", c2.Value);
        Assert.Equal("youtube.com", c2.Domain);

        var c3 = cookies.Find(c => c.Name == "SID");
        Assert.NotNull(c3);
        Assert.Equal("test_sid_val", c3.Value);
        Assert.False(c3.Secure);
    }

    [Fact]
    public void Parse_empty_or_null_returns_empty_list()
    {
        Assert.Empty(NetscapeCookieParser.Parse(string.Empty));
        Assert.Empty(NetscapeCookieParser.Parse("   "));
    }

    [Fact]
    public void CookieContainer_Matches_Www_YouTube_Com()
    {
        var raw = """
            .youtube.com	TRUE	/	TRUE	1790808350	VISITOR_INFO1_LIVE	test_visitor_val
            """;
        var cookies = NetscapeCookieParser.Parse(raw);
        var container = new CookieContainer();
        foreach (var c in cookies)
            container.Add(c);

        var matched = container.GetCookies(new Uri("https://www.youtube.com/youtubei/v1/player"));
        Assert.Single(matched);
    }
}
