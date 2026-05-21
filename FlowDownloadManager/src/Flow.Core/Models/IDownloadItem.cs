using System.Text.Json.Serialization;

namespace Flow.Core.Models;

[JsonPolymorphic(TypeDiscriminatorPropertyName = "type")]
[JsonDerivedType(typeof(HttpDownloadItem), "http")]
public interface IDownloadItem : IDownloadCredentials
{
    public const long LengthUnknown = -1L;

    long Id { get; set; }
    string Folder { get; set; }
    string Name { get; set; }
    long ContentLength { get; set; }
    long DateAdded { get; set; }
    long? StartTime { get; set; }
    long? CompleteTime { get; set; }
    DownloadStatus Status { get; set; }
    int? PreferredConnectionCount { get; set; }
    long SpeedLimit { get; set; }
    string? FileChecksum { get; set; }

    void ValidateItem();
}
