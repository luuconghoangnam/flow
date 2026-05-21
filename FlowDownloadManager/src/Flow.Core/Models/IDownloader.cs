using System;

namespace Flow.Core.Models;

public interface IDownloader
{
    DownloadJob CreateJob(IDownloadItem item, DownloadManager downloadManager);
    bool Accept(IDownloadItem item);
    Type DownloadItemType { get; }
    Type DownloadCredentialsType { get; }
    Type DownloadJobType { get; }
}

public interface IDownloader<in TDownloadItem, out TDownloadJob, in TDownloadCredentials> : IDownloader
    where TDownloadItem : IDownloadItem
    where TDownloadJob : DownloadJob
    where TDownloadCredentials : IDownloadCredentials
{
}
