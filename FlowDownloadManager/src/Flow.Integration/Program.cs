using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Flow.Integration;

namespace Flow.Integration;

internal class Program
{
    private static async Task Main(string[] args)
    {
        Console.WriteLine("========================================");
        Console.WriteLine("Flow Integration Server Diagnostic Runner");
        Console.WriteLine("========================================");

        var handler = new ConsoleLoggerIntegrationHandler();
        var integration = new Integration(handler, debugMode: true);

        int port = 15151; // Default diagnostic port
        if (args.Length > 0 && int.TryParse(args[0], out int customPort))
        {
            port = customPort;
        }

        Console.WriteLine($"Starting Integration Server on http://localhost:{port}/ ...");
        integration.Enable(port);

        if (integration.Status is IntegrationResult.Success)
        {
            Console.WriteLine($"Server is RUNNING successfully.");
            Console.WriteLine("Endpoints:");
            Console.WriteLine($"  POST http://localhost:{port}/add");
            Console.WriteLine($"  GET  http://localhost:{port}/queues");
            Console.WriteLine($"  POST http://localhost:{port}/start-headless-download");
            Console.WriteLine($"  POST/GET http://localhost:{port}/ping");
            Console.WriteLine("Press [Ctrl+C] to stop the server...");
            
            // Keep running asynchronously until cancellation
            var tcs = new TaskCompletionSource();
            AppDomain.CurrentDomain.ProcessExit += (s, e) => tcs.SetResult();
            Console.CancelKeyPress += (s, e) => {
                e.Cancel = true;
                tcs.SetResult();
            };

            await tcs.Task;
            Console.WriteLine("Stopping server...");
            integration.Disable();
            Console.WriteLine("Server stopped.");
        }
        else if (integration.Status is IntegrationResult.Fail fail)
        {
            Console.WriteLine($"Server FAILED to start: {fail.Exception.Message}");
        }
    }
}

internal class ConsoleLoggerIntegrationHandler : IIntegrationHandler
{
    public Task AddDownloadAsync(List<IDownloadCredentialsFromIntegration> list, AddDownloadOptionsFromIntegration options)
    {
        Console.WriteLine("\n[Handler] AddDownloadAsync received:");
        Console.WriteLine($"  SilentAdd: {options.SilentAdd}, SilentStart: {options.SilentStart}");
        Console.WriteLine($"  Items Count: {list.Count}");
        for (int i = 0; i < list.Count; i++)
        {
            var item = list[i];
            Console.WriteLine($"  - Item {i + 1}: link={item.Link}, page={item.DownloadPage}, suggested={item.SuggestedName}");
            if (item is HttpDownloadCredentialsFromIntegration http)
            {
                Console.WriteLine($"    (Type: HTTP, Headers: {http.Headers?.Count ?? 0} keys)");
            }
            else if (item is HlsDownloadCredentialsFromIntegration hls)
            {
                Console.WriteLine($"    (Type: HLS, Headers: {hls.Headers?.Count ?? 0} keys)");
            }
        }
        return Task.CompletedTask;
    }

    public List<ApiQueueModel> ListQueues()
    {
        Console.WriteLine("\n[Handler] ListQueues called.");
        return new List<ApiQueueModel>
        {
            new(1, "Default Queue"),
            new(2, "Headless Background Queue"),
            new(3, "High Priority Queue")
        };
    }

    public Task AddDownloadTaskAsync(NewDownloadTask task)
    {
        Console.WriteLine("\n[Handler] AddDownloadTaskAsync received:");
        Console.WriteLine($"  Folder: {task.Folder ?? "<default>"}");
        Console.WriteLine($"  Name: {task.Name ?? "<default>"}");
        Console.WriteLine($"  QueueId: {task.QueueId?.ToString() ?? "<default>"}");
        var item = task.DownloadSource;
        Console.WriteLine($"  - Source: link={item.Link}, page={item.DownloadPage}, suggested={item.SuggestedName}");
        return Task.CompletedTask;
    }
}
