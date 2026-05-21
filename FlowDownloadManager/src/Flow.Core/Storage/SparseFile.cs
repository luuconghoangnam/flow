using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace Flow.Core.Storage;

public static class SparseFile
{
    private static readonly HashSet<string> FileSystemsSupportingSparseFiles = new(StringComparer.OrdinalIgnoreCase)
    {
        // Windows
        "NTFS", "ReFS",
        // Linux / Unix
        "ext4", "ext3", "ext2",
        "XFS", "Btrfs", "ZFS", "ReiserFS", "JFS", "F2FS",
        "UFS", "UFS2", "tmpfs", "OverlayFS",
        // macOS
        "APFS", "HFS+",
        // Network file systems
        "SMB", "CIFS", "NFS", "NFSv4"
    };

    [DllImport("Kernel32.dll", SetLastError = true, CharSet = CharSet.Auto)]
    private static extern bool DeviceIoControl(
        SafeFileHandle hDevice,
        int dwIoControlCode,
        IntPtr lpInBuffer,
        int nInBufferSize,
        IntPtr lpOutBuffer,
        int nOutBufferSize,
        ref int lpBytesReturned,
        IntPtr lpOverlapped);

    private const int FSCTL_SET_SPARSE = 0x000900C4;

    public static bool CreateSparseFile(string filePath)
    {
        try
        {
            if (!File.Exists(filePath))
            {
                // Create an empty file first
                using (var fs = new FileStream(filePath, FileMode.CreateNew, FileAccess.Write, FileShare.ReadWrite))
                {
                    if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
                    {
                        int bytesReturned = 0;
                        bool success = DeviceIoControl(fs.SafeFileHandle, FSCTL_SET_SPARSE, IntPtr.Zero, 0, IntPtr.Zero, 0, ref bytesReturned, IntPtr.Zero);
                        if (!success)
                        {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        catch
        {
            return false;
        }

        return false;
    }

    public static bool CanWeCreateSparseFile(string filePath)
    {
        try
        {
            string? nearestDir = FindNearestExistingDirectory(filePath);
            if (nearestDir == null) return false;

            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                var driveRoot = Path.GetPathRoot(nearestDir);
                if (string.IsNullOrEmpty(driveRoot)) return false;

                var drive = new DriveInfo(driveRoot);
                return FileSystemsSupportingSparseFiles.Contains(drive.DriveFormat);
            }
            else
            {
                // On macOS and Linux, APFS, ext4, XFS, etc. support sparse files natively.
                // We'll return true as a safe assumption for Unix environments, or try to check DriveInfo.
                try
                {
                    var driveRoot = Path.GetPathRoot(nearestDir);
                    if (!string.IsNullOrEmpty(driveRoot))
                    {
                        var drive = new DriveInfo(driveRoot);
                        if (FileSystemsSupportingSparseFiles.Contains(drive.DriveFormat))
                        {
                            return true;
                        }
                    }
                }
                catch
                {
                    // Fallback to true since modern Unix filesystems support it
                }
                return true;
            }
        }
        catch
        {
            return false;
        }
    }

    private static string? FindNearestExistingDirectory(string path)
    {
        string? current = Path.GetDirectoryName(path);
        while (current != null)
        {
            if (Directory.Exists(current))
            {
                return current;
            }
            current = Path.GetDirectoryName(current);
        }
        return null;
    }
}
