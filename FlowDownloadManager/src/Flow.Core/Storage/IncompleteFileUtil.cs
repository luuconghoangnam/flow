using System.IO;
using System.Runtime.InteropServices;

namespace Flow.Core.Storage;

public static class IncompleteFileUtil
{
    private const int SYSTEM_MAXIMUM_FILE_LENGTH = 255;
    private const int SYSTEM_MAXIMUM_FULL_PATH_LENGTH = 259;

    private static string CreateExtension(long id)
    {
        return $".dl-{id}.Flow.part";
    }

    public static string AddIncompleteIndicator(string filePath, long id)
    {
        var fileInfo = new FileInfo(filePath);
        var ext = CreateExtension(id);

        if (!fileInfo.Name.EndsWith(ext))
        {
            string trimmedFileName;
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                // Account for the directory separator
                int parentPathLength = fileInfo.DirectoryName?.Length ?? 0;
                int maxFileNameLength = SYSTEM_MAXIMUM_FULL_PATH_LENGTH - (parentPathLength + 1 + ext.Length);
                maxFileNameLength = Math.Max(0, maxFileNameLength);

                string originalName = fileInfo.Name;
                trimmedFileName = originalName.Substring(0, Math.Min(originalName.Length, maxFileNameLength));
            }
            else
            {
                string originalName = fileInfo.Name;
                int maxFileNameLength = SYSTEM_MAXIMUM_FILE_LENGTH - ext.Length;
                trimmedFileName = originalName.Substring(0, Math.Min(originalName.Length, maxFileNameLength));
            }

            string parentFolder = fileInfo.DirectoryName ?? string.Empty;
            return Path.Combine(parentFolder, trimmedFileName + ext);
        }

        return filePath;
    }
}
