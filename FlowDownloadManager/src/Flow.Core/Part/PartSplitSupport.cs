using System;
using Flow.Core.Models;

namespace Flow.Core.Part;

public class PartSplitSupport
{
    public const long SafeZoneSize = 128 * 8192; // 1MB

    private readonly object _partEndLock;
    private long _safeZone;

    public RangedPart Part { get; }

    public long SafeZone
    {
        get => _safeZone;
        private set => _safeZone = value;
    }

    public PartSplitSupport(RangedPart part, object? partEndLock = null)
    {
        Part = part ?? throw new ArgumentNullException(nameof(part));
        _partEndLock = partEndLock ?? new object();
        _safeZone = part.Current - 1;
    }

    public long RemainingSafe()
    {
        return Math.Max(SafeZone + 1 - Part.Current, 0L);
    }

    public long HowMuchCanRead(long? expandToBufferSize = null, bool? tryToExtendSafeZone = null)
    {
        bool shouldExtend = tryToExtendSafeZone ?? expandToBufferSize.HasValue;
        long defaultRemaining = RemainingSafe();

        if (shouldExtend)
        {
            if (expandToBufferSize.HasValue)
            {
                if (defaultRemaining < expandToBufferSize.Value)
                {
                    if (ExtendSafeZone())
                    {
                        return RemainingSafe();
                    }
                }
            }
            else if (defaultRemaining == 0L)
            {
                if (ExtendSafeZone())
                {
                    return RemainingSafe();
                }
            }
        }
        return defaultRemaining;
    }

    public bool ExtendSafeZone()
    {
        lock (_partEndLock)
        {
            long remaining = Part.RemainingLength ?? long.MaxValue;
            if (remaining == 0L)
            {
                return false;
            }

            long oldSafeZone = SafeZone;
            long newSafeZone = Math.Min(
                oldSafeZone + Math.Min(remaining, SafeZoneSize),
                Part.To ?? long.MaxValue
            );

            if (oldSafeZone == newSafeZone)
            {
                return false;
            }

            SafeZone = newSafeZone;
            return true;
        }
    }

    public RangedPart? SplitPart()
    {
        lock (_partEndLock)
        {
            if (!CanSplit()) return null;

            long delta = Part.To!.Value - SafeZone;
            long safeZoneToEnd = SafeZone + (delta / 2) + (delta % 2);

            if (safeZoneToEnd + 1 > Part.To!.Value)
            {
                return null;
            }

            var newPart = new RangedPart(
                from: safeZoneToEnd + 1,
                to: Part.To!.Value
            );

            Part.To = safeZoneToEnd;
            return newPart;
        }
    }

    public bool CanSplit()
    {
        if (Part.To == null)
        {
            return false;
        }
        long delta = Part.To.Value - SafeZone;
        return delta >= SafeZoneSize;
    }

    public override string ToString()
    {
        return $"PartSplitSupport(part={Part}, safeZone={SafeZone}, remainingSafe={RemainingSafe()})";
    }
}
