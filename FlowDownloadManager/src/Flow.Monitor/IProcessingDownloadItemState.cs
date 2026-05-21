using System.Collections.Generic;
using Flow.Core.Models;

namespace Flow.Monitor;

public interface IProcessingDownloadItemState : IDownloadItemState
{
    DownloadJobStatus Status { get; }
    long Speed { get; }
    bool? SupportResume { get; }
    IReadOnlyList<IUiPart> Parts { get; }

    bool GotAnyProgress { get; }
    long Progress { get; }
    bool HasProgress { get; }
    int? Percent { get; } // 0..100

    // remaining time in seconds
    long? RemainingTime { get; }

    bool IsWaiting { get; }

    bool CanBePaused() => IsWaiting || Status.IsActive;
    bool CanBeResumed() => Status.CanBeResumed && !IsWaiting;
}
