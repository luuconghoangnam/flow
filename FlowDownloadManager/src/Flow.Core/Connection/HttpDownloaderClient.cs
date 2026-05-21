using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core.Connection;

public abstract class HttpDownloaderClient
{
    public virtual Dictionary<string, string> DefaultHeadersInFirst() => new();

    public virtual Dictionary<string, string> DefaultHeadersInLast() => new(StringComparer.OrdinalIgnoreCase)
    {
        { "accept-encoding", "identity" }
    };

    protected abstract Task<HttpResponseInfo> ActualHeadAsync(
        IHttpBasedDownloadCredentials credentials,
        long? start,
        long? end,
        CancellationToken cancellationToken);

    protected abstract Task<Connection<HttpResponseInfo>> ActualConnectAsync(
        IHttpBasedDownloadCredentials credentials,
        long? start,
        long? end,
        CancellationToken cancellationToken);

    public async Task<HttpResponseInfo> HeadAsync(
        IHttpBasedDownloadCredentials credentials,
        long? start,
        long? end,
        CancellationToken cancellationToken = default)
    {
        return await ActualHeadAsync(credentials, start, end, cancellationToken);
    }

    public async Task<Connection<HttpResponseInfo>> ConnectAsync(
        IHttpBasedDownloadCredentials credentials,
        long? start,
        long? end,
        CancellationToken cancellationToken = default)
    {
        return await ActualConnectAsync(credentials, start, end, cancellationToken);
    }

    public async Task<HttpResponseInfo> TestAsync(
        IHttpBasedDownloadCredentials credentials,
        CancellationToken cancellationToken = default)
    {
        try
        {
            long rangeStart = 0L;
            long rangeEnd = 255L;
            long rangeLength = rangeEnd - rangeStart + 1; // 256
            var response = await HeadAsync(credentials, rangeStart, rangeEnd, cancellationToken);

            if (response.IsSuccessFul && response.TotalLength != rangeLength)
            {
                return response;
            }
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            // Some servers may reset the connection if we ask for range, so we fallback
        }
        return await HeadAsync(credentials, null, null, cancellationToken);
    }

    public static (string Key, string Value) CreateRangeHeader(long start, long? end)
    {
        return ("Range", $"bytes={start}-{end?.ToString() ?? ""}");
    }

    public static string GetDefaultUserAgent() => UserAgent.GetDefault();
}
