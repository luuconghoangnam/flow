using System;
using System.Collections.Generic;

namespace Flow.Core.Models;

public class HttpDownloadCredentials : IHttpBasedDownloadCredentials
{
    public string Link { get; set; } = string.Empty;
    public Dictionary<string, string>? Headers { get; set; }
    public string? Username { get; set; }
    public string? Password { get; set; }
    public string? DownloadPage { get; set; }
    public string? UserAgent { get; set; }

    public void ValidateCredentials()
    {
        Validate(this);
    }

    public static HttpDownloadCredentials Empty() => new() { Link = string.Empty };

    public static HttpDownloadCredentials From(IHttpBasedDownloadCredentials credentials)
    {
        if (credentials is HttpDownloadCredentials concrete)
        {
            return concrete;
        }

        return new HttpDownloadCredentials
        {
            Link = credentials.Link,
            Headers = credentials.Headers != null ? new Dictionary<string, string>(credentials.Headers) : null,
            Username = credentials.Username,
            Password = credentials.Password,
            DownloadPage = credentials.DownloadPage,
            UserAgent = credentials.UserAgent
        };
    }

    public static void Validate(IDownloadCredentials credentials)
    {
        if (string.IsNullOrWhiteSpace(credentials.Link))
        {
            throw new ArgumentException("URL cannot be empty.");
        }

        bool isValid = Uri.TryCreate(credentials.Link, UriKind.Absolute, out var uriResult)
                       && (uriResult.Scheme == Uri.UriSchemeHttp || uriResult.Scheme == Uri.UriSchemeHttps);

        if (!isValid)
        {
            throw new ArgumentException("URL is not valid.");
        }
    }
}
