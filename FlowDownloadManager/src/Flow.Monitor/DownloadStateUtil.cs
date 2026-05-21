using Flow.Core.Models;

namespace Flow.Monitor;

public static class DownloadStateUtil
{
    public static DownloadJobStatus StatusOrFinished(this IDownloadItemState state)
    {
        return (state as IProcessingDownloadItemState)?.Status ?? DownloadJobStatus.Finished;
    }

    public static bool IsFinished(this IDownloadItemState state)
    {
        return state is CompletedDownloadItemState;
    }

    public static bool IsNotFinished(this IDownloadItemState state)
    {
        return state is IProcessingDownloadItemState;
    }

    public static long? SpeedOrNull(this IDownloadItemState state)
    {
        return (state as IProcessingDownloadItemState)?.Speed;
    }

    public static long? RemainingOrNull(this IDownloadItemState state)
    {
        return (state as IProcessingDownloadItemState)?.RemainingTime;
    }
}
