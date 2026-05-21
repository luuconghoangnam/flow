using System.Diagnostics;
using System.Runtime.InteropServices;

namespace Flow.Shared.Utils;

public static class FileUtils
{
    public static bool OpenFile(string path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return false;

        try
        {
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
                return true;
            }
            if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            {
                return ExecAndWait("open", $"\"{path}\"");
            }
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Linux))
            {
                return ExecAndWait("xdg-open", $"\"{path}\"");
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Failed to open file {path}: {ex.Message}");
        }
        return false;
    }

    public static bool OpenFolderOfFile(string path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return false;

        try
        {
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                Process.Start("explorer.exe", $"/select,\"{path}\"");
                return true;
            }
            if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            {
                return ExecAndWait("open", $"-R \"{path}\"");
            }
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Linux))
            {
                // DBus FileManager1 spec for selecting files
                var uri = "file://" + Uri.EscapeDataString(path).Replace("%2F", "/");
                bool dbusSuccess = ExecAndWait("dbus-send", 
                    $"--print-reply --dest=org.freedesktop.FileManager1 /org/freedesktop/FileManager1 org.freedesktop.FileManager1.ShowItems array:string:\"{uri}\" string:\"\"");
                
                if (dbusSuccess) return true;

                // Fallback to opening parent directory
                string? parent = Path.GetDirectoryName(path);
                if (parent != null)
                {
                    return ExecAndWait("xdg-open", $"\"{parent}\"");
                }
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Failed to open folder of file {path}: {ex.Message}");
        }
        return false;
    }

    public static bool OpenFolder(string folderPath)
    {
        if (string.IsNullOrWhiteSpace(folderPath) || !Directory.Exists(folderPath)) return false;

        try
        {
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                Process.Start(new ProcessStartInfo(folderPath) { UseShellExecute = true });
                return true;
            }
            if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            {
                return ExecAndWait("open", $"\"{folderPath}\"");
            }
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Linux))
            {
                return ExecAndWait("xdg-open", $"\"{folderPath}\"");
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Failed to open folder {folderPath}: {ex.Message}");
        }
        return false;
    }

    public static bool CanWriteInThisFolder(string folderPath)
    {
        try
        {
            if (!Directory.Exists(folderPath))
            {
                string? parent = Path.GetDirectoryName(folderPath);
                if (parent == null) return false;
                return CanWriteInThisFolder(parent);
            }

            string testFile = Path.Combine(folderPath, Guid.NewGuid().ToString("N") + ".tmp");
            using (var stream = File.Create(testFile))
            {
                stream.WriteByte(0);
            }
            File.Delete(testFile);
            return true;
        }
        catch
        {
            return false;
        }
    }

    public static bool IsRemovableStorage(string path)
    {
        try
        {
            string? root = Path.GetPathRoot(path);
            if (!string.IsNullOrEmpty(root))
            {
                var drive = new DriveInfo(root);
                return drive.DriveType == DriveType.Removable;
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"Failed to determine drive type for {path}: {ex.Message}");
        }
        return false;
    }

    private static bool ExecAndWait(string fileName, string arguments)
    {
        try
        {
            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = fileName,
                Arguments = arguments,
                CreateNoWindow = true,
                UseShellExecute = false
            });
            if (process == null) return false;
            process.WaitForExit();
            return process.ExitCode == 0;
        }
        catch
        {
            return false;
        }
    }
}
