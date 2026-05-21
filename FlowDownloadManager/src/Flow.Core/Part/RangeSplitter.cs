using System;
using System.Collections.Generic;

namespace Flow.Core.Part;

public static class RangeSplitter
{
    public static List<(long Start, long End)> SplitToRange(long size, long minPartSize, long maxPartCount)
    {
        if (size < 1) throw new ArgumentOutOfRangeException(nameof(size), "size must be >= 1");
        if (minPartSize < 1) throw new ArgumentOutOfRangeException(nameof(minPartSize), "minPartSize must be >= 1");
        if (maxPartCount < 1) throw new ArgumentOutOfRangeException(nameof(maxPartCount), "maxPartCount must be >= 1");

        long minParts = (size + minPartSize - 1) / minPartSize; // round up division
        long actualPartCount = Math.Min(maxPartCount, minParts);
        long idealPartSize = size / actualPartCount;

        var ranges = new List<(long Start, long End)>();
        long start = 0L;
        for (long i = 1; i <= actualPartCount; i++)
        {
            long end = start + idealPartSize - 1;
            if (i <= size % actualPartCount)
            {
                end++;
            }
            ranges.Add((start, end));
            start = end + 1;
        }
        return ranges;
    }
}
