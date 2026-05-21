using System;
using System.IO;
using System.Threading.Tasks;
using Microsoft.Win32.SafeHandles;

namespace Flow.Core.Storage;

public class DestWriter : IDisposable
{
    public long Id { get; }
    public string FilePath { get; }
    public long SeekPos { get; set; }
    public SafeFileHandle FileHandle { get; }

    private Status _status = Status.NotPrepared;

    public DestWriter(long id, string filePath, long seekPos, SafeFileHandle fileHandle)
    {
        Id = id;
        FilePath = filePath;
        SeekPos = seekPos;
        FileHandle = fileHandle;
    }

    public enum Status
    {
        NotPrepared,
        Preparing,
        Prepared,
        Writing,
        Finished
    }

    public Status CurrentStatus => _status;

    public void Prepare()
    {
        if (_status != Status.NotPrepared)
        {
            throw new InvalidOperationException($"Already prepared or in invalid state: status={_status}");
        }
        if (!File.Exists(FilePath))
        {
            throw new FileNotFoundException("Target file does not exist, cannot prepare writer.", FilePath);
        }

        _status = Status.Preparing;
        _status = Status.Prepared;
    }

    public void Release()
    {
        _status = Status.NotPrepared;
    }

    public void Write(ReadOnlySpan<byte> buffer)
    {
        if (_status == Status.NotPrepared)
        {
            throw new InvalidOperationException("First prepare the DestWriter.");
        }
        if (_status == Status.Finished)
        {
            throw new InvalidOperationException("DestWriter is already finished.");
        }
        if (_status == Status.Prepared)
        {
            _status = Status.Writing;
        }

        RandomAccess.Write(FileHandle, buffer, SeekPos);
        SeekPos += buffer.Length;
    }

    public async ValueTask WriteAsync(ReadOnlyMemory<byte> buffer)
    {
        if (_status == Status.NotPrepared)
        {
            throw new InvalidOperationException("First prepare the DestWriter.");
        }
        if (_status == Status.Finished)
        {
            throw new InvalidOperationException("DestWriter is already finished.");
        }
        if (_status == Status.Prepared)
        {
            _status = Status.Writing;
        }

        await RandomAccess.WriteAsync(FileHandle, buffer, SeekPos);
        SeekPos += buffer.Length;
    }

    public void Use(Action<DestWriter> block)
    {
        Prepare();
        try
        {
            block(this);
        }
        finally
        {
            Release();
        }
    }

    public void Dispose()
    {
        Release();
    }
}
