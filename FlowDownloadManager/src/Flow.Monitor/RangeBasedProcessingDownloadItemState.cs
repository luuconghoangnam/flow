using System.Collections.Generic;
using System.Linq;
using Flow.Core.Models;

namespace Flow.Monitor;

public class RangeBasedProcessingDownloadItemState : IProcessingDownloadItemState
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

    public long Progress { get; }
    public bool HasProgress => Progress > 0;
    public bool GotAnyProgress => Progress > 0;
    
    public int? Percent { get; }
    public long? RemainingTime { get; }

    public RangeBasedProcessingDownloadItemState(
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

        Progress = parts.Sum(p => p.HowMuchProceed);
        
        if (contentLength == IDownloadItem.LengthUnknown)
        {
            Percent = null;
        }
        else
        {
            Percent = contentLength <= 0 ? 0 : (int)((double)Progress / contentLength * 100);
        }

        if (contentLength <= 0 || speed <= 0)
        {
            RemainingTime = null;
        }
        else
        {
            RemainingTime = (contentLength - Progress) / speed;
        }
    }
}
