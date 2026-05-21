using System.Collections.Generic;
using System.IO;

namespace Flow.Core.Utils;

public static class FileNameUtil
{
    public static string? GetExtensionOrNull(string filename)
    {
        int lastDot = filename.LastIndexOf('.');
        if (lastDot >= 0 && lastDot < filename.Length - 1)
        {
            return filename.Substring(lastDot + 1);
        }
        return null;
    }

    public static IEnumerable<string> NumberedIfExists(string filepath)
    {
        if (!File.Exists(filepath))
        {
            yield return filepath;
        }

        string directory = Path.GetDirectoryName(filepath) ?? string.Empty;
        string nameWithoutExtension = Path.GetFileNameWithoutExtension(filepath);
        string ext = Path.GetExtension(filepath); // Includes the leading dot (e.g., ".txt")

        int counter = 1;
        while (true)
        {
            string newPath = Path.Combine(directory, $"{nameWithoutExtension}_{counter}{ext}");
            if (!File.Exists(newPath))
            {
                yield return newPath;
            }
            counter++;
        }
    }

    public static string ReplaceExtension(string filename, string newExtension, bool appendIfNotExists = true)
    {
        string? ext = GetExtensionOrNull(filename);
        if (ext == null)
        {
            return appendIfNotExists ? $"{filename}.{newExtension}" : filename;
        }
        string filenameWithoutExtension = filename.Substring(0, filename.Length - ext.Length - 1);
        return $"{filenameWithoutExtension}.{newExtension}";
    }
}
