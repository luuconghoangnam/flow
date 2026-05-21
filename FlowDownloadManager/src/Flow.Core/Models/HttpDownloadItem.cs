using System;
using System.Collections.Generic;

namespace Flow.Core.Models;

public class HttpDownloadItem : IDownloadItem, IHttpBasedDownloadCredentials
{
    // IDownloadCredentials & IHttpBasedDownloadCredentials
    public string Link { get; set; } = string.Empty;
    public Dictionary<string, string>? Headers { get; set; }
    public string? Username { get; set; }
    public string? Password { get; set; }
    public string? DownloadPage { get; set; }
    public string? UserAgent { get; set; }

    // HTTP Specific Attributes
    public string? ServerETag { get; set; }

    // IDownloadItem
    public long Id { get; set; }
    public string Folder { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public long ContentLength { get; set; } = IDownloadItem.LengthUnknown;
    public long DateAdded { get; set; }
    public long? StartTime { get; set; }
    public long? CompleteTime { get; set; }
    public DownloadStatus Status { get; set; } = DownloadStatus.Added;
    public int? PreferredConnectionCount { get; set; }
    public long SpeedLimit { get; set; } // 0 is unlimited
    public string? FileChecksum { get; set; }

    public void ValidateCredentials()
    {
        HttpDownloadCredentials.Validate(this);
    }

    public void ValidateItem()
    {
        ValidateCredentials();
    }

    public void ApplyFrom(HttpDownloadItem other)
    {
        Link = other.Link;
        Headers = other.Headers != null ? new Dictionary<string, string>(other.Headers) : null;
        Username = other.Username;
        Password = other.Password;
        DownloadPage = other.DownloadPage;
        UserAgent = other.UserAgent;

        Id = other.Id;
        Folder = other.Folder;
        Name = other.Name;

        ContentLength = other.ContentLength;
        ServerETag = other.ServerETag;

        DateAdded = other.DateAdded;
        StartTime = other.StartTime;
        CompleteTime = other.CompleteTime;
        Status = other.Status;
        PreferredConnectionCount = other.PreferredConnectionCount;
        SpeedLimit = other.SpeedLimit;

        FileChecksum = other.FileChecksum;
    }

    public void WithHttpCredentials(IHttpBasedDownloadCredentials credentials)
    {
        Link = credentials.Link;
        Headers = credentials.Headers != null ? new Dictionary<string, string>(credentials.Headers) : null;
        Username = credentials.Username;
        Password = credentials.Password;
        DownloadPage = credentials.DownloadPage;
        UserAgent = credentials.UserAgent;
    }

    public static HttpDownloadItem CreateWithCredentials(
        HttpDownloadCredentials credentials,
        long id,
        string folder,
        string name,
        long contentLength = IDownloadItem.LengthUnknown,
        string? serverETag = null,
        long dateAdded = 0,
        long? startTime = null,
        long? completeTime = null,
        DownloadStatus status = DownloadStatus.Added,
        int? preferredConnectionCount = null,
        long speedLimit = 0,
        string? fileChecksum = null)
    {
        return new HttpDownloadItem
        {
            Link = credentials.Link,
            Headers = credentials.Headers != null ? new Dictionary<string, string>(credentials.Headers) : null,
            Username = credentials.Username,
            Password = credentials.Password,
            DownloadPage = credentials.DownloadPage,
            UserAgent = credentials.UserAgent,
            Id = id,
            Folder = folder,
            Name = name,
            ContentLength = contentLength,
            ServerETag = serverETag,
            DateAdded = dateAdded,
            StartTime = startTime,
            CompleteTime = completeTime,
            Status = status,
            PreferredConnectionCount = preferredConnectionCount,
            SpeedLimit = speedLimit,
            FileChecksum = fileChecksum
        };
    }
}
