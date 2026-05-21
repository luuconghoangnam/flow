using System;
using System.Collections.Generic;
using System.Linq;

namespace Flow.Core.Models;

public class DownloaderRegistry
{
    private readonly HashSet<IDownloader> _downloaders = new();
    private readonly object _lock = new();

    public void Add(IDownloader downloader)
    {
        if (downloader == null) throw new ArgumentNullException(nameof(downloader));
        lock (_lock)
        {
            _downloaders.Add(downloader);
        }
    }

    public void Remove(IDownloader downloader)
    {
        if (downloader == null) throw new ArgumentNullException(nameof(downloader));
        lock (_lock)
        {
            _downloaders.Remove(downloader);
        }
    }

    public DownloadJob CreateJob(IDownloadItem downloadItem, DownloadManager downloadManager)
    {
        if (downloadItem == null) throw new ArgumentNullException(nameof(downloadItem));
        if (downloadManager == null) throw new ArgumentNullException(nameof(downloadManager));

        IDownloader? downloader;
        lock (_lock)
        {
            downloader = _downloaders.FirstOrDefault(d => d.Accept(downloadItem));
        }

        if (downloader == null)
        {
            throw new NotSupportedException($"Download item type '{downloadItem.GetType().FullName}' is not supported by any registered downloader!");
        }

        return downloader.CreateJob(downloadItem, downloadManager);
    }

    public List<IDownloader> GetAll()
    {
        lock (_lock)
        {
            return _downloaders.ToList();
        }
    }
}
