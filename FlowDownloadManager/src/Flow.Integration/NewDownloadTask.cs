using System.Text.Json.Serialization;

namespace Flow.Integration;

public class NewDownloadTask
{
    [JsonPropertyName("downloadSource")]
    public IDownloadCredentialsFromIntegration DownloadSource { get; set; } = null!;

    [JsonPropertyName("folder")]
    public string? Folder { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("queueId")]
    public long? QueueId { get; set; }
}
