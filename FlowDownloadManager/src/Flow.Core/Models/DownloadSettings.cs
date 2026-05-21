namespace Flow.Core.Models;

public class DownloadSettings
{
    public int DefaultThreadCount { get; set; } = 8;
    public bool DynamicPartCreationMode { get; set; } = true;
    public bool UseServerLastModifiedTime { get; set; } = false;
    public long GlobalSpeedLimit { get; set; } = 0; // unlimited
    public bool UseSparseFileAllocation { get; set; } = true;
    public long MinPartSize { get; } = 2048; // 2kB
    public int MaxDownloadRetryCount { get; set; } = 0;
    public bool AppendExtensionToIncompleteDownloads { get; set; } = false;
}
