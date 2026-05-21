using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace Flow.Integration;

public abstract record IntegrationResult
{
    public record Inactive : IntegrationResult;
    public record Fail(Exception Exception) : IntegrationResult;
    public record Success(int Port) : IntegrationResult;
}

public class Integration
{
    private readonly IIntegrationHandler _handler;
    private readonly bool _debugMode;
    private HttpListener? _listener;
    private CancellationTokenSource? _cts;
    private readonly object _lock = new();

    public IntegrationResult Status { get; private set; } = new IntegrationResult.Inactive();
    public event Action<IntegrationResult>? OnStatusChanged;

    public Integration(IIntegrationHandler handler, bool debugMode = false)
    {
        _handler = handler ?? throw new ArgumentNullException(nameof(handler));
        _debugMode = debugMode;
    }

    public void Enable(int port)
    {
        lock (_lock)
        {
            if (Status is IntegrationResult.Success s && s.Port == port)
            {
                return; // Already running on this port
            }

            Disable();

            try
            {
                _listener = new HttpListener();
                // Listen to localhost on specified port. Note that HttpListener on Windows
                // requires admin privileges if we listen to non-localhost, but "http://localhost:port/"
                // is normally allowed for non-admin accounts.
                _listener.Prefixes.Add($"http://localhost:{port}/");
                _listener.Start();

                _cts = new CancellationTokenSource();
                var token = _cts.Token;

                Task.Run(() => ListenLoopAsync(_listener, token), token);

                UpdateStatus(new IntegrationResult.Success(port));
            }
            catch (Exception ex)
            {
                if (_debugMode)
                {
                    Console.WriteLine($"[Integration] Failed to start on port {port}: {ex}");
                }
                UpdateStatus(new IntegrationResult.Fail(ex));
                Disable();
            }
        }
    }

    public void Disable()
    {
        lock (_lock)
        {
            if (_cts != null)
            {
                _cts.Cancel();
                _cts.Dispose();
                _cts = null;
            }

            if (_listener != null)
            {
                try
                {
                    _listener.Stop();
                    _listener.Close();
                }
                catch { }
                _listener = null;
            }

            if (Status is not IntegrationResult.Inactive)
            {
                UpdateStatus(new IntegrationResult.Inactive());
            }
        }
    }

    private void UpdateStatus(IntegrationResult newStatus)
    {
        Status = newStatus;
        OnStatusChanged?.Invoke(newStatus);
    }

    private async Task ListenLoopAsync(HttpListener listener, CancellationToken token)
    {
        while (!token.IsCancellationRequested && listener.IsListening)
        {
            try
            {
                var context = await listener.GetContextAsync();
                _ = Task.Run(() => HandleRequestAsync(context, token), token);
            }
            catch (Exception ex)
            {
                if (token.IsCancellationRequested || !listener.IsListening)
                {
                    break;
                }
                if (_debugMode)
                {
                    Console.WriteLine($"[Integration] Error accepting connection: {ex.Message}");
                }
            }
        }
    }

    private async Task HandleRequestAsync(HttpListenerContext context, CancellationToken token)
    {
        var request = context.Request;
        var response = context.Response;

        // Apply CORS headers for browser extensions (crucial for interop)
        response.Headers.Add("Access-Control-Allow-Origin", "*");
        response.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.Headers.Add("Access-Control-Allow-Headers", "Content-Type, Accept");

        try
        {
            // Handle preflight OPTIONS request
            if (request.HttpMethod == "OPTIONS")
            {
                response.StatusCode = (int)HttpStatusCode.OK;
                response.Close();
                return;
            }

            var urlPath = request.Url?.AbsolutePath.ToLowerInvariant() ?? "/";

            if (urlPath == "/ping")
            {
                await WriteTextResponseAsync(response, HttpStatusCode.OK, "pong");
                return;
            }

            if (urlPath == "/add" && request.HttpMethod == "POST")
            {
                using var reader = new StreamReader(request.InputStream, request.ContentEncoding ?? Encoding.UTF8);
                var body = await reader.ReadToEndAsync(token);
                
                var addRequest = AddDownloadsFromIntegration.CreateFromRequest(body);
                await _handler.AddDownloadAsync(addRequest.Items, addRequest.Options);

                await WriteTextResponseAsync(response, HttpStatusCode.OK, "OK");
                return;
            }

            if (urlPath == "/queues" && request.HttpMethod == "GET")
            {
                var queues = _handler.ListQueues();
                var jsonOptions = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
                var json = JsonSerializer.Serialize(queues, jsonOptions);

                await WriteJsonResponseAsync(response, HttpStatusCode.OK, json);
                return;
            }

            if (urlPath == "/start-headless-download" && request.HttpMethod == "POST")
            {
                using var reader = new StreamReader(request.InputStream, request.ContentEncoding ?? Encoding.UTF8);
                var body = await reader.ReadToEndAsync(token);

                var jsonOptions = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
                var task = JsonSerializer.Deserialize<NewDownloadTask>(body, jsonOptions);

                if (task == null)
                {
                    await WriteTextResponseAsync(response, HttpStatusCode.BadRequest, "Invalid task payload");
                    return;
                }

                await _handler.AddDownloadTaskAsync(task);
                await WriteTextResponseAsync(response, HttpStatusCode.OK, "OK");
                return;
            }

            // Endpoint not found
            await WriteTextResponseAsync(response, HttpStatusCode.NotFound, "Not Found");
        }
        catch (Exception ex)
        {
            if (_debugMode)
            {
                Console.WriteLine($"[Integration] Error handling {request.HttpMethod} {request.RawUrl}: {ex}");
            }

            var errorMsg = _debugMode ? $"Error: {ex.Message}" : "Error";
            await WriteTextResponseAsync(response, HttpStatusCode.InternalServerError, errorMsg);
        }
        finally
        {
            try
            {
                response.Close();
            }
            catch { }
        }
    }

    private static async Task WriteTextResponseAsync(HttpListenerResponse response, HttpStatusCode statusCode, string text)
    {
        response.StatusCode = (int)statusCode;
        response.ContentType = "text/plain; charset=utf-8";
        var bytes = Encoding.UTF8.GetBytes(text);
        response.ContentLength64 = bytes.Length;
        await response.OutputStream.WriteAsync(bytes.AsMemory());
    }

    private static async Task WriteJsonResponseAsync(HttpListenerResponse response, HttpStatusCode statusCode, string json)
    {
        response.StatusCode = (int)statusCode;
        response.ContentType = "application/json; charset=utf-8";
        var bytes = Encoding.UTF8.GetBytes(json);
        response.ContentLength64 = bytes.Length;
        await response.OutputStream.WriteAsync(bytes.AsMemory());
    }
}
