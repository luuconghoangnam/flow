using System;

namespace Flow.Core.Models;

public interface IDownloadPart
{
    long Current { get; set; }
    PartDownloadStatus Status { get; set; }
    bool IsCompleted { get; }
    int? Percent { get; }
    
    long HowMuchProceed();
    void ResetCurrent();
    long GetId();

    event Action<PartDownloadStatus>? StatusChanged;
}
