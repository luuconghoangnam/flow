# KẾ HOẠCH MIGRATION: FLOW DOWNLOAD MANAGER
# Kotlin Multiplatform → C# .NET (1:1 Parity)

## TỔNG QUAN

**Mục tiêu:** Migration hoàn toàn app Flow Download Manager từ Kotlin sang C# với trải nghiệm người dùng giống hệt 100%

**Timeline:** 12 tháng
**Team size:** 2-3 developers
**Target platforms:** Windows, macOS, Linux

---

## PHẦN 1: TECHNOLOGY MAPPING

### Core Technologies

| Kotlin Stack | C# Equivalent | Notes |
|--------------|---------------|-------|
| Kotlin Multiplatform | .NET 8/9 | Cross-platform runtime |
| Jetpack Compose Desktop | Avalonia UI 11.x | XAML-based UI framework |
| Gradle | MSBuild / .csproj | Build system |
| Koin (DI) | Microsoft.Extensions.DependencyInjection | Built-in DI |
| kotlinx.coroutines | async/await + Task | Native C# async |
| kotlinx.serialization | System.Text.Json | JSON serialization |
| OkHttp | HttpClient | Built-in HTTP client |
| StateFlow/MutableStateFlow | IObservable / ReactiveUI | Reactive state |
| Decompose | ReactiveUI Routing | Navigation |

---

## PHẦN 2: PROJECT STRUCTURE

```
FlowDownloadManager/
├── src/
│   ├── Flow.Core/                          # Download engine
│   │   ├── Models/
│   │   │   ├── IDownloadItem.cs
│   │   │   ├── HttpDownloadItem.cs
│   │   │   ├── HLSDownloadItem.cs
│   │   │   ├── DownloadStatus.cs
│   │   │   ├── QueueModel.cs
│   │   │   └── Category.cs
│   │   ├── Engine/
│   │   │   ├── DownloadManager.cs
│   │   │   ├── HttpDownloadJob.cs
│   │   │   ├── HLSDownloadJob.cs
│   │   │   ├── PartDownloader.cs
│   │   │   └── DownloadDestination.cs
│   │   ├── Queue/
│   │   │   ├── QueueManager.cs
│   │   │   └── DownloadQueue.cs
│   │   ├── Storage/
│   │   │   ├── IDownloadRepository.cs
│   │   │   ├── JsonDownloadRepository.cs
│   │   │   └── TransactionalFileSaver.cs
│   │   └── Network/
│   │       ├── ProxyManager.cs
│   │       └── HttpClientFactory.cs
│   │
│   ├── Flow.Monitor/                       # Download monitoring
│   │   ├── DownloadMonitor.cs
│   │   ├── DownloadItemState.cs
│   │   └── SpeedCalculator.cs
│   │
│   ├── Flow.Integration/                   # Browser extension server
│   │   ├── IntegrationServer.cs
│   │   ├── Controllers/
│   │   │   └── DownloadController.cs
│   │   └── Models/
│   │       └── AddDownloadRequest.cs
│   │
│   ├── Flow.Desktop/                       # Avalonia Desktop UI
│   │   ├── Views/
│   │   │   ├── MainWindow.axaml
│   │   │   ├── AddDownloadDialog.axaml
│   │   │   ├── SettingsWindow.axaml
│   │   │   ├── QueueManagementWindow.axaml
│   │   │   └── CategoryDialog.axaml
│   │   ├── ViewModels/
│   │   │   ├── MainViewModel.cs
│   │   │   ├── AddDownloadViewModel.cs
│   │   │   ├── SettingsViewModel.cs
│   │   │   └── QueueViewModel.cs
│   │   ├── Services/
│   │   │   ├── SystemTrayService.cs
│   │   │   ├── NotificationService.cs
│   │   │   └── AutoStartService.cs
│   │   ├── Themes/
│   │   │   ├── CyberDark.axaml
│   │   │   ├── Terminal.axaml
│   │   │   └── Light.axaml
│   │   └── Program.cs
│   │
│   └── Flow.Shared/                        # Shared utilities
│       ├── Configuration/
│       ├── Localization/
│       └── Utils/
│
├── tests/
│   ├── Flow.Core.Tests/
│   ├── Flow.Monitor.Tests/
│   └── Flow.Desktop.Tests/
│
└── FlowDownloadManager.sln
```

---

## PHẦN 3: MIGRATION ROADMAP (12 THÁNG)

### PHASE 1: FOUNDATION (Tháng 1-2)

