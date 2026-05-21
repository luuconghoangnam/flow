using System;
using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core;

public abstract record DownloadManagerEvent(IDownloadItem DownloadItem, DownloadItemContext Context);

public record JobAddedEvent(IDownloadItem DownloadItem, DownloadItemContext Context) : DownloadManagerEvent(DownloadItem, Context);
public record JobChangedEvent(IDownloadItem DownloadItem, DownloadItemContext Context) : DownloadManagerEvent(DownloadItem, Context);
public record JobStartingEvent(IDownloadItem DownloadItem, DownloadItemContext Context) : DownloadManagerEvent(DownloadItem, Context);
public record JobStartedEvent(IDownloadItem DownloadItem, DownloadItemContext Context) : DownloadManagerEvent(DownloadItem, Context);
public record JobCompletedEvent(IDownloadItem DownloadItem, DownloadItemContext Context) : DownloadManagerEvent(DownloadItem, Context);
public record JobCanceledEvent(IDownloadItem DownloadItem, DownloadItemContext Context, Exception Exception) : DownloadManagerEvent(DownloadItem, Context);
public record JobRemovedEvent(IDownloadItem DownloadItem, DownloadItemContext Context) : DownloadManagerEvent(DownloadItem, Context);

public interface IDownloadManagerMinimalControl
{
    Task StartJobAsync(long id, DownloadItemContext? context = null);
    Task StopJobAsync(long id, DownloadItemContext? context = null);
    bool CanActivateJob(long id);
    event EventHandler<DownloadManagerEvent>? OnJobEvent;
}
