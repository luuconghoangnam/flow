using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core;
using Flow.Core.Models;
using Flow.Core.Queue;

namespace Flow.Monitor;

public class DownloadMonitor : IDownloadMonitor, IDisposable
{
    private readonly DownloadManager _downloadManager;
    private readonly ManualDownloadQueue _manualDownloadQueue;
    private readonly Lazy<IDownloadItemStateFactory<IDownloadItem, DownloadJob>> _downloadItemStateFactory;

    private readonly CancellationTokenSource _cts = new();
    private readonly object _lock = new();

    private bool _useAverageSpeed;
    public bool UseAverageSpeed
    {
        get
        {
            lock (_lock) return _useAverageSpeed;
        }
        set
        {
            lock (_lock)
            {
                if (_useAverageSpeed == value) return;
                _useAverageSpeed = value;
            }
        }
    }

    private List<IProcessingDownloadItemState> _activeDownloadList = new();
    public IReadOnlyList<IProcessingDownloadItemState> ActiveDownloadList
    {
        get
        {
            lock (_lock) return _activeDownloadList.ToList();
        }
    }

    private List<CompletedDownloadItemState> _completedDownloadList = new();
    public IReadOnlyList<CompletedDownloadItemState> CompletedDownloadList
    {
        get
        {
            lock (_lock) return _completedDownloadList.ToList();
        }
    }

    public IReadOnlyList<IDownloadItemState> DownloadList
    {
        get
        {
            lock (_lock)
            {
                var list = new List<IDownloadItemState>();
                list.AddRange(_activeDownloadList);
                list.AddRange(_completedDownloadList);
                return list;
            }
        }
    }

    private int _activeDownloadCount;
    public int ActiveDownloadCount
    {
        get
        {
            lock (_lock) return _activeDownloadCount;
        }
    }

    public event EventHandler? OnActiveDownloadListChanged;
    public event EventHandler? OnCompletedDownloadListChanged;
    public event EventHandler? OnDownloadListChanged;
    public event EventHandler? OnActiveDownloadCountChanged;

    private readonly object _speedLock = new();
    private Dictionary<long, long> _downloadSpeeds = new();
    private Dictionary<long, long> _averageSpeeds = new();
    private readonly Queue<Dictionary<long, long>> _speedHistory = new();

    public DownloadMonitor(
        DownloadManager downloadManager,
        ManualDownloadQueue manualDownloadQueue,
        Lazy<IDownloadItemStateFactory<IDownloadItem, DownloadJob>> downloadItemStateFactory)
    {
        _downloadManager = downloadManager ?? throw new ArgumentNullException(nameof(downloadManager));
        _manualDownloadQueue = manualDownloadQueue ?? throw new ArgumentNullException(nameof(manualDownloadQueue));
        _downloadItemStateFactory = downloadItemStateFactory ?? throw new ArgumentNullException(nameof(downloadItemStateFactory));

        // Start background tasks
        _ = StartLifecycleAsync();
    }

    private async Task StartLifecycleAsync()
    {
        try
        {
            await _downloadManager.AwaitBootAsync();
            
            // Populate initial completed downloads
            var allDownloads = await _downloadManager.GetDownloadListAsync();
            var completedInitial = allDownloads
                .Where(it => it.Status == DownloadStatus.Completed)
                .Select(it => _downloadItemStateFactory.Value.CreateCompletedDownloadItemState(it))
                .ToList();

            lock (_lock)
            {
                _completedDownloadList = completedInitial;
            }
            OnCompletedDownloadListChanged?.Invoke(this, EventArgs.Empty);
            OnDownloadListChanged?.Invoke(this, EventArgs.Empty);

            // Start background loops
            _ = SpeedMeterLoopAsync(_cts.Token);
            _ = ActiveListUpdateLoopAsync(_cts.Token);

            // Register events
            _downloadManager.OnJobEvent += HandleJobEvent;
            _manualDownloadQueue.OnQueueChanged += HandleQueueChanged;
        }
        catch (Exception)
        {
            // Fail-safe initialization
        }
    }

