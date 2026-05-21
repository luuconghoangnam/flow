using System.Collections.Generic;
using Flow.Core.Models;

namespace Flow.Monitor;

public class DurationBasedProcessingDownloadItemState : IProcessingDownloadItemState
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
    
    public DownloadJobStatus Status { get; }
    public long Speed { get; }
    public IReadOnlyList<IUiPart> Parts { get; }
    public bool? SupportResume { get; }
    public bool IsWaiting { get; }

    public long OptimisticLength { get; }
    public double? Duration { get; }
    public long Progress { get; }
    public int? Percent { get; }
    
    public bool HasProgress => Progress > 0;
    public bool GotAnyProgress => Progress > 0;
    public long? RemainingTime { get; }

    public DurationBasedProcessingDownloadItemState(
        long id,
        string folder,
        string name,
        string downloadLink,
        long contentLength,
        string saveLocation,
        long dateAdded,
        long startTime,
        long completeTime,
        DownloadJobStatus status,
        long speed,
        IReadOnlyList<IUiPart> parts,
        bool? supportResume,
        long optimisticLength,
        double? duration,
        long progress,
        int percent,
        bool isWaiting)
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
        Status = status;
        Speed = speed;
        Parts = parts;
        SupportResume = supportResume;
        IsWaiting = isWaiting;
        OptimisticLength = optimisticLength;
        Duration = duration;
        Progress = progress;
        Percent = percent;

        long length = GetLengthOrOptimistic(contentLength, optimisticLength);
        if (length <= 0 || speed <= 0)
        {
            RemainingTime = null;
        }
        else
        {
            RemainingTime = (length - Progress) / speed;
        }
    }

    private static long GetLengthOrOptimistic(long exactLength, long optimisticLength)
    {
        if (exactLength > 0) return exactLength;
        if (optimisticLength > 0) return optimisticLength;
        return -1;
    }
}
