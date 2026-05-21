using System;
using System.IO;
using Flow.Core.Models;

namespace Flow.Core.Utils;

public interface IDuplicateDownloadFilter
{
    bool IsDuplicate(IDownloadItem downloadItem);
}

public class DuplicateFilterByPath : IDuplicateDownloadFilter
{
    private readonly string _filePath;

    public DuplicateFilterByPath(string filePath)
    {
        _filePath = Path.GetFullPath(filePath);
    }

    public bool IsDuplicate(IDownloadItem downloadItem)
    {
        if (downloadItem == null) return false;
        try
        {
            var otherPath = Path.GetFullPath(Path.Combine(downloadItem.Folder, downloadItem.Name));
            
            // Windows filesystems are case-insensitive, Linux is case-sensitive
            var comparison = OperatingSystem.IsWindows() 
                ? StringComparison.OrdinalIgnoreCase 
                : StringComparison.Ordinal;
                
            return string.Equals(_filePath, otherPath, comparison);
        }
        catch
        {
            return false;
        }
    }
}
