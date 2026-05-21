using System;

namespace Flow.Core.Models;

public class HttpDownloader : IDownloader<HttpDownloadItem, HttpDownloadJob, HttpDownloadCredentials>
{
    private readonly Lazy<Connection.HttpDownloaderClient> _clientLazy;

    public Connection.HttpDownloaderClient Client => _clientLazy.Value;

    public HttpDownloader(Lazy<Connection.HttpDownloaderClient> clientLazy)
    {
        _clientLazy = clientLazy ?? throw new ArgumentNullException(nameof(clientLazy));
    }

    public DownloadJob CreateJob(IDownloadItem item, DownloadManager downloadManager)
    {
        if (item is not HttpDownloadItem httpItem)
        {
            throw new ArgumentException($"Expected HttpDownloadItem but got {item?.GetType().FullName}", nameof(item));
        }
        return new HttpDownloadJob(httpItem, downloadManager, Client);
    }

    public bool Accept(IDownloadItem item)
    {
        return item is HttpDownloadItem;
    }

    public Type DownloadItemType => typeof(HttpDownloadItem);
    public Type DownloadCredentialsType => typeof(HttpDownloadCredentials);
    public Type DownloadJobType => typeof(HttpDownloadJob);
}
