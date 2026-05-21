using Flow.Core.Models;

namespace Flow.Monitor;

public class CompletedDownloadItemState : IDownloadItemState
{
    public long Id { get; }
    public string Folder { get; }
    public string Name { get; }
    public string DownloadLink { get; }
    public long ContentLength { get; }
    public string SaveLocation { get; }
    public long DateAdded { get; }
    public long StartTime { get; }
    public long CompleteTime { get; }

    public CompletedDownloadItemState(
        long id,
        string folder,
        string name,
        string downloadLink,
        long contentLength,
        string saveLocation,
        long dateAdded,
        long startTime,
        long completeTime)
    {
        Id = id;
        Folder = folder;
        Name = name;
        DownloadLink = downloadLink;
        ContentLength = contentLength;
        SaveLocation = saveLocation;
        DateAdded = dateAdded;
        StartTime = startTime;
        CompleteTime = completeTime;
    }

    public static CompletedDownloadItemState FromDownloadItem(IDownloadItem item)
    {
        return new CompletedDownloadItemState(
            id: item.Id,
            folder: item.Folder,
            name: item.Name,
            downloadLink: item.Link,
            contentLength: item.ContentLength,
            saveLocation: item.Name,
            dateAdded: item.DateAdded,
            startTime: item.StartTime ?? -1,
            completeTime: item.CompleteTime ?? -1
        );
    }
}
