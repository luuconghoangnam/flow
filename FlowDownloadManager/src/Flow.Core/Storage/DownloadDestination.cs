using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core.Storage;

public abstract class DownloadDestination
{
    public string OutputFile { get; }

    protected readonly List<DestWriter> FileParts = new();
    protected bool AllPartsDownloaded = false;
    protected long? RequestedToChangeLastModified = null;

    protected DownloadDestination(string outputFile)
    {
        OutputFile = Path.GetFullPath(outputFile);
    }

    protected virtual void OnAllFilePartsRemoved()
    {
        UpdateLastModified();
    }

    public virtual void OnAllPartsCompleted(Action<int?>? onProgressUpdate = null)
    {
        AllPartsDownloaded = true;
        CleanUpJunkFiles();
        UpdateLastModified();
    }

    public virtual void CleanUpJunkFiles()
    {
    }

    public abstract DestWriter GetWriterFor(IDownloadPart part);
    public abstract bool CanGetFileWriter();

    public DestWriter? ReturnIfAlreadyHaveWriter(long partId)
    {
        lock (this)
        {
            return FileParts.FirstOrDefault(w => w.Id == partId);
        }
    }

    public virtual void DeleteOutputFile()
    {
        try
        {
            if (File.Exists(OutputFile))
            {
                File.Delete(OutputFile);
            }
        }
        catch { }
    }

    public abstract Task PrepareFileAsync(Action<int?> onProgressUpdate);
    public abstract Task<bool> IsDownloadedPartsIsValidAsync();
    public abstract void Flush();

    public virtual void OnPartCancelled(IDownloadPart part)
    {
        lock (this)
        {
            var writer = FileParts.FirstOrDefault(w => w.Id == part.GetId());
            if (writer != null)
            {
                FileParts.Remove(writer);
                writer.Dispose();

                if (FileParts.Count == 0)
                {
                    OnAllFilePartsRemoved();
                }
            }
        }
    }

    public void SetLastModified(long? timestamp)
    {
        RequestedToChangeLastModified = timestamp;
    }

    protected virtual void UpdateLastModified()
    {
        try
        {
            if (RequestedToChangeLastModified.HasValue && File.Exists(OutputFile))
            {
                var dt = DateTimeOffset.FromUnixTimeMilliseconds(RequestedToChangeLastModified.Value).DateTime;
                File.SetLastWriteTime(OutputFile, dt);
            }
        }
        catch { }
    }

    public virtual void MoveOutput(string toPath)
    {
        if (File.Exists(OutputFile))
        {
            try
            {
                string targetDir = Path.GetDirectoryName(toPath) ?? string.Empty;
                if (!string.IsNullOrEmpty(targetDir) && !Directory.Exists(targetDir))
                {
                    Directory.CreateDirectory(targetDir);
                }

                if (File.Exists(toPath))
                {
                    File.Delete(toPath);
                }
                File.Move(OutputFile, toPath);
            }
            catch (Exception ex)
            {
                throw new InvalidOperationException($"Failed to move output file to the new destination: {ex.Message}", ex);
            }
        }
    }

    public static void PrepareDestinationFolder(string outputFile)
    {
        string? parent = Path.GetDirectoryName(outputFile);
        if (!string.IsNullOrEmpty(parent))
        {
            Directory.CreateDirectory(parent);
            if (!Directory.Exists(parent))
            {
                throw new IOException($"Cannot create folder for destination file: {parent}");
            }
        }
    }
}
