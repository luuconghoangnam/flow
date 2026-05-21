using System.IO;

namespace Flow.Monitor;

public interface IDownloadItemState
{
    long Id { get; }
    string Folder { get; }
    string Name { get; }
    long ContentLength { get; }
    string SaveLocation { get; }
    long DateAdded { get; }
    long StartTime { get; }
    long CompleteTime { get; }
    string DownloadLink { get; }

    string GetFullPath() => Path.Combine(Folder, Name);
}
