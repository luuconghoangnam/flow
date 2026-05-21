namespace Flow.Core.Connection;

public interface IResponseInfo
{
    bool IsSuccessFul { get; }
    bool RequiresAuth { get; }
    bool RequireBasicAuth { get; }
    bool ResumeSupport { get; }
    bool IsWebPage { get; }
}
