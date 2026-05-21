using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading.Tasks;
using Flow.Core;
using Flow.Core.Connection;
using Flow.Core.Connection.Proxy;
using Flow.Core.Models;
using Flow.Core.Queue;
using Flow.Core.Storage;
using Flow.Monitor;
using Flow.Integration;

namespace Flow.Desktop.Services;

public class AppBootstrapper
{
    private static readonly Lazy<AppBootstrapper> _instance = new(() => new AppBootstrapper());
    public static AppBootstrapper Instance => _instance.Value;

    public string AppDataFolder { get; }
    public DownloadSettings Settings { get; private set; } = null!;
    public TransactionalFileSaver FileSaver { get; }
    public DownloadListFileStorage DlListDb { get; }
    public PartListFileStorage PartListDb { get; }
    public EmptyFileCreator EmptyFileCreator { get; }
    public HttpClientHttpDownloaderClient HttpClient { get; }
    public DownloaderRegistry DownloaderRegistry { get; }
    public DownloadManager DownloadManager { get; }
    public ManualDownloadQueue ManualQueue { get; }
    public DownloadMonitor DownloadMonitor { get; }
    public Integration.Integration IntegrationServer { get; }

    public string DefaultDownloadFolder { get; set; }

    private AppBootstrapper()
    {
        // 1. Locate AppData & Downloads Folder
        AppDataFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "FlowDownloadManager"
        );
        Directory.CreateDirectory(AppDataFolder);

        string dbFolder = Path.Combine(AppDataFolder, "downloads");
        string partsFolder = Path.Combine(AppDataFolder, "parts");
        
        DefaultDownloadFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
            "Downloads"
        );
        if (!Directory.Exists(DefaultDownloadFolder))
        {
            DefaultDownloadFolder = AppDataFolder;
        }

        // 2. Initialize Core components
        FileSaver = new TransactionalFileSaver();
        
        // Load Settings
        string settingsFile = Path.Combine(AppDataFolder, "settings.json");
        var loadedSettings = FileSaver.ReadObject<DownloadSettings>(settingsFile);
        if (loadedSettings == null)
        {
            Settings = new DownloadSettings();
            FileSaver.WriteObject(settingsFile, Settings);
        }
        else
        {
            Settings = loadedSettings;
        }

        DlListDb = new DownloadListFileStorage(dbFolder, FileSaver);
        PartListDb = new PartListFileStorage(partsFolder, FileSaver);
        
        var diskStat = new DiskStat();
        EmptyFileCreator = new EmptyFileCreator(diskStat, () => Settings.UseSparseFileAllocation);

        var uaProvider = new DesktopUserAgentProvider();
        var proxyProvider = new DesktopProxyStrategyProvider();
        HttpClient = new HttpClientHttpDownloaderClient(uaProvider, proxyProvider);

        DownloaderRegistry = new DownloaderRegistry();
        var httpDownloader = new HttpDownloader(new Lazy<HttpDownloaderClient>(() => HttpClient));
        DownloaderRegistry.Add(httpDownloader);

        DownloadManager = new DownloadManager(
            DlListDb,
            PartListDb,
            Settings,
            EmptyFileCreator,
            DownloaderRegistry,
            AppDataFolder
        );

        ManualQueue = new ManualDownloadQueue(DownloadManager);
        DownloadMonitor = new DownloadMonitor(
            DownloadManager,
            ManualQueue,
            new Lazy<IDownloadItemStateFactory<IDownloadItem, DownloadJob>>(
                () => new HttpDownloadItemStateFactory()
            )
        );

        // 3. Initialize Integration Handler & Server
        var handler = new DesktopIntegrationHandler(DownloadManager, this);
        IntegrationServer = new Integration.Integration(handler, debugMode: true);
    }

    public async Task StartAsync()
    {
        // Boot up database items & schedule loops
        // DownloadMonitor starts automatically in its constructor via StartLifecycleAsync()
        await DownloadManager.BootAsync();

        // Run Integration Server on default port 23075
        try
        {
            IntegrationServer.Enable(23075);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Bootstrapper] Failed to start integration server: {ex.Message}");
        }
    }

    public async Task StopAsync()
    {
        try
        {
            IntegrationServer.Disable();
        }
        catch { }

        // DownloadMonitor uses Dispose() to cancel its background tasks
        DownloadMonitor.Dispose();
        await DownloadManager.StopAllAsync(new DownloadItemContext(new[] { new StoppedBy(UserActor.Instance) }));
        
        // Save current configurations
        string settingsFile = Path.Combine(AppDataFolder, "settings.json");
        FileSaver.WriteObject(settingsFile, Settings);
    }

    public void SaveSettings()
    {
        string settingsFile = Path.Combine(AppDataFolder, "settings.json");
        FileSaver.WriteObject(settingsFile, Settings);
        DownloadManager.ReloadSetting();
    }
}

