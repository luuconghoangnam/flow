namespace Flow.Shared.Utils;

public static class PathValidator
{
    public static bool CanWriteToThisPath(string path)
    {
        return FileUtils.CanWriteInThisFolder(path);
    }

    public static bool IsValidPath(string path)
    {
        if (string.IsNullOrWhiteSpace(path)) return false;
        try
        {
            // GetFullPath throws if the path contains invalid characters or is malformed
            var fullPath = Path.GetFullPath(path);
            return true;
        }
        catch
        {
            return false;
        }
    }
}
