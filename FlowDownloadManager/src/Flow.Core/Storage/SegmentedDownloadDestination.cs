using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core.Storage;

public class SegmentedDownloadDestination : DownloadDestination
{
    public string TempDirectory { get; }
    public Func<IDownloadPart, string> GetFileName { get; }
    public Func<List<IDownloadPart>> GetAllParts { get; }
    public bool AppendMode { get; }

    // Store FileStream per part so we can close/flush them properly
    private readonly Dictionary<long, FileStream> _partStreams = new();

    public SegmentedDownloadDestination(
        string tempDirectory,
        Func<IDownloadPart, string> getFileName,
        Func<List<IDownloadPart>> getAllParts,
        bool appendMode,
        string outputFile) : base(outputFile)
    {
        TempDirectory = Path.GetFullPath(tempDirectory);
        GetFileName = getFileName;
        GetAllParts = getAllParts;
        AppendMode = appendMode;
    }

    private string GetFileOfPart(IDownloadPart part)
    {
        return Path.Combine(TempDirectory, part.GetId().ToString());
    }

    public override DestWriter GetWriterFor(IDownloadPart part)
    {
        if (!Directory.Exists(TempDirectory))
        {
            Directory.CreateDirectory(TempDirectory);
        }

        string tFile = GetFileOfPart(part);
        var existing = ReturnIfAlreadyHaveWriter(part.GetId());
        if (existing != null)
        {
            return existing;
        }

        FileStream fs;
        lock (this)
        {
            if (_partStreams.TryGetValue(part.GetId(), out var existingFs) && !existingFs.SafeFileHandle.IsClosed)
            {
                fs = existingFs;
            }
            else
            {
                fs = new FileStream(tFile, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.ReadWrite, 4096, FileOptions.Asynchronous);
                _partStreams[part.GetId()] = fs;
            }
        }

        long newSize = AppendMode ? fs.Length : 0;
        fs.SetLength(newSize);

        var destWriter = new DestWriter(
            part.GetId(),
            tFile,
            newSize,
            fs.SafeFileHandle
        );

        lock (this)
        {
            FileParts.Add(destWriter);
        }

        return destWriter;
    }

    public override void OnPartCancelled(IDownloadPart part)
    {
        base.OnPartCancelled(part);

        lock (this)
        {
            if (_partStreams.TryGetValue(part.GetId(), out var fs))
            {
                try
                {
                    fs.Dispose();
                }
                catch { }
                _partStreams.Remove(part.GetId());
            }
        }
    }

    public override bool CanGetFileWriter()
    {
        return true;
    }

    public override Task PrepareFileAsync(Action<int?> onProgressUpdate)
    {
        return Task.CompletedTask;
    }

    public override Task<bool> IsDownloadedPartsIsValidAsync()
    {
        return Task.FromResult(Directory.Exists(TempDirectory));
    }

    public bool IsDownloadPartValid(IDownloadPart part)
    {
        return File.Exists(GetFileOfPart(part));
    }

    public override void CleanUpJunkFiles()
    {
        try
        {
            if (Directory.Exists(TempDirectory))
            {
                Directory.Delete(TempDirectory, true);
            }
        }
        catch { }
    }

    public override void OnAllPartsCompleted(Action<int?>? onProgressUpdate = null)
    {
        // Close all part streams first to make sure files are released
        lock (this)
        {
            foreach (var fs in _partStreams.Values)
            {
                try
                {
                    fs.Dispose();
                }
                catch { }
            }
            _partStreams.Clear();
        }

        var sortedParts = GetAllParts()
            .OrderBy(p => p.GetId())
            .Select(p => Path.Combine(TempDirectory, GetFileName(p)))
            .ToList();

        Assemble(sortedParts, OutputFile, onProgressUpdate);

        base.OnAllPartsCompleted(onProgressUpdate);
    }

    private void Assemble(List<string> sources, string destination, Action<int?>? onProgress)
    {
        DownloadDestination.PrepareDestinationFolder(OutputFile);
        
        long totalLength = 0;
        foreach (var src in sources)
        {
            if (File.Exists(src))
            {
                totalLength += new FileInfo(src).Length;
            }
        }

        long totalWritten = 0L;
        byte[] buffer = new byte[8192];
        int percent = 0;

        using (var dstStream = new FileStream(destination, FileMode.Create, FileAccess.Write, FileShare.ReadWrite))
        {
            foreach (var sourceFile in sources)
            {
                if (!File.Exists(sourceFile)) continue;

                onProgress?.Invoke(percent);
                using (var srcStream = new FileStream(sourceFile, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
                {
                    while (true)
                    {
                        int len = srcStream.Read(buffer, 0, buffer.Length);
                        if (len <= 0) break;

                        dstStream.Write(buffer, 0, len);
                        totalWritten += len;

                        int newPercent = CalcPercent(totalWritten, totalLength);
                        if (newPercent != percent)
                        {
                            onProgress?.Invoke(newPercent);
                            percent = newPercent;
                        }
                    }
                }
            }
        }
        onProgress?.Invoke(100);
    }

    private static int CalcPercent(long progress, long total)
    {
        if (total <= 0) return 0;
        return (int)((progress * 100) / total);
    }

    public override void Flush()
    {
        lock (this)
        {
            foreach (var fs in _partStreams.Values)
            {
                try
                {
                    fs.Flush(true);
                }
                catch { }
            }
        }
    }
}