public class DesktopIntegrationHandler : IIntegrationHandler
{
    private readonly DownloadManager _downloadManager;
    private readonly AppBootstrapper _bootstrapper;

    public DesktopIntegrationHandler(DownloadManager downloadManager, AppBootstrapper bootstrapper)
    {
        _downloadManager = downloadManager;
        _bootstrapper = bootstrapper;
    }

    public async Task AddDownloadAsync(List<IDownloadCredentialsFromIntegration> list, AddDownloadOptionsFromIntegration options)
    {
        foreach (var cred in list)
        {
            var item = new HttpDownloadItem
            {
                Link = cred.Link,
                Name = cred.SuggestedName ?? Path.GetFileName(new Uri(cred.Link).LocalPath),
                Folder = _bootstrapper.DefaultDownloadFolder,
                DownloadPage = cred.DownloadPage,
                DateAdded = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                Status = DownloadStatus.Added
            };

            if (string.IsNullOrWhiteSpace(item.Name))
            {
                item.Name = "download_" + Guid.NewGuid().ToString("N").Substring(0, 8);
            }

            if (cred is HttpDownloadCredentialsFromIntegration httpCred)
            {
                item.Headers = httpCred.Headers;
            }
            else if (cred is HlsDownloadCredentialsFromIntegration hlsCred)
            {
                item.Headers = hlsCred.Headers;
            }

            var props = new NewDownloadItemProps(
                item,
                null,
                OnDuplicateStrategy.AddNumbered,
                DownloadItemContext.Empty
            );

            long id = await _downloadManager.AddDownloadAsync(props);
            if (options.SilentStart)
            {
                await _downloadManager.ResumeAsync(id);
            }
        }
    }

    public List<ApiQueueModel> ListQueues()
    {
        return new List<ApiQueueModel>
        {
            new(1, "Main Queue")
        };
    }

    public async Task AddDownloadTaskAsync(NewDownloadTask task)
    {
        var cred = task.DownloadSource;
        var item = new HttpDownloadItem
        {
            Link = cred.Link,
            Name = task.Name ?? cred.SuggestedName ?? Path.GetFileName(new Uri(cred.Link).LocalPath),
            Folder = task.Folder ?? _bootstrapper.DefaultDownloadFolder,
            DownloadPage = cred.DownloadPage,
            DateAdded = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            Status = DownloadStatus.Added
        };

        if (string.IsNullOrWhiteSpace(item.Name))
        {
            item.Name = "download_" + Guid.NewGuid().ToString("N").Substring(0, 8);
        }

        if (cred is HttpDownloadCredentialsFromIntegration httpCred)
        {
            item.Headers = httpCred.Headers;
        }
        else if (cred is HlsDownloadCredentialsFromIntegration hlsCred)
        {
            item.Headers = hlsCred.Headers;
        }

        var props = new NewDownloadItemProps(
            item,
            null,
            OnDuplicateStrategy.AddNumbered,
            DownloadItemContext.Empty
        );

        await _downloadManager.AddDownloadAsync(props);
    }
}
