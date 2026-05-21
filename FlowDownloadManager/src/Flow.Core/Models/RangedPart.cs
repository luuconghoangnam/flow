using System;
using System.Text.Json.Serialization;

namespace Flow.Core.Models;

public class RangedPart : IDownloadPart
{
    private long _current;
    private PartDownloadStatus _status = PartDownloadStatus.Idle;

    public long From { get; set; }
    public long? To { get; set; }

    public long Current
    {
        get => _current;
        set
        {
            _current = value;
        }
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
    public bool IsCompleted => To.HasValue ? Current == To.Value + 1 : false;

    [JsonIgnore]
    public int? Percent
    {
        get
        {
            long? len = PartLength;
            if (!len.HasValue || len.Value <= 0) return null;
            return (int)((double)HowMuchProceed() / len.Value * 100);
        }
    }

    [JsonIgnore]
    public long? RemainingLength => To.HasValue ? (To.Value - Current) + 1 : null;

    [JsonIgnore]
    public long? PartLength => To.HasValue ? (To.Value - From) + 1 : null;

    [JsonIgnore]
    public bool IsBlind => !To.HasValue;

    public RangedPart()
    {
        _current = From;
    }

    public RangedPart(long from, long? to, long? current = null)
    {
        From = from;
        To = to;
        _current = current ?? from;
    }

    public long HowMuchProceed() => Current - From;

    public void ResetCurrent() => Current = From;

    public long GetId() => From;

    public void SetBlindAsCompleted()
    {
        To = Current - 1;
    }

    public event Action<PartDownloadStatus>? StatusChanged;
}
