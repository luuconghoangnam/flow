using System.Collections.Generic;

namespace Flow.Core.Models;

public interface IHttpBasedDownloadCredentials : IDownloadCredentials
{
    Dictionary<string, string>? Headers { get; set; }
    string? Username { get; set; }
    string? Password { get; set; }
    string? UserAgent { get; set; }
}
