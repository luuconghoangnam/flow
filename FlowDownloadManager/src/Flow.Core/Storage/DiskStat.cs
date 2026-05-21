using System;
using System.IO;

namespace Flow.Core.Storage;

public class DiskStat : IDiskStat
{
    public long GetRemainingSpace(string path)
    {
        try
        {
            string? root = Path.GetPathRoot(path);
            if (!string.IsNullOrEmpty(root))
            {
                var drive = new DriveInfo(root);
                return drive.AvailableFreeSpace;
            }
        }
        catch
        {
            // fallback if anything goes wrong, try checking directory parent
            try
            {
                string? parent = Path.GetDirectoryName(path);
                if (parent != null)
                {
                    string? root = Path.GetPathRoot(parent);
                    if (!string.IsNullOrEmpty(root))
                    {
                        var drive = new DriveInfo(root);
                        return drive.AvailableFreeSpace;
                    }
                }
            }
            catch { }
        }
        return long.MaxValue; // Return max value as fallback to not block the download
    }
}