#### Week 1-2: Project Setup
- [ ] Tạo solution structure
- [ ] Setup .NET 8/9 projects
- [ ] Configure Avalonia UI
- [ ] Setup DI container
- [ ] Configure build pipeline

#### Week 3-4: Core Models
- [ ] Port IDownloadItem → IDownloadItem.cs
- [ ] Port HttpDownloadItem → HttpDownloadItem.cs
- [ ] Port HLSDownloadItem → HLSDownloadItem.cs
- [ ] Port DownloadStatus enum
- [ ] Port QueueModel → QueueModel.cs
- [ ] Port Category → Category.cs

**Deliverable:** Core data models với unit tests

---

### PHASE 2: DOWNLOAD ENGINE (Tháng 3-5)

#### Week 5-8: HTTP Download Engine

**DownloadManager.cs:**
```csharp
public class DownloadManager : IDisposable
{
    private readonly HttpClient _httpClient;
    private readonly IDownloadRepository _repository;
    private readonly ConcurrentDictionary<long, HttpDownloadJob> _activeJobs;
    private readonly SemaphoreSlim _globalSemaphore;
    
    public event EventHandler<DownloadEventArgs> OnJobAdded;
    public event EventHandler<DownloadEventArgs> OnJobStarted;
    public event EventHandler<DownloadEventArgs> OnJobCompleted;
    
    public async Task<long> AddDownloadAsync(IDownloadItem item)
    {
        var id = await _repository.SaveAsync(item);
        OnJobAdded?.Invoke(this, new DownloadEventArgs(id));
        return id;
    }
    
    public async Task StartDownloadAsync(long id)
    {
        var item = await _repository.GetAsync(id);
        var job = new HttpDownloadJob(item, _httpClient);
        _activeJobs[id] = job;
        await job.StartAsync();
    }
}
```

**HttpDownloadJob.cs:**
```csharp
public class HttpDownloadJob
{
    private readonly HttpDownloadItem _item;
    private readonly HttpClient _httpClient;
    private readonly List<PartDownloader> _parts;
    private CancellationTokenSource _cts;
    private SafeFileHandle _fileHandle;
    
    public async Task StartAsync()
    {
        // 1. HEAD request để lấy Content-Length
        var response = await _httpClient.SendAsync(
            new HttpRequestMessage(HttpMethod.Head, _item.Link));
        
        var totalSize = response.Content.Headers.ContentLength ?? 0;
        var supportsRange = response.Headers.AcceptRanges.Contains("bytes");
        
        var destination = Path.Combine(_item.Folder, _item.Name);
        
        // Mở file nhị phân trực tiếp bằng SafeFileHandle hỗ trợ ghi song song không cần merge
        _fileHandle = File.OpenHandle(destination, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite, FileOptions.Asynchronous);
        
        // Phân bổ dung lượng trước cho Sparse File / Pre-allocated File
        if (totalSize > 0)
        {
            RandomAccess.SetLength(_fileHandle, totalSize);
        }
        
        if (supportsRange && totalSize > 0)
        {
            // 2. Chia thành parts
            var partCount = _item.PreferredConnectionCount ?? 8;
            var chunkSize = totalSize / partCount;
            
            for (int i = 0; i < partCount; i++)
            {
                var start = i * chunkSize;
                var end = (i == partCount - 1) ? totalSize - 1 : (start + chunkSize - 1);
                
                var part = new PartDownloader(i, start, end, _item.Link, _httpClient, _fileHandle);
                _parts.Add(part);
            }
            
            // 3. Download parallel & ghi trực tiếp qua RandomAccess.WriteAsync theo offset của từng part
            var tasks = _parts.Select(p => p.DownloadAsync(_cts.Token));
            await Task.WhenAll(tasks);
        }
    }
}
```

**Tasks:**
- [ ] Port DownloadManager.kt → DownloadManager.cs
- [ ] Port HttpDownloadJob.kt → HttpDownloadJob.cs
- [ ] Port PartDownloader.kt → PartDownloader.cs
- [ ] Implement retry logic
- [ ] Implement resume support
- [ ] Unit tests

#### Week 9-10: HLS Download Engine
- [ ] Port HLSDownloadJob.kt → HLSDownloadJob.cs
- [ ] M3U8 parser
- [ ] Segment downloader
- [ ] TS to MP4 conversion

#### Week 11-12: Queue System
- [ ] Port QueueManager.kt → QueueManager.cs
- [ ] Port DownloadQueue.kt → DownloadQueue.cs
- [ ] Scheduling logic
- [ ] Queue events

