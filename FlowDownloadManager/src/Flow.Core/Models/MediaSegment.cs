using System;
using System.Text.Json.Serialization;

namespace Flow.Core.Models;

public class MediaSegment : IDownloadPart
{
    private long _current = 0;
    private bool _isCompleted = false;
    private PartDownloadStatus _status = PartDownloadStatus.Idle;

    public long SegmentIndex { get; set; }
    public string Link { get; set; } = string.Empty;
    public double Duration { get; set; }
    public long? Length { get; set; }

    public long Current
    {
        get => _current;
        set => _current = value;
    }

    public bool IsCompleted
    {
        get => _isCompleted;
        set => _isCompleted = value;
    }

    [JsonIgnore]
    public PartDownloadStatus Status
    {
        get => _status;
        set
        {
            if (_status != value)
            {
                _status = value;
                StatusChanged?.Invoke(value);
            }
        }
    }

    [JsonIgnore]
    public int? Percent
    {
        get
        {
            if (!Length.HasValue || Length.Value <= 0) return null;
            return (int)((double)Current / Length.Value * 100);
        }
    }

    public MediaSegment()
    {
    }

    public MediaSegment(long segmentIndex, string link, double duration, bool isCompleted = false, long? length = null, long current = 0)
    {
        SegmentIndex = segmentIndex;
        Link = link;
        Duration = duration;
        _isCompleted = isCompleted;
        Length = length;
        _current = current;
    }

    public long HowMuchProceed() => Current;

    public void ResetCurrent() => Current = 0;

    public long GetId() => SegmentIndex;

    public event Action<PartDownloadStatus>? StatusChanged;
}
