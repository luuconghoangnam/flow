using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Connection;
using Flow.Core.Models;
using Flow.Core.Storage;

namespace Flow.Core.Part;

public class HttpPartDownloader : PartDownloader<RangedPart>
{
    private readonly PartSplitSupport _partSplitSupport;

    public IHttpBasedDownloadCredentials Credentials { get; }
    public HttpDownloaderClient Client { get; }
    public List<Throttler> SpeedLimiters { get; }
    public bool StrictMode { get; }

    public HttpPartDownloader(
        IHttpBasedDownloadCredentials credentials,
        Func<DestWriter> getDestWriter,
        RangedPart part,
        HttpDownloaderClient client,
        List<Throttler> speedLimiters,
        bool strictMode,
        object partSplitLock)
        : base(part, getDestWriter)
    {
        Credentials = credentials ?? throw new ArgumentNullException(nameof(credentials));
        Client = client ?? throw new ArgumentNullException(nameof(client));
        SpeedLimiters = speedLimiters ?? new List<Throttler>();
        StrictMode = strictMode;
        _partSplitSupport = new PartSplitSupport(part, partSplitLock);
    }

    private async Task<Connection<HttpResponseInfo>> EstablishConnectionAsync(
        long from,
        long? to,
        CancellationToken cancellationToken)
    {
        var connect = await Client.ConnectAsync(Credentials, from, to, cancellationToken);
        try
        {
            connect.ResponseInfo.ExpectSuccess();
        }
        catch
        {
            connect.Dispose();
            throw;
        }
        return connect;
    }

    protected override void OnFinish()
    {
        lock (_partSplitSupport)
        {
            if (Part.IsBlind)
            {
                Part.SetBlindAsCompleted();
            }
        }
        base.OnFinish();
    }

    public override long HowMuchCanRead(long maxAllowed)
    {
        return _partSplitSupport.HowMuchCanRead(maxAllowed, tryToExtendSafeZone: true);
    }

    public bool CanBeSplit()
    {
        return _partSplitSupport.CanSplit();
    }

    public override async Task<Connection<HttpResponseInfo>> ConnectAndVerifyAsync(CancellationToken cancellationToken)
    {
        var partCopy = new RangedPart(Part.From, Part.To, Part.Current);
        var conn = await EstablishConnectionAsync(partCopy.Current, partCopy.To, cancellationToken);

        if (Stop || cancellationToken.IsCancellationRequested)
        {
            conn.Dispose();
            throw new OperationCanceledException(cancellationToken);
        }

        long? contentLength = conn.ContentLength == -1L ? null : conn.ContentLength;

        if (contentLength != partCopy.RemainingLength)
        {
            bool throwException = StrictMode;
            if (StrictMode)
            {
                if (conn.ResponseInfo.ContentRange?.Range != null)
                {
                    var range = conn.ResponseInfo.ContentRange.Range.Value;
                    if (range.Start == partCopy.Current)
                    {
                        throwException = false;
                    }
                }
            }

            var ex = new Flow.Core.Exceptions.ServerPartIsNotTheSameAsWeExpectException(
                partCopy.Current,
                partCopy.To,
                partCopy.RemainingLength ?? 0,
                contentLength
            );

            if (throwException)
            {
                conn.Dispose();
                throw ex;
            }
            else
            {
                Console.WriteLine($"WARNING: {ex.Message}");
            }
        }

        return conn;
    }

    protected override async Task OnDataReadAsync(int bytesRead, CancellationToken cancellationToken)
    {
        foreach (var throttler in SpeedLimiters)
        {
            await throttler.ThrottleAsync(bytesRead, cancellationToken);
        }
    }

    public RangedPart? SplitPart()
    {
        return _partSplitSupport.SplitPart();
    }
}
