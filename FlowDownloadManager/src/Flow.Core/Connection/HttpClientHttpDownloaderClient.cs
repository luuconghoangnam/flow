using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Connection.Proxy;
using Flow.Core.Models;

namespace Flow.Core.Connection;

public class HttpClientHttpDownloaderClient : HttpDownloaderClient, IDisposable
{
    private readonly HttpClient _defaultClient;
    private readonly IUserAgentProvider _customUserAgentProvider;
    private readonly IProxyStrategyProvider _proxyStrategyProvider;
    private readonly ConcurrentDictionary<ProxyStrategy, HttpClient> _proxyClientCache = new();

    public HttpClientHttpDownloaderClient(
        IUserAgentProvider customUserAgentProvider,
        IProxyStrategyProvider proxyStrategyProvider)
    {
        _customUserAgentProvider = customUserAgentProvider ?? throw new ArgumentNullException(nameof(customUserAgentProvider));
        _proxyStrategyProvider = proxyStrategyProvider ?? throw new ArgumentNullException(nameof(proxyStrategyProvider));

        var defaultHandler = new SocketsHttpHandler
        {
            UseProxy = false,
            AutomaticDecompression = DecompressionMethods.None,
            AllowAutoRedirect = true
        };
        _defaultClient = new HttpClient(defaultHandler);
    }

    private HttpClient GetClient(IHttpBasedDownloadCredentials credentials)
    {
        var strategy = _proxyStrategyProvider.GetProxyStrategyFor(credentials.Link);
        if (strategy is ProxyStrategy.DirectStrategy)
        {
            return _defaultClient;
        }

        return _proxyClientCache.GetOrAdd(strategy, s =>
        {
            var handler = new SocketsHttpHandler
            {
                Proxy = WebProxyFactory.CreateProxy(s),
                UseProxy = s is not ProxyStrategy.DirectStrategy,
                AutomaticDecompression = DecompressionMethods.None,
                AllowAutoRedirect = true
            };
            return new HttpClient(handler);
        });
    }

    private HttpRequestMessage CreateRequest(
        IHttpBasedDownloadCredentials credentials,
        long? start,
        long? end,
        HttpMethod method)
    {
        var request = new HttpRequestMessage(method, credentials.Link);

        // Apply default headers first
        foreach (var kvp in DefaultHeadersInFirst())
        {
            request.Headers.TryAddWithoutValidation(kvp.Key, kvp.Value);
        }

        // Apply user agent
        if (string.IsNullOrEmpty(credentials.UserAgent))
        {
            string customUserAgent = _customUserAgentProvider.GetUserAgent() ?? GetDefaultUserAgent();
            request.Headers.TryAddWithoutValidation("User-Agent", customUserAgent);
        }
        else
        {
            request.Headers.TryAddWithoutValidation("User-Agent", credentials.UserAgent);
        }

        // Apply credential headers
        if (credentials.Headers != null)
        {
            foreach (var kvp in credentials.Headers)
            {
                if (kvp.Key.Equals("Host", StringComparison.OrdinalIgnoreCase)) continue;
                request.Headers.TryAddWithoutValidation(kvp.Key, kvp.Value);
            }
        }

        // Apply default headers last
        foreach (var kvp in DefaultHeadersInLast())
        {
            request.Headers.TryAddWithoutValidation(kvp.Key, kvp.Value);
        }

        // Apply basic auth
        if (!string.IsNullOrWhiteSpace(credentials.Username) && !string.IsNullOrWhiteSpace(credentials.Password))
        {
            var authValue = Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes($"{credentials.Username}:{credentials.Password}"));
            request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Basic", authValue);
        }

        // Apply Range
        if (start.HasValue)
        {
            var (rk, rv) = CreateRangeHeader(start.Value, end);
            request.Headers.TryAddWithoutValidation(rk, rv);
        }

        return request;
    }

    protected override async Task<HttpResponseInfo> ActualHeadAsync(
        IHttpBasedDownloadCredentials credentials,
        long? start,
        long? end,
        CancellationToken cancellationToken)
    {
        var client = GetClient(credentials);
        using var request = CreateRequest(credentials, start, end, HttpMethod.Get);
        
        using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        return CreateFileInfo(response);
    }

    protected override async Task<Connection<HttpResponseInfo>> ActualConnectAsync(
        IHttpBasedDownloadCredentials credentials,
        long? start,
        long? end,
        CancellationToken cancellationToken)
    {
        var client = GetClient(credentials);
        var request = CreateRequest(credentials, start, end, HttpMethod.Get);
        
        HttpResponseMessage? response = null;
        try
        {
            response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            response.EnsureSuccessStatusCode();

            var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
            long contentLength = response.Content.Headers.ContentLength ?? -1L;
            var info = CreateFileInfo(response);

            return new Connection<HttpResponseInfo>(stream, contentLength, info);
        }
        catch
        {
            response?.Dispose();
            request.Dispose();
            throw;
        }
    }

    private HttpResponseInfo CreateFileInfo(HttpResponseMessage response)
    {
        var requestHeaders = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        if (response.RequestMessage != null)
        {
            foreach (var header in response.RequestMessage.Headers)
            {
                requestHeaders[header.Key] = string.Join(", ", header.Value);
            }
        }

        var responseHeaders = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var header in response.Headers)
        {
            responseHeaders[header.Key] = string.Join(", ", header.Value);
        }
        foreach (var header in response.Content.Headers)
        {
            responseHeaders[header.Key] = string.Join(", ", header.Value);
        }

        return new HttpResponseInfo(
            statusCode: (int)response.StatusCode,
            message: response.ReasonPhrase ?? response.StatusCode.ToString(),
            requestUrl: response.RequestMessage?.RequestUri?.ToString() ?? response.Headers.Location?.ToString() ?? string.Empty,
            requestHeaders: requestHeaders,
            responseHeaders: responseHeaders
        );
    }

    public void Dispose()
    {
        _defaultClient.Dispose();
        foreach (var client in _proxyClientCache.Values)
        {
            client.Dispose();
        }
        _proxyClientCache.Clear();
    }
}

public static class WebProxyFactory
{
    public static IWebProxy? CreateProxy(ProxyStrategy strategy)
    {
        return strategy switch
        {
            ProxyStrategy.DirectStrategy => null,
            ProxyStrategy.UseSystemStrategy => HttpClient.DefaultProxy,
            ProxyStrategy.ManualProxyStrategy manual => CreateManualProxy(manual.Proxy),
            ProxyStrategy.ByScriptStrategy => HttpClient.DefaultProxy,
            _ => null
        };
    }

    private static IWebProxy CreateManualProxy(DownloadProxy proxy)
    {
        string scheme = proxy.Type == ProxyType.Socks ? "socks5" : "http";
        var uri = new Uri($"{scheme}://{proxy.Host}:{proxy.Port}");
        var webProxy = new WebProxy(uri);
        if (!string.IsNullOrEmpty(proxy.Username))
        {
            webProxy.Credentials = new NetworkCredential(proxy.Username, proxy.Password ?? string.Empty);
        }
        return webProxy;
    }
}
