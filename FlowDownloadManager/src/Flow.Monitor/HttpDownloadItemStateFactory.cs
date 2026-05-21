using System.Linq;
using Flow.Core.Models;

namespace Flow.Monitor;

/// <summary>
/// Concrete factory that creates download item states for HTTP-based downloads.
/// Handles both RangedPart (HTTP) jobs and builds CompletedDownloadItemState from IDownloadItem.
/// </summary>
public class HttpDownloadItemStateFactory : IDownloadItemStateFactory<IDownloadItem, DownloadJob>
{
    public IProcessingDownloadItemState CreateProcessingDownloadItemState(
        ProcessingDownloadItemFactoryInputs<DownloadJob> props)
    {
        var job = props.DownloadJob;
        var item = job.DownloadItem;

        // If it's an HttpDownloadJob, use its active parts for segment visualization
        if (job is HttpDownloadJob httpJob)
        {
            var rawParts = httpJob.ActiveParts;
            var uiParts = rawParts
                .Select(p => (IUiPart)UiRangedPart.FromPart(p, item.ContentLength))
                .ToList();

            return new RangeBasedProcessingDownloadItemState(
                id: item.Id,
                folder: item.Folder,
                name: item.Name,
                downloadLink: item.Link,
                contentLength: item.ContentLength,
                saveLocation: item.Name,
                dateAdded: item.DateAdded,
                startTime: item.StartTime ?? -1,
                completeTime: item.CompleteTime ?? -1,
                status: job.Status,
                speed: props.Speed,
                parts: uiParts,
                supportResume: httpJob.SupportsConcurrent,
                isWaiting: props.IsWaiting
            );
        }

        // Fallback for unknown job types (no parts info)
        return new RangeBasedProcessingDownloadItemState(
            id: item.Id,
            folder: item.Folder,
            name: item.Name,
            downloadLink: item.Link,
            contentLength: item.ContentLength,
            saveLocation: item.Name,
            dateAdded: item.DateAdded,
            startTime: item.StartTime ?? -1,
            completeTime: item.CompleteTime ?? -1,
            status: job.Status,
            speed: props.Speed,
            parts: System.Array.Empty<IUiPart>(),
            supportResume: null,
            isWaiting: props.IsWaiting
        );
    }

    public CompletedDownloadItemState CreateCompletedDownloadItemState(IDownloadItem downloadItem)
    {
        return CompletedDownloadItemState.FromDownloadItem(downloadItem);
    }
}
