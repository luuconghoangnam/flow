using Flow.Core.Models;

namespace Flow.Monitor;

public interface IUiPart
{
    long Id { get; }
    PartDownloadStatus Status { get; }
    long HowMuchProceed { get; }
    int? Percent { get; }
    long? Length { get; }
    float PartSpace { get; }
}

public record UiRangedPart(
    long Id,
    PartDownloadStatus Status,
    long HowMuchProceed,
    int? Percent,
    long? Length,
    float PartSpace
) : IUiPart
{
    public static UiRangedPart FromPart(RangedPart part, long totalLength)
    {
        long? length = part.PartLength;
        float partSpace = 0f;
        if (totalLength > 0 && length.HasValue && length.Value > 0L)
        {
            partSpace = (float)((double)length.Value / totalLength);
        }

        return new UiRangedPart(
            Id: part.GetId(),
            Status: part.Status,
            HowMuchProceed: part.HowMuchProceed(),
            Percent: part.Percent,
            Length: length,
            PartSpace: partSpace
        );
    }
}

public record UiDurationBasedPart(
    long Id,
    PartDownloadStatus Status,
    long HowMuchProceed,
    int? Percent,
    long? Length,
    float PartSpace
) : IUiPart
{
    public static UiDurationBasedPart FromPart(MediaSegment part, int totalPartsCount)
    {
        float partSpace = 0f;
        if (totalPartsCount > 0)
        {
            partSpace = 1f / totalPartsCount;
        }

        return new UiDurationBasedPart(
            Id: part.GetId(),
            Status: part.Status,
            HowMuchProceed: part.HowMuchProceed(),
            Percent: part.Percent,
            Length: part.Length,
            PartSpace: partSpace
        );
    }
}