**Deliverable:** Fully functional download engine

---

### PHASE 3: STORAGE & PERSISTENCE (Tháng 6)

**DownloadListFileStorage.cs:**
```csharp
public class DownloadListFileStorage : IDownloadListDb
{
    private readonly string _downloadListFolder;
    private readonly JsonSerializerOptions _jsonOptions;
    private readonly ConcurrentDictionary<long, SemaphoreSlim> _fileLocks = new();

    public DownloadListFileStorage(string downloadListFolder, JsonSerializerOptions jsonOptions)
    {
        _downloadListFolder = downloadListFolder;
        _jsonOptions = jsonOptions;
        Directory.CreateDirectory(_downloadListFolder);
    }

    private string GetDownloadItemFile(long id) => Path.Combine(_downloadListFolder, $"{id}.json");

    public async Task<IDownloadItem?> GetByIdAsync(long id)
    {
        var filePath = GetDownloadItemFile(id);
        if (!File.Exists(filePath)) return null;

        var semaphore = _fileLocks.GetOrAdd(id, _ => new SemaphoreSlim(1, 1));
        await semaphore.WaitAsync();
        try
        {
            var json = await File.ReadAllTextAsync(filePath);
            return JsonSerializer.Deserialize<IDownloadItem>(json, _jsonOptions);
        }
        finally
        {
            semaphore.Release();
        }
    }

    public async Task SaveAsync(IDownloadItem item)
    {
        var filePath = GetDownloadItemFile(item.Id);
        var semaphore = _fileLocks.GetOrAdd(item.Id, _ => new SemaphoreSlim(1, 1));
        await semaphore.WaitAsync();
        try
        {
            var json = JsonSerializer.Serialize(item, _jsonOptions);
            var tempPath = filePath + ".tmp";
            
            // Ghi transactional (ghi ra file tạm trước rồi rename để tránh hỏng dữ liệu nếu đột ngột mất điện/crash)
            await File.WriteAllTextAsync(tempPath, json);
            File.Move(tempPath, filePath, overwrite: true);
        }
        finally
        {
            semaphore.Release();
        }
    }
}
```

**Tasks:**
- [ ] Port storage layer
- [ ] Implement TransactionalFileSaver
- [ ] Settings persistence
- [ ] Queue persistence
- [ ] Category persistence

---

### PHASE 4: MONITORING & STATE (Tháng 7)

**DownloadMonitor.cs:**
```csharp
public class DownloadMonitor
{
    private readonly ConcurrentDictionary<long, DownloadItemState> _states;
    
    public IObservable<DownloadItemState> ObserveDownload(long id)
    {
        return Observable.Create<DownloadItemState>(observer =>
        {
            // ReactiveUI observable pattern
            return Disposable.Empty;
        });
    }
    
    public void UpdateProgress(long id, long downloaded, long total)
    {
        if (_states.TryGetValue(id, out var state))
        {
            state.BytesDownloaded = downloaded;
            state.TotalBytes = total;
            state.Progress = (double)downloaded / total * 100;
            state.Speed = CalculateSpeed(id);
            state.ETA = CalculateETA(id);
        }
    }
}
```

---

### PHASE 5: INTEGRATION SERVER (Tháng 8)

**Program.cs (Integration Server):**
```csharp
var builder = WebApplication.CreateBuilder(args);
builder.Services.AddSingleton<IDownloadManager, DownloadManager>();

var app = builder.Build();

app.MapPost("/add", async (AddDownloadRequest request, IDownloadManager manager) =>
{
    var item = new HttpDownloadItem
    {
        Link = request.Url,
        Name = request.FileName,
        Folder = request.SavePath
    };
    
    var id = await manager.AddDownloadAsync(item);
    await manager.StartDownloadAsync(id);
    
    return Results.Ok(new { id });
});

app.MapGet("/queues", (IDownloadManager manager) =>
{
    return Results.Ok(manager.GetQueues());
});

app.Run("http://localhost:15151");
```

---

### PHASE 6: AVALONIA UI (Tháng 9-11)

