namespace Flow.Shared.Utils;

public static class FileNameValidator
{
    public static bool IsValidFileName(string name)
    {
        if (string.IsNullOrWhiteSpace(name)) return false;

        // Check for any invalid characters in filename
        var invalidChars = Path.GetInvalidFileNameChars();
        if (name.Any(c => invalidChars.Contains(c))) return false;

        return true;
    }
}
