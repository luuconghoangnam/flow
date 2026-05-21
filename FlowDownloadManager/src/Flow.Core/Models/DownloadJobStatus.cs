using System;

namespace Flow.Core.Models;

public enum DownloadJobState
{
    Idle,
    Resuming,
    Downloading,
    PreparingFile,
    Retrying,
    Canceled,
    Finished
}

public class DownloadJobStatus
{
    public DownloadJobState State { get; }
    public long? TimeUntilRetry { get; }
    public int? Percent { get; }
    public Exception? Error { get; }

    public int Order { get; }

    public bool IsActive => State == DownloadJobState.Downloading || 
                            State == DownloadJobState.Retrying || 
                            State == DownloadJobState.Resuming || 
                            State == DownloadJobState.PreparingFile;

    public bool CanBeResumed => State == DownloadJobState.Idle || 
                                State == DownloadJobState.Canceled;

    public DownloadJobStatus(DownloadJobState state, int order, long? timeUntilRetry = null, int? percent = null, Exception? error = null)
    {
        State = state;
        Order = order;
        TimeUntilRetry = timeUntilRetry;
        Percent = percent;
        Error = error;
    }

    public DownloadStatus AsDownloadStatus()
    {
        return State switch
        {
            DownloadJobState.Downloading => DownloadStatus.Downloading,
            DownloadJobState.Resuming => DownloadStatus.Downloading,
            DownloadJobState.PreparingFile => DownloadStatus.Downloading,
            DownloadJobState.Retrying => DownloadStatus.Paused,
            DownloadJobState.Finished => DownloadStatus.Completed,
            DownloadJobState.Idle => DownloadStatus.Added,
            DownloadJobState.Canceled => IsNormalCancellation(Error) ? DownloadStatus.Paused : DownloadStatus.Error,
            _ => DownloadStatus.Added
        };
    }

    private static bool IsNormalCancellation(Exception? e)
    {
        if (e == null) return true;
        if (e is OperationCanceledException) return true;
        return false;
    }

    public static readonly DownloadJobStatus Idle = new(DownloadJobState.Idle, 2);
    public static readonly DownloadJobStatus Downloading = new(DownloadJobState.Downloading, 0);
    public static readonly DownloadJobStatus Resuming = new(DownloadJobState.Resuming, 0);
    public static readonly DownloadJobStatus Finished = new(DownloadJobState.Finished, 3);

    public static DownloadJobStatus FromRetrying(long timeUntilRetry) => new(DownloadJobState.Retrying, 0, timeUntilRetry: timeUntilRetry);
    public static DownloadJobStatus FromPreparingFile(int? percent) => new(DownloadJobState.PreparingFile, 1, percent: percent);
    public static DownloadJobStatus FromCanceled(Exception e) => new(DownloadJobState.Canceled, 2, error: e);

    public override string ToString()
    {
        return State switch
        {
            DownloadJobState.Retrying => $"Retrying in {TimeUntilRetry}ms",
            DownloadJobState.PreparingFile => $"PreparingFile ({Percent}%)",
            DownloadJobState.Canceled => $"Canceled (Error: {Error?.Message})",
            _ => State.ToString()
        };
    }
}