**MainWindow.axaml:**
```xml
<Window xmlns="https://github.com/avaloniaui"
        Title="Flow Download Manager"
        Width="1200" Height="800">
    
    <DockPanel>
        <StackPanel DockPanel.Dock="Top" Orientation="Horizontal" Margin="10">
            <Button Content="Add URL" Command="{Binding AddDownloadCommand}"/>
            <Button Content="Start" Command="{Binding StartCommand}"/>
            <Button Content="Pause" Command="{Binding PauseCommand}"/>
        </StackPanel>
        
        <DataGrid Items="{Binding Downloads}" AutoGenerateColumns="False">
            <DataGrid.Columns>
                <DataGridTextColumn Header="Name" Binding="{Binding Name}" Width="*"/>
                <DataGridTextColumn Header="Size" Binding="{Binding SizeFormatted}" Width="100"/>
                <DataGridTemplateColumn Header="Progress" Width="200">
                    <DataGridTemplateColumn.CellTemplate>
                        <DataTemplate>
                            <ProgressBar Value="{Binding Progress}" Maximum="100"/>
                        </DataTemplate>
                    </DataGridTemplateColumn.CellTemplate>
                </DataGridTemplateColumn>
                <DataGridTextColumn Header="Speed" Binding="{Binding SpeedFormatted}" Width="100"/>
            </DataGrid.Columns>
        </DataGrid>
    </DockPanel>
</Window>
```

**MainViewModel.cs:**
```csharp
public class MainViewModel : ViewModelBase
{
    private readonly IDownloadManager _downloadManager;
    
    public ObservableCollection<DownloadItemViewModel> Downloads { get; }
    
    public ReactiveCommand<Unit, Unit> AddDownloadCommand { get; }
    public ReactiveCommand<Unit, Unit> StartCommand { get; }
    
    public MainViewModel(IDownloadManager downloadManager)
    {
        _downloadManager = downloadManager;
        Downloads = new ObservableCollection<DownloadItemViewModel>();
        
        AddDownloadCommand = ReactiveCommand.CreateFromTask(ShowAddDownloadDialog);
        StartCommand = ReactiveCommand.CreateFromTask(StartSelectedDownload);
        
        LoadDownloads();
    }
}
```

**Tasks:**
- [ ] Port all UI screens
- [ ] Port all ViewModels
- [ ] Implement data binding
- [ ] Port 5 themes

---

### PHASE 7: PLATFORM INTEGRATION (Tháng 12)

**SystemTrayService.cs:**
```csharp
public class SystemTrayService
{
    private readonly TrayIcon _trayIcon;
    
    public SystemTrayService()
    {
        _trayIcon = new TrayIcon
        {
            Icon = new WindowIcon("Assets/icon.ico"),
            ToolTipText = "Flow Download Manager"
        };
        
        var menu = new NativeMenu();
        menu.Add(new NativeMenuItem("Show") { Command = ShowWindowCommand });
        menu.Add(new NativeMenuItem("Exit") { Command = ExitCommand });
        
        _trayIcon.Menu = menu;
        _trayIcon.IsVisible = true;
    }
}
```

**Tasks:**
- [ ] System tray integration
- [ ] Auto-start
- [ ] Notifications
- [ ] Power actions

---

## PHẦN 4: TESTING STRATEGY

### Functional Parity Checklist

**Download Engine:**
- [ ] Single-threaded download works
- [ ] Multi-threaded download works (8 parts)
- [ ] Resume after pause works
- [ ] Speed limit works
- [ ] Retry on error works
- [ ] HLS download works

**Queue System:**
- [ ] Multiple queues work
- [ ] Max concurrent downloads respected
- [ ] Queue scheduling works

**UI:**
- [ ] All screens render correctly
- [ ] Themes switch correctly
- [ ] System tray works

### Performance Benchmarks

| Metric | Kotlin App | C# Target |
|--------|-----------|-----------|
| Memory (idle) | 150-200MB | 40-80MB |
| Memory (10 downloads) | 200-250MB | 80-120MB |
| Startup time | 1-2s | < 100ms |
| Download speed | ~50MB/s | ~50MB/s |

---

## PHẦN 5: SUCCESS CRITERIA

App được coi là 1:1 parity khi:

1. ✅ Tất cả features hoạt động giống hệt Kotlin version
2. ✅ UI/UX giống 100%
3. ✅ Performance tương đương hoặc tốt hơn
4. ✅ Memory usage < 80MB
5. ✅ Tất cả tests pass
6. ✅ Browser extension hoạt động bình thường

---

## PHẦN 6: DELIVERABLES

- [ ] Windows installer (.msi)
- [ ] macOS installer (.dmg)
- [ ] Linux package (.deb)
- [ ] Documentation
- [ ] Migration guide

---

**Timeline:** 12 tháng
**Effort:** 2-3 developers full-time
**Budget:** Estimate based on team size

