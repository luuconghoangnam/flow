using System.Text.Json;
using Flow.Integration;
using Xunit;

namespace Flow.Core.Tests;

public class IntegrationTests
{
    [Fact]
    public void TestPolymorphicDeserialization_HttpCredential()
    {
        string json = """
        {
            "type": "http",
            "link": "https://example.com/file.zip",
            "headers": {
                "User-Agent": "Mozilla/5.0",
                "Cookie": "session=123"
            },
            "downloadPage": "https://example.com/",
            "suggestedName": "file.zip"
        }
        """;

        var credential = JsonSerializer.Deserialize<IDownloadCredentialsFromIntegration>(json);

        Assert.NotNull(credential);
        var httpCred = Assert.IsType<HttpDownloadCredentialsFromIntegration>(credential);
        Assert.Equal("https://example.com/file.zip", httpCred.Link);
        Assert.Equal("file.zip", httpCred.SuggestedName);
        Assert.Equal("https://example.com/", httpCred.DownloadPage);
        Assert.NotNull(httpCred.Headers);
        Assert.Equal("Mozilla/5.0", httpCred.Headers["User-Agent"]);
        Assert.Equal("session=123", httpCred.Headers["Cookie"]);
    }

    [Fact]
    public void TestPolymorphicDeserialization_HlsCredential()
    {
        string json = """
        {
            "type": "hls",
            "link": "https://example.com/video.m3u8",
            "headers": {
                "Authorization": "Bearer token123"
            },
            "suggestedName": "video.ts"
        }
        """;

        var credential = JsonSerializer.Deserialize<IDownloadCredentialsFromIntegration>(json);

        Assert.NotNull(credential);
        var hlsCred = Assert.IsType<HlsDownloadCredentialsFromIntegration>(credential);
        Assert.Equal("https://example.com/video.m3u8", hlsCred.Link);
        Assert.Equal("video.ts", hlsCred.SuggestedName);
        Assert.Null(hlsCred.DownloadPage);
        Assert.NotNull(hlsCred.Headers);
        Assert.Equal("Bearer token123", hlsCred.Headers["Authorization"]);
    }

    [Fact]
    public void TestAddDownloadsFromIntegration_CreateFromRequest_ModernPayload()
    {
        string json = """
        {
            "items": [
                {
                    "type": "http",
                    "link": "https://example.com/file.zip",
                    "suggestedName": "file.zip"
                }
            ],
            "options": {
                "silentAdd": true,
                "silentStart": false
            }
        }
        """;

        var request = AddDownloadsFromIntegration.CreateFromRequest(json);

        Assert.NotNull(request);
        Assert.Single(request.Items);
        Assert.True(request.Options.SilentAdd);
        Assert.False(request.Options.SilentStart);
        
        var httpCred = Assert.IsType<HttpDownloadCredentialsFromIntegration>(request.Items[0]);
        Assert.Equal("https://example.com/file.zip", httpCred.Link);
    }

    [Fact]
    public void TestAddDownloadsFromIntegration_CreateFromRequest_LegacyFallbackPayload()
    {
        // Legacy payloads sent by older versions are just a JSON array of HTTP download items
        string json = """
        [
            {
                "link": "https://example.com/file1.zip",
                "suggestedName": "file1.zip"
            },
            {
                "link": "https://example.com/file2.zip",
                "suggestedName": "file2.zip"
            }
        ]
        """;

        var request = AddDownloadsFromIntegration.CreateFromRequest(json);

        Assert.NotNull(request);
        Assert.Equal(2, request.Items.Count);
        Assert.False(request.Options.SilentAdd); // Defaulted
        Assert.False(request.Options.SilentStart); // Defaulted

        var first = Assert.IsType<HttpDownloadCredentialsFromIntegration>(request.Items[0]);
        Assert.Equal("https://example.com/file1.zip", first.Link);
        Assert.Equal("file1.zip", first.SuggestedName);

        var second = Assert.IsType<HttpDownloadCredentialsFromIntegration>(request.Items[1]);
        Assert.Equal("https://example.com/file2.zip", second.Link);
        Assert.Equal("file2.zip", second.SuggestedName);
    }
}