    private void HandleQueueChanged()
    {
        TriggerActiveListUpdate();
    }

    private void HandleJobEvent(object? sender, DownloadManagerEvent ev)
    {
        switch (ev)
        {
            case JobCompletedEvent completedEv:
                var item = _downloadItemStateFactory.Value.CreateCompletedDownloadItemState(completedEv.DownloadItem);
                lock (_lock)
                {
                    var existing = _completedDownloadList.FirstOrDefault(c => c.Id == item.Id);
                    if (existing != null)
                    {
                        _completedDownloadList = _completedDownloadList.Select(c => c.Id == item.Id ? item : c).ToList();
                    }
                    else
                    {
                        _completedDownloadList = _completedDownloadList.Concat(new[] { item }).ToList();
                    }
                }
                OnCompletedDownloadListChanged?.Invoke(this, EventArgs.Empty);
                OnDownloadListChanged?.Invoke(this, EventArgs.Empty);
                break;

            case JobRemovedEvent removedEv:
                lock (_lock)
                {
                    _completedDownloadList = _completedDownloadList.Where(c => c.Id != removedEv.DownloadItem.Id).ToList();
                }
                OnCompletedDownloadListChanged?.Invoke(this, EventArgs.Empty);
                OnDownloadListChanged?.Invoke(this, EventArgs.Empty);
                break;

            case JobChangedEvent changedEv:
                bool isCompleted = changedEv.DownloadItem.Status == DownloadStatus.Completed;
                lock (_lock)
                {
                    if (isCompleted)
                    {
                        var newItem = _downloadItemStateFactory.Value.CreateCompletedDownloadItemState(changedEv.DownloadItem);
                        var existing = _completedDownloadList.FirstOrDefault(c => c.Id == newItem.Id);
                        if (existing != null)
                        {
                            _completedDownloadList = _completedDownloadList.Select(c => c.Id == newItem.Id ? newItem : c).ToList();
                        }
                        else
                        {
                            _completedDownloadList = _completedDownloadList.Concat(new[] { newItem }).ToList();
                        }
                    }
                    else
                    {
                        _completedDownloadList = _completedDownloadList.Where(c => c.Id != changedEv.DownloadItem.Id).ToList();
                    }
                }
                OnCompletedDownloadListChanged?.Invoke(this, EventArgs.Empty);
                OnDownloadListChanged?.Invoke(this, EventArgs.Empty);
                break;
        }

        // Active download count and list update
        UpdateActiveCount();
        TriggerActiveListUpdate();
    }

    private void UpdateActiveCount()
    {
        int currentCount = _downloadManager.GetActiveCount();
        bool changed = false;
        lock (_lock)
        {
            if (_activeDownloadCount != currentCount)
            {
                _activeDownloadCount = currentCount;
                changed = true;
            }
        }
        if (changed)
        {
            OnActiveDownloadCountChanged?.Invoke(this, EventArgs.Empty);
        }
    }

    private void TriggerActiveListUpdate()
    {
        try
        {
            var activeJobs = _downloadManager.DownloadJobs.Where(j => j.Status.State != DownloadJobState.Finished).ToList();
            var pendingIds = _manualDownloadQueue.PendingItems.ToHashSet();

            var newList = new List<IProcessingDownloadItemState>();

            foreach (var job in activeJobs)
            {
                long speed = 0;
                if (job.Status.IsActive)
                {
                    speed = GetSpeedOf(job.Id);
                }

                bool isWaiting = pendingIds.Contains(job.Id);

                var state = _downloadItemStateFactory.Value.CreateProcessingDownloadItemState(
                    new ProcessingDownloadItemFactoryInputs<DownloadJob>(
                        downloadJob: job,
                        speed: speed,
                        isWaiting: isWaiting
                    )
                );

                newList.Add(state);
            }

            lock (_lock)
            {
                _activeDownloadList = newList;
            }

            OnActiveDownloadListChanged?.Invoke(this, EventArgs.Empty);
            OnDownloadListChanged?.Invoke(this, EventArgs.Empty);
        }
        catch (Exception)
        {
            // Avoid crash during update
        }
    }

