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
    public Core.DownloadManager DownloadManager { get; }
    public ManualDownloadQueue ManualQueue { get; }
    public DownloadMonitor DownloadMonitor { get; }
    public Integration.Integration IntegrationServer { get; }
    public DesktopIntegrationHandler IntegrationHandler { get; }

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
        IntegrationHandler = new DesktopIntegrationHandler(DownloadManager, this);
        IntegrationServer = new Integration.Integration(IntegrationHandler, debugMode: true);
    }

    /// <summary>
    /// Fired when the browser extension sends a non-silent download request.
    /// Subscribe in App.axaml.cs to show the AddDownloadDialog.
    /// </summary>
    public event EventHandler<IntegrationDownloadRequestEventArgs>? OnIntegrationDownloadRequested
    {
        add => IntegrationHandler.OnDownloadRequested += value;
        remove => IntegrationHandler.OnDownloadRequested -= value;
    }

    public async Task StartAsync()
    {
        Flow.Shared.Utils.Logger.Info("[Bootstrapper] App starting...");
        // Boot up database items & schedule loops
        // DownloadMonitor starts automatically in its constructor via StartLifecycleAsync()
        await DownloadManager.BootAsync();

        // Wire up the per-download floating progress windows (IDM style)
        DownloadWindowManager.Instance.Initialize(DownloadManager);

        // Run Integration Server on default port 15151
        try
        {
            Flow.Shared.Utils.Logger.Info("[Bootstrapper] Starting integration server on port 15151...");
            IntegrationServer.Enable(15151);
            Flow.Shared.Utils.Logger.Info("[Bootstrapper] Integration server enabled on port 15151.");
        }
        catch (Exception ex)
        {
            Flow.Shared.Utils.Logger.Error("[Bootstrapper] Failed to start integration server", ex);
            Console.WriteLine($"[Bootstrapper] Failed to start integration server: {ex.Message}");
        }
    }

    public async Task StopAsync()
    {
        Flow.Shared.Utils.Logger.Info("[Bootstrapper] App stopping...");
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

/// <summary>
/// Carries download credentials from the integration server to the UI thread for dialog display.
/// </summary>
public class IntegrationDownloadRequestEventArgs : EventArgs
{
    public List<IDownloadCredentialsFromIntegration> Items { get; }
    public AddDownloadOptionsFromIntegration Options { get; }

    public IntegrationDownloadRequestEventArgs(List<IDownloadCredentialsFromIntegration> items, AddDownloadOptionsFromIntegration options)
    {
        Items = items;
        Options = options;
    }
}

public class DesktopIntegrationHandler : IIntegrationHandler
{
    private readonly DownloadManager _downloadManager;
    private readonly AppBootstrapper _bootstrapper;

    /// <summary>
    /// Raised when the integration server receives a non-silent download request.
    /// UI should handle this to show a download dialog.
    /// </summary>
    public event EventHandler<IntegrationDownloadRequestEventArgs>? OnDownloadRequested;

    public DesktopIntegrationHandler(DownloadManager downloadManager, AppBootstrapper bootstrapper)
    {
        _downloadManager = downloadManager;
        _bootstrapper = bootstrapper;
    }

    public async Task AddDownloadAsync(List<IDownloadCredentialsFromIntegration> list, AddDownloadOptionsFromIntegration options)
    {
        Flow.Shared.Utils.Logger.Info($"[Integration] AddDownloadAsync received {list.Count} items. SilentAdd: {options.SilentAdd}, SilentStart: {options.SilentStart}");

        // Non-silent: dispatch to UI to show download dialog with pre-filled info
        if (!options.SilentAdd)
        {
            Flow.Shared.Utils.Logger.Info($"[Integration] Non-silent mode — raising OnDownloadRequested for UI dialog");
            Avalonia.Threading.Dispatcher.UIThread.Post(() =>
            {
                OnDownloadRequested?.Invoke(this, new IntegrationDownloadRequestEventArgs(list, options));
            });
            return;
        }

        // Silent: add directly without user dialog
        await AddDownloadsDirectly(list, options.SilentStart);
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
        Flow.Shared.Utils.Logger.Info($"[Integration] AddDownloadTaskAsync received. Link: {cred.Link}, Name: {task.Name}, Folder: {task.Folder}");

        // Headless tasks always add silently (they have all info pre-filled)
        await AddSingleDownloadDirectly(cred, task.Folder ?? _bootstrapper.DefaultDownloadFolder, task.Name, autoStart: true);
    }

    /// <summary>
    /// Adds downloads directly without showing a dialog. Used for silent/headless mode.
    /// </summary>
    public async Task AddDownloadsDirectly(List<IDownloadCredentialsFromIntegration> list, bool autoStart)
    {
        foreach (var cred in list)
        {
            await AddSingleDownloadDirectly(cred, _bootstrapper.DefaultDownloadFolder, cred.SuggestedName, autoStart);
        }
    }

    private async Task AddSingleDownloadDirectly(IDownloadCredentialsFromIntegration cred, string folder, string? name, bool autoStart)
    {
        Flow.Shared.Utils.Logger.Info($"[Integration] Direct add: {cred.Link}");

        var item = new HttpDownloadItem
        {
            Link = cred.Link,
            Name = name ?? Path.GetFileName(new Uri(cred.Link).LocalPath),
            Folder = folder,
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
        Flow.Shared.Utils.Logger.Info($"[Integration] Item added to DB (ID: {id}): {item.Name}");

        if (autoStart)
        {
            Flow.Shared.Utils.Logger.Info($"[Integration] Auto-starting download ID: {id}");
            await _downloadManager.ResumeAsync(id);
        }
    }
}
