using System;
using System.IO;

namespace Flow.Core.Connection;

public class Connection<TResponseInfo> : IDisposable where TResponseInfo : IResponseInfo
{
    public Stream Stream { get; }
    public long ContentLength { get; }
    public TResponseInfo ResponseInfo { get; }

    public Connection(Stream stream, long contentLength, TResponseInfo responseInfo)
    {
        Stream = stream ?? throw new ArgumentNullException(nameof(stream));
        ContentLength = contentLength;
        ResponseInfo = responseInfo;
    }

    public void Dispose()
    {
        Stream.Dispose();
    }
}
