using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace Flow.Integration;

[JsonPolymorphic(TypeDiscriminatorPropertyName = "type")]
[JsonDerivedType(typeof(HttpDownloadCredentialsFromIntegration), "http")]
[JsonDerivedType(typeof(HlsDownloadCredentialsFromIntegration), "hls")]
public interface IDownloadCredentialsFromIntegration
{
    [JsonPropertyName("link")]
    string Link { get; }

    [JsonPropertyName("downloadPage")]
    string? DownloadPage { get; }

    [JsonPropertyName("suggestedName")]
    string? SuggestedName { get; }
}

public class HttpDownloadCredentialsFromIntegration : IDownloadCredentialsFromIntegration
{
    [JsonPropertyName("link")]
    public string Link { get; set; } = string.Empty;

    [JsonPropertyName("headers")]
    public Dictionary<string, string>? Headers { get; set; }

    [JsonPropertyName("downloadPage")]
    public string? DownloadPage { get; set; }

    [JsonPropertyName("suggestedName")]
    public string? SuggestedName { get; set; }
}

public class HlsDownloadCredentialsFromIntegration : IDownloadCredentialsFromIntegration
{
    [JsonPropertyName("link")]
    public string Link { get; set; } = string.Empty;

    [JsonPropertyName("headers")]
    public Dictionary<string, string>? Headers { get; set; }

    [JsonPropertyName("downloadPage")]
    public string? DownloadPage { get; set; }

    [JsonPropertyName("suggestedName")]
    public string? SuggestedName { get; set; }
}
