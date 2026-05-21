using System;

namespace Flow.Core.Models;

public enum PartDownloadState
{
    Idle,
    Canceled,
    Completed,
    Connecting,
    ReceivingData
}

public class PartDownloadStatus
{
    public PartDownloadState State { get; }
    public Exception? Error { get; }

    public bool IsActive => State == PartDownloadState.Connecting || State == PartDownloadState.ReceivingData;
    public bool IsInactive => !IsActive;

    public PartDownloadStatus(PartDownloadState state, Exception? error = null)
    {
        State = state;
        Error = error;
    }

    public static readonly PartDownloadStatus Idle = new(PartDownloadState.Idle);
    public static readonly PartDownloadStatus Completed = new(PartDownloadState.Completed);
    public static readonly PartDownloadStatus Connecting = new(PartDownloadState.Connecting);
    public static readonly PartDownloadStatus ReceivingData = new(PartDownloadState.ReceivingData);

    public static PartDownloadStatus FromCanceled(Exception error) => new(PartDownloadState.Canceled, error);

    public override string ToString() => State.ToString();
}
