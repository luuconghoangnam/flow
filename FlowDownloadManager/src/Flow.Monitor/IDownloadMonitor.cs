using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace Flow.Monitor;

public interface IDownloadMonitor
{
    bool UseAverageSpeed { get; set; }
    IReadOnlyList<IProcessingDownloadItemState> ActiveDownloadList { get; }
    IReadOnlyList<CompletedDownloadItemState> CompletedDownloadList { get; }
    IReadOnlyList<IDownloadItemState> DownloadList { get; }
    int ActiveDownloadCount { get; }

    event EventHandler? OnActiveDownloadListChanged;
    event EventHandler? OnCompletedDownloadListChanged;
    event EventHandler? OnDownloadListChanged;
    event EventHandler? OnActiveDownloadCountChanged;

    Task WaitForDownloadToFinishOrCancelAsync(long id);
}

public static class DownloadMonitorExtensions
{
    public static bool IsDownloadActive(this IDownloadMonitor monitor, long downloadId)
    {
        foreach (var item in monitor.ActiveDownloadList)
        {
            if (item.Id == downloadId)
            {
                return item.CanBePaused();
            }
        }
        return false;
    }
}
