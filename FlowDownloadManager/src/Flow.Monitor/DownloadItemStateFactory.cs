using Flow.Core.Models;

namespace Flow.Monitor;

public class ProcessingDownloadItemFactoryInputs<TDownloadJob> where TDownloadJob : DownloadJob
{
    public TDownloadJob DownloadJob { get; }
    public long Speed { get; }
    public bool IsWaiting { get; }

    public ProcessingDownloadItemFactoryInputs(TDownloadJob downloadJob, long speed, bool isWaiting)
    {
        DownloadJob = downloadJob;
        Speed = speed;
        IsWaiting = isWaiting;
    }
}

public interface IDownloadItemStateFactory<TDownloadItem, TDownloadJob>
    where TDownloadItem : IDownloadItem
    where TDownloadJob : DownloadJob
{
    IProcessingDownloadItemState CreateProcessingDownloadItemState(
        ProcessingDownloadItemFactoryInputs<TDownloadJob> props);

    CompletedDownloadItemState CreateCompletedDownloadItemState(
        TDownloadItem downloadItem);
}