    private long GetSpeedOf(long id)
    {
        lock (_speedLock)
        {
            var targetDict = UseAverageSpeed ? _averageSpeeds : _downloadSpeeds;
            return targetDict.TryGetValue(id, out long val) ? val : 0;
        }
    }

    private async Task SpeedMeterLoopAsync(CancellationToken token)
    {
        var lastWrites = new Dictionary<long, long>();

        while (!token.IsCancellationRequested)
        {
            try
            {
                var newWrites = new Dictionary<long, long>();
                var currentSpeeds = new Dictionary<long, long>();

                var activeJobs = _downloadManager.DownloadJobs;
                foreach (var job in activeJobs)
                {
                    long size = job.GetDownloadedSize();
                    newWrites[job.Id] = size;

                    if (lastWrites.TryGetValue(job.Id, out long lastVal))
                    {
                        if (size < lastVal)
                        {
                            currentSpeeds[job.Id] = size;
                        }
                        else
                        {
                            currentSpeeds[job.Id] = size - lastVal;
                        }
                    }
                    else
                    {
                        currentSpeeds[job.Id] = 0;
                    }
                }

                lastWrites = newWrites;

                lock (_speedLock)
                {
                    _downloadSpeeds = currentSpeeds;

                    // History
                    _speedHistory.Enqueue(new Dictionary<long, long>(currentSpeeds));
                    if (_speedHistory.Count > 5)
                    {
                        _speedHistory.Dequeue();
                    }

                    // Average speeds
                    var avgSpeeds = new Dictionary<long, long>();
                    if (_speedHistory.Count > 0)
                    {
                        var allActiveIds = _speedHistory.SelectMany(h => h.Keys).Distinct();
                        foreach (var id in allActiveIds)
                        {
                            var historyForId = _speedHistory
                                .Select(h => h.TryGetValue(id, out long val) ? val : (long?)null)
                                .Where(val => val.HasValue)
                                .Select(val => val!.Value)
                                .ToList();

                            if (historyForId.Count > 0)
                            {
                                avgSpeeds[id] = (long)historyForId.Average();
                            }
                        }
                    }
                    _averageSpeeds = avgSpeeds;
                }

                TriggerActiveListUpdate();
            }
            catch (Exception)
            {
                // Silence loop errors
            }

            try
            {
                await Task.Delay(1000, token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private async Task ActiveListUpdateLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            TriggerActiveListUpdate();
            UpdateActiveCount();

            try
            {
                await Task.Delay(500, token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    public async Task WaitForDownloadToFinishOrCancelAsync(long id)
    {
        var tcs = new TaskCompletionSource<bool>();

        EventHandler<DownloadManagerEvent>? handler = null;
        handler = (sender, ev) =>
        {
            if (ev.DownloadItem.Id == id)
            {
                switch (ev)
                {
                    case JobCompletedEvent:
                    case JobRemovedEvent:
                        tcs.TrySetResult(true);
                        break;
                    case JobCanceledEvent cancelEv:
                        tcs.TrySetException(cancelEv.Exception);
                        break;
                }
            }
        };

        _downloadManager.OnJobEvent += handler;

        try
        {
            // Verify if it is already completed
            var completedList = CompletedDownloadList;
            if (completedList.Any(c => c.Id == id))
            {
                return;
            }

            // Verify if it still exists as active
            var activeJobs = _downloadManager.DownloadJobs;
            if (!activeJobs.Any(j => j.Id == id))
            {
                return;
            }

            await tcs.Task;
        }
        finally
        {
            _downloadManager.OnJobEvent -= handler;
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _cts.Dispose();

        _downloadManager.OnJobEvent -= HandleJobEvent;
        _manualDownloadQueue.OnQueueChanged -= HandleQueueChanged;
    }
}
