using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Exceptions;

namespace Flow.Core.Storage;

public class EmptyFileCreator
{
    private readonly IDiskStat _diskStat;
    private readonly Func<bool> _useSparseFile;

    public EmptyFileCreator(IDiskStat diskStat, Func<bool> useSparseFile)
    {
        _diskStat = diskStat;
        _useSparseFile = useSparseFile;
    }

    private bool CanWeUseSparse(string filePath)
    {
        return _useSparseFile() && SparseFile.CanWeCreateSparseFile(filePath);
    }

    public async Task PrepareFileAsync(
        string filePath,
        long length,
        Action<int?> onProgressUpdate,
        CancellationToken cancellationToken = default)
    {
        if (length < -1)
        {
            throw new ArgumentOutOfRangeException(nameof(length), $"length must be -1 or a positive value, but got {length}");
        }

        await Task.Run(async () =>
        {
            bool canWeUseSparse = CanWeUseSparse(filePath);
            onProgressUpdate(0);

            if (length == -1L)
            {
                using (var fs = new FileStream(filePath, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite))
                {
                    fs.SetLength(0);
                }
                onProgressUpdate(100);
                return;
            }

            string parentDir = Path.GetDirectoryName(filePath) ?? string.Empty;
            long remainingSpace = _diskStat.GetRemainingSpace(parentDir);

            if (File.Exists(filePath))
            {
                var fileInfo = new FileInfo(filePath);
                long currentLength = fileInfo.Length;
                long requiredLength = length - currentLength;

                if (remainingSpace < requiredLength)
                {
                    throw new NoSpaceInStorageException(remainingSpace, requiredLength);
                }

                if (currentLength > length)
                {
                    using (var fs = new FileStream(filePath, FileMode.Open, FileAccess.Write, FileShare.ReadWrite))
                    {
                        fs.SetLength(length);
                    }
                    onProgressUpdate(100);
                }
                else if (currentLength < length)
                {
                    if (canWeUseSparse)
                    {
                        try
                        {
                            File.Delete(filePath);
                        }
                        catch (Exception ex)
                        {
                            throw new IOException("Cannot delete file to re-create it as sparse file.", ex);
                        }

                        if (SparseFile.CreateSparseFile(filePath))
                        {
                            onProgressUpdate(null);
                            WriteAtLast(filePath, length);
                        }
                        else
                        {
                            // fallback
                            using (var fs = new FileStream(filePath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite))
                            {
                                // Created empty file
                            }
                            await FillOutputAsync(filePath, length, onProgressUpdate, cancellationToken);
                        }
                    }
                    else
                    {
                        await FillOutputAsync(filePath, length, onProgressUpdate, cancellationToken);
                    }
                    onProgressUpdate(100);
                }
                else
                {
                    onProgressUpdate(100);
                }
            }
            else
            {
                if (remainingSpace < length)
                {
                    throw new NoSpaceInStorageException(remainingSpace, length);
                }

                if (canWeUseSparse && SparseFile.CreateSparseFile(filePath))
                {
                    onProgressUpdate(null);
                    WriteAtLast(filePath, length);
                }
                else
                {
                    using (var fs = new FileStream(filePath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite))
                    {
                        // Created empty file
                    }
                    await FillOutputAsync(filePath, length, onProgressUpdate, cancellationToken);
                }
                onProgressUpdate(100);
            }
        }, cancellationToken);
    }

    private void WriteAtLast(string filePath, long length)
    {
        using var fs = new FileStream(filePath, FileMode.Open, FileAccess.Write, FileShare.ReadWrite);
        fs.Seek(length - 1, SeekOrigin.Begin);
        fs.WriteByte(0);
    }

    private async Task FillOutputAsync(
        string filePath,
        long length,
        Action<int?> onProgressUpdate,
        CancellationToken cancellationToken)
    {
        var fileInfo = new FileInfo(filePath);
        long much = length - fileInfo.Length;
        string parentDir = fileInfo.DirectoryName ?? string.Empty;
        long remainingSpace = _diskStat.GetRemainingSpace(parentDir);

        if (remainingSpace < much)
        {
            throw new NoSpaceInStorageException(remainingSpace, much);
        }

        using var fs = new FileStream(filePath, FileMode.Append, FileAccess.Write, FileShare.ReadWrite);
        byte[] buffer = new byte[8192];
        long written = 0L;

        while (written < much)
        {
            cancellationToken.ThrowIfCancellationRequested();
            int writeInThisLoop = (int)Math.Min(buffer.Length, much - written);
            if (writeInThisLoop <= 0) break;

            await fs.WriteAsync(buffer.AsMemory(0, writeInThisLoop), cancellationToken);
            written += writeInThisLoop;
            onProgressUpdate(CalcPercent(written, much));
        }
    }

    private static int CalcPercent(long progress, long total)
    {
        if (total <= 0) return 0;
        return (int)((progress * 100) / total);
    }
}
