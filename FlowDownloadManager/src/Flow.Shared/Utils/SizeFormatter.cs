using System.Globalization;

namespace Flow.Shared.Utils;

public static class SizeFormatter
{
    private static readonly string[] BinaryUnits = { "B", "KB", "MB", "GB", "TB", "PB", "EB" };

    public static string FormatBytes(long bytes, bool useBinary = true, bool compact = false)
    {
        if (bytes < 0) return "Unknown";
        if (bytes == 0) return compact ? "0B" : "0 B";

        double size = bytes;
        int unitIndex = 0;
        double limit = useBinary ? 1024.0 : 1000.0;
        string[] units = BinaryUnits;

        while (size >= limit && unitIndex < units.Length - 1)
        {
            size /= limit;
            unitIndex++;
        }

        string format = compact ? "0.#" : "0.##";
        string formattedValue = size.ToString(format, CultureInfo.InvariantCulture);
        string space = compact ? "" : " ";
        return $"{formattedValue}{space}{units[unitIndex]}";
    }

    public static string FormatSpeed(long bytesPerSecond, bool useBinary = true)
    {
        if (bytesPerSecond < 0) return "-";
        return $"{FormatBytes(bytesPerSecond, useBinary)}/s";
    }
}
