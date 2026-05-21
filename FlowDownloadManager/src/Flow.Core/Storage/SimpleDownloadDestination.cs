using System;
using System.IO;
using System.Threading.Tasks;
using Flow.Core.Models;
using Microsoft.Win32.SafeHandles;

namespace Flow.Core.Storage;

public class SimpleDownloadDestination : DownloadDestination
{
    public bool AppendExtensionToIncompleteDownloads { get; }
    public long DownloadId { get; }
    private readonly EmptyFileCreator _emptyFileCreator;

    public string IncompleteFile => IncompleteFileUtil.AddIncompleteIndicator(OutputFile, DownloadId);

    public string FileToWrite => AppendExtensionToIncompleteDownloads ? IncompleteFile : OutputFile;

    private FileStream? _fileStream;
    private SafeFileHandle? _safeFileHandle;

    public long OutputSize { get; set; } = -1;

    public SimpleDownloadDestination(
        string file,
        bool appendExtensionToIncompleteDownloads,
        long downloadId,
        EmptyFileCreator emptyFileCreator) : base(file)
    {
        AppendExtensionToIncompleteDownloads = appendExtensionToIncompleteDownloads;
        DownloadId = downloadId;
        _emptyFileCreator = emptyFileCreator;
    }

    private SafeFileHandle GetFileHandle()
    {
        lock (this)
        {
            if (_safeFileHandle == null || _safeFileHandle.IsClosed || _safeFileHandle.IsInvalid)
            {
                _fileStream = new FileStream(FileToWrite, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.ReadWrite, 4096, FileOptions.Asynchronous);
                _safeFileHandle = _fileStream.SafeFileHandle;
            }
            return _safeFileHandle;
        }
    }

    private void RemoveFileHandle()
    {
        lock (this)
        {
            _fileStream?.Dispose();
            _fileStream = null;
            _safeFileHandle = null;
        }
    }

    protected override void OnAllFilePartsRemoved()
    {
        base.OnAllFilePartsRemoved();
        RemoveFileHandle();
    }

    public override void OnAllPartsCompleted(Action<int?>? onProgressUpdate = null)
    {
        RemoveFileHandle(); // Ensure file handle is closed before trying to rename

        if (AppendExtensionToIncompleteDownloads)
        {
            string incomplete = IncompleteFile;
            if (!File.Exists(incomplete))
            {
                return;
            }

            string complete = OutputFile;
            if (File.Exists(complete))
            {
                try
                {
                    File.Delete(complete);
                }
                catch { }
            }

            try
            {
                File.Move(incomplete, complete);
            }
            catch (Exception ex)
            {
                throw new InvalidOperationException($"Failed to move .part file to the actual output file: {ex.Message}", ex);
            }
        }

        base.OnAllPartsCompleted(onProgressUpdate);
    }

    public override DestWriter GetWriterFor(IDownloadPart part)
    {
        if (!CanGetFileWriter())
        {
            throw new InvalidOperationException("Cannot acquire file writer in current state.");
        }

        string outFile = FileToWrite;
        var existing = ReturnIfAlreadyHaveWriter(part.GetId());
        if (existing != null)
        {
            return existing;
        }

        var writer = new DestWriter(
            part.GetId(),
            outFile,
            part.Current,
            GetFileHandle()
        );

        lock (this)
        {
            FileParts.Add(writer);
        }

        return writer;
    }

    public override void Flush()
    {
        lock (this)
        {
            try
            {
                _fileStream?.Flush(true);
            }
            catch { }
        }
    }

    public void PrepareDestinationFolder()
    {
        DownloadDestination.PrepareDestinationFolder(FileToWrite);
    }

    public override async Task PrepareFileAsync(Action<int?> onProgressUpdate)
    {
        PrepareDestinationFolder();
        await _emptyFileCreator.PrepareFileAsync(FileToWrite, OutputSize, onProgressUpdate);
    }

    public override Task<bool> IsDownloadedPartsIsValidAsync()
    {
        string targetFile = FileToWrite;
        bool exists = File.Exists(targetFile);
        if (!exists) return Task.FromResult(false);

        var fileInfo = new FileInfo(targetFile);
        bool sizeMatches = fileInfo.Length == OutputSize;
        return Task.FromResult(exists && sizeMatches);
    }

    public override bool CanGetFileWriter()
    {
        return true;
    }

    protected override void UpdateLastModified()
    {
        try
        {
            if (RequestedToChangeLastModified.HasValue && File.Exists(FileToWrite))
            {
                var dt = DateTimeOffset.FromUnixTimeMilliseconds(RequestedToChangeLastModified.Value).DateTime;
                File.SetLastWriteTime(FileToWrite, dt);
            }
        }
        catch { }
    }

    public override void MoveOutput(string toPath)
    {
        if (AppendExtensionToIncompleteDownloads)
        {
            string incomplete = IncompleteFile;
            if (File.Exists(incomplete))
            {
                try
                {
                    string newIncomplete = IncompleteFileUtil.AddIncompleteIndicator(toPath, DownloadId);
                    string? newDir = Path.GetDirectoryName(newIncomplete);
                    if (!string.IsNullOrEmpty(newDir) && !Directory.Exists(newDir))
                    {
                        Directory.CreateDirectory(newDir);
                    }

                    if (File.Exists(newIncomplete))
                    {
                        File.Delete(newIncomplete);
                    }
                    File.Move(incomplete, newIncomplete);
                }
                catch (Exception ex)
                {
                    throw new InvalidOperationException($"Failed to move .part file to the new destination: {ex.Message}", ex);
                }
            }
        }

        base.MoveOutput(toPath);
    }

    public override void CleanUpJunkFiles()
    {
        try
        {
            string incomplete = IncompleteFile;
            if (File.Exists(incomplete))
            {
                File.Delete(incomplete);
            }
        }
        catch { }
    }
}
