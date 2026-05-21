namespace Flow.Core.Models;

public interface IDownloadCredentials
{
    string Link { get; set; }
    string? DownloadPage { get; set; }

    void ValidateCredentials();
}
