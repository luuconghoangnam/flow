using System;
using Flow.Core.Models;

namespace Flow.Core.Exceptions;

public abstract class DownloadValidationException : Exception
{
    protected DownloadValidationException(string message, Exception? innerException = null) 
        : base(message, innerException)
    {
    }

    public abstract bool IsCritical { get; }
}

public class NoSpaceInStorageException : DownloadValidationException
{
    public long Available { get; }
    public long Required { get; }

    public NoSpaceInStorageException(long available, long required) 
        : base($"No space available required={required}, available={available}")
    {
        Available = available;
        Required = required;
    }

    public override bool IsCritical => true;
}

public abstract class FileChangedException : DownloadValidationException
{
    protected FileChangedException(string message) : base(message)
    {
    }

    public override bool IsCritical => true;

    public class LengthChangedException : FileChangedException
    {
        public long LastContentLength { get; }
        public long NewContentLength { get; }

        public LengthChangedException(long lastContentLength, long newContentLength)
            : base($"File size changed since last download! last time was {lastContentLength} now it's {newContentLength}")
        {
            LastContentLength = lastContentLength;
            NewContentLength = newContentLength;
        }
    }

    public class ETagChangedException : FileChangedException
    {
        public string OldETag { get; }
        public string NewETag { get; }

        public ETagChangedException(string oldETag, string newETag)
            : base($"File content changed since last download! last time was {oldETag} now it's {newETag}")
        {
            OldETag = oldETag;
            NewETag = newETag;
        }
    }

    public class GotAWebPage : FileChangedException
    {
        public GotAWebPage() : base("link is a webpage")
        {
        }
    }
}

public class ServerResumeSupportChangeException : DownloadValidationException
{
    public ServerResumeSupportChangeException() 
        : base("Server resume support changed, please restart the download manually")
    {
    }

    public override bool IsCritical => false;
}

public class ServerPartIsNotTheSameAsWeExpectException : DownloadValidationException
{
    public long Start { get; }
    public long? End { get; }
    public long? ExpectedLength { get; }
    public long? ActualLength { get; }

    public ServerPartIsNotTheSameAsWeExpectException(long start, long? end, long? expectedLength, long? actualLength)
        : base($"Response Length not match. expecting '{expectedLength}', but we got '{actualLength}', requested range is {start}-{end}")
    {
        Start = start;
        End = end;
        ExpectedLength = expectedLength;
        ActualLength = actualLength;
    }

    public override bool IsCritical => false;
}

public class PrepareDestinationFailedException : DownloadValidationException
{
    public PrepareDestinationFailedException(Exception e)
        : base($"Problem in preparing output: {e.Message}", e)
    {
    }

    public override bool IsCritical => true;
}

public class UnSuccessfulResponseException : Exception
{
    public int Code { get; }

    public UnSuccessfulResponseException(int code, string msg) 
        : base($"{code} | {msg}")
    {
        Code = code;
    }
}

public class PartTooManyErrorException : Exception
{
    public IDownloadPart Part { get; }

    public PartTooManyErrorException(IDownloadPart part, Exception cause)
        : base($"this part {part} have too many errors", cause)
    {
        Part = part;
    }
}

public class TooManyErrorException : Exception
{
    public TooManyErrorException(Exception cause)
        : base("Download is stopped because all parts exceeds max retries", cause)
    {
    }

    public Exception FindActualDownloadErrorCause()
    {
        if (InnerException is PartTooManyErrorException p && p.InnerException != null)
        {
            return p.InnerException;
        }
        return InnerException ?? this;
    }
}
