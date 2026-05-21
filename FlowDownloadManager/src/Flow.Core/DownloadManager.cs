using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Models;
using Flow.Core.Part;
using Flow.Core.Storage;
using Flow.Core.Utils;
using Flow.Shared.Utils;

namespace Flow.Core;

public class DownloadManager : IDownloadManagerMinimalControl
{
    public IDownloadListDb DlListDb { get; }
    public IDownloadPartListDb PartListDb { get; }
    public DownloadSettings Settings { get; }
    public EmptyFileCreator EmptyFileCreator { get; }
    private readonly DownloaderRegistry _downloaderRegistry;
    public string DownloadDataFolder { get; }

    private readonly ISuspendGuardedEntry _bootGuard = GuardedEntry.CreateSuspend();
    private readonly SemaphoreSlim _dbAddSync = new(1, 1);
    
    private readonly List<DownloadJob> _downloadJobs = new();
    private readonly object _jobModificationLock = new();

    public IReadOnlyList<DownloadJob> DownloadJobs
    {
        get
        {
            lock (_jobModificationLock)
            {
                return _downloadJobs.ToList();
            }
        }
    }

    private readonly ContextProvider _contextContainer = new();

    public event EventHandler<DownloadManagerEvent>? OnJobEvent;

    // Global speed limiter
    public Throttler Throttler { get; } = new();

    public DownloadManager(
        IDownloadListDb dlListDb,
        IDownloadPartListDb partListDb,
        DownloadSettings settings,
        EmptyFileCreator emptyFileCreator,
        DownloaderRegistry downloaderRegistry,
        string downloadDataFolder)
    {
        DlListDb = dlListDb ?? throw new ArgumentNullException(nameof(dlListDb));
        PartListDb = partListDb ?? throw new ArgumentNullException(nameof(partListDb));
        Settings = settings ?? throw new ArgumentNullException(nameof(settings));
        EmptyFileCreator = emptyFileCreator ?? throw new ArgumentNullException(nameof(emptyFileCreator));
        _downloaderRegistry = downloaderRegistry ?? throw new ArgumentNullException(nameof(downloaderRegistry));
        DownloadDataFolder = downloadDataFolder ?? throw new ArgumentNullException(nameof(downloadDataFolder));
    }

    public async Task AwaitBootAsync()
    {
        await _bootGuard.AwaitDoneAsync();
    }

    public async Task BootAsync()
    {
        await _bootGuard.ActionAsync(async () =>
        {
            await CreateJobForPendingDownloadsAsync();
        });
    }

    private async Task CreateJobForPendingDownloadsAsync()
    {
        var allDownloads = await DlListDb.GetAllAsync();
        foreach (var item in allDownloads)
        {
            if (item.Status != DownloadStatus.Completed)
            {
                var job = CreateJob(item);
                await job.BootAsync();
            }
        }
    }

    public async Task<long> AddDownloadAsync(NewDownloadItemProps props)
    {
        var newItem = props.DownloadItem;
        var onDuplicateStrategy = props.OnDuplicateStrategy;
        var context = props.Context;
        var extraConfig = props.ExtraConfig;

        newItem.ValidateItem();

        if (!PathValidator.IsValidPath(newItem.Folder))
        {
            throw new ArgumentException($"Folder of new download is not valid: {newItem.Folder}");
        }
        if (!PathValidator.CanWriteToThisPath(newItem.Folder))
        {
            throw new ArgumentException($"Can't write to this new download's folder: {newItem.Folder}");
        }
        if (!FileNameValidator.IsValidFileName(newItem.Name))
        {
            throw new ArgumentException($"Name of new download is not valid: {newItem.Name}");
        }

        DownloadJob job;
        await _dbAddSync.WaitAsync();
        try
        {
            var allDownloads = await DlListDb.GetAllAsync();
            var duplicateFinder = new DuplicateFilterByPath(Path.Combine(newItem.Folder, newItem.Name));
            var foundItems = allDownloads.Where(duplicateFinder.IsDuplicate).ToList();
            var removedItems = new List<IDownloadItem>();

            if (foundItems.Count > 0)
            {
                switch (onDuplicateStrategy)
                {
                    case OnDuplicateStrategy.AddNumbered:
                        // No immediate action; numbering resolved below
                        break;

                    case OnDuplicateStrategy.OverrideDownload:
                        foreach (var it in foundItems)
                        {
                            await DeleteDownloadAsync(it.Id, _ => true, new DownloadItemContext(new[] { new RemovedBy(DuplicateRemovalActor.Instance) }));
                        }
                        removedItems = foundItems;
                        break;

                    case OnDuplicateStrategy.Abort:
                        throw new InvalidOperationException("Aborting add download that already exists");
                }
            }

            string name = newItem.Name;
            var initialPath = Path.Combine(newItem.Folder, newItem.Name);

            foreach (var candidateNewFile in FileNameUtil.NumberedIfExists(initialPath))
            {
                var candidateName = Path.GetFileName(candidateNewFile);
                var candidateFolder = Path.GetDirectoryName(candidateNewFile) ?? string.Empty;

                var withSameDestination = allDownloads
                    .Where(it => !removedItems.Contains(it))
                    .FirstOrDefault(it => string.Equals(it.Name, candidateName, StringComparison.OrdinalIgnoreCase) &&
                                          string.Equals(it.Folder, candidateFolder, StringComparison.OrdinalIgnoreCase));

                if (withSameDestination == null)
                {
                    name = candidateName;
                    break;
                }
            }

            long id = (await DlListDb.GetLastIdAsync()) + 1;
            long dateAdded = newItem.DateAdded != 0 ? newItem.DateAdded : DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

            // Setup downloadItem properties on the mutable C# model
            newItem.Id = id;
            newItem.Name = name;
            newItem.DateAdded = dateAdded;
            newItem.StartTime = null;
            newItem.CompleteTime = null;
            newItem.Status = DownloadStatus.Added;

            await DlListDb.AddAsync(newItem);
            
            job = CreateJob(newItem);
            await job.BootAsync();

            if (extraConfig != null)
            {
                await job.ExtraConfigsReceivedAsync(extraConfig);
            }
        }
        finally
        {
            _dbAddSync.Release();
        }

        _contextContainer.SetContext(job.Id, context);
        OnDownloadAdded(job.DownloadItem);

        return job.Id;
    }

    private DownloadJob CreateJob(IDownloadItem downloadItem)
    {
        lock (_jobModificationLock)
        {
            var job = _downloaderRegistry.CreateJob(downloadItem, this);
            _downloadJobs.Add(job);
            return job;
        }
    }

    public async Task DeleteDownloadAsync(
        long id,
        Func<IDownloadItem, bool> alsoRemoveFile,
        DownloadItemContext? context = null)
    {
        var resolvedContext = context ?? DownloadItemContext.Empty;
        try
        {
            await PauseAsync(id, resolvedContext);
        }
        catch { }

        var itemToDelete = await DlListDb.GetByIdAsync(id);
        if (itemToDelete == null) return;

        var job = GetDownloadJob(id);
        if (job == null)
        {
            job = CreateJob(itemToDelete);
            await job.BootAsync();
        }

        _contextContainer.UpdateContext(id, ctx => ctx.Plus(resolvedContext));

        bool removeFile = itemToDelete.Status == DownloadStatus.Completed
            ? alsoRemoveFile(itemToDelete)
            : true;

        job.DownloadRemoved(removeFile);
        DeleteJob(job.Id);

        await DlListDb.RemoveAsync(itemToDelete);
        await PartListDb.RemovePartsAsync(id);

        OnJobRemoved(itemToDelete, _contextContainer.GetContext(id));
        _contextContainer.RemoveContext(id);
    }

    private void DeleteJob(long id)
    {
        lock (_jobModificationLock)
        {
            var jobToDelete = _downloadJobs.FirstOrDefault(j => j.Id == id);
            if (jobToDelete != null)
            {
                jobToDelete.Close();
                _downloadJobs.Remove(jobToDelete);
            }
        }
    }

    public async Task PauseAsync(long id, DownloadItemContext? context = null)
    {
        var resolvedContext = context ?? DownloadItemContext.Empty;
        var job = GetDownloadJob(id);
        if (job != null)
        {
            _contextContainer.UpdateContext(id, ctx => ctx.Plus(resolvedContext));
            await job.PauseAsync();
        }
    }

    public async Task ResumeAsync(long id, DownloadItemContext? context = null)
    {
        var resolvedContext = context ?? DownloadItemContext.Empty;
        var job = GetDownloadJob(id);
        if (job == null)
        {
            var item = await DlListDb.GetByIdAsync(id);
            if (item != null)
            {
                job = CreateJob(item);
            }
        }

        if (job != null)
        {
            _contextContainer.UpdateContext(id, ctx => ctx.Plus(resolvedContext));
            await job.ResumeAsync();
        }
    }

    public async Task ResetAsync(long id, DownloadItemContext? context = null)
    {
        var resolvedContext = context ?? DownloadItemContext.Empty;
        var job = GetDownloadJob(id);
        if (job == null)
        {
            var item = await DlListDb.GetByIdAsync(id);
            if (item != null)
            {
                job = CreateJob(item);
            }
        }

        if (job != null)
        {
            _contextContainer.UpdateContext(id, ctx => ctx.Plus(resolvedContext));
            await job.ResetAsync();
        }
    }

    private DownloadJob? GetDownloadJob(long id)
    {
        lock (_jobModificationLock)
        {
            return _downloadJobs.FirstOrDefault(j => j.Id == id);
        }
    }

    public async Task<List<IDownloadItem>> GetDownloadListAsync()
    {
        return await DlListDb.GetAllAsync();
    }

    public void OnDownloadResuming(IDownloadItem downloadItem)
    {
        var context = _contextContainer.GetContext(downloadItem.Id);
        OnJobEvent?.Invoke(this, new JobStartingEvent(downloadItem, context));
    }

    public void OnDownloadResumed(IDownloadItem downloadItem)
    {
        var context = _contextContainer.GetContext(downloadItem.Id);
        OnJobEvent?.Invoke(this, new JobStartedEvent(downloadItem, context));
    }

    public void OnDownloadAdded(IDownloadItem downloadItem)
    {
        var context = _contextContainer.GetContext(downloadItem.Id);
        OnJobEvent?.Invoke(this, new JobAddedEvent(downloadItem, context));
    }

    public void OnDownloadCanceled(IDownloadItem downloadItem, Exception throwable)
    {
        var context = _contextContainer.GetContext(downloadItem.Id);
        OnJobEvent?.Invoke(this, new JobCanceledEvent(downloadItem, context, throwable));
    }

    public void OnDownloadFinished(IDownloadItem downloadItem)
    {
        Task.Run(() =>
        {
            var context = _contextContainer.GetContext(downloadItem.Id);
            OnJobEvent?.Invoke(this, new JobCompletedEvent(downloadItem, context));
            DeleteJob(downloadItem.Id);
        });
    }

    public void OnDownloadItemChange(IDownloadItem downloadItem)
    {
        Task.Run(() =>
        {
            var context = _contextContainer.GetContext(downloadItem.Id);
            OnJobEvent?.Invoke(this, new JobChangedEvent(downloadItem, context));
        });
    }

    private void OnJobRemoved(IDownloadItem downloadItem, DownloadItemContext context)
    {
        OnJobEvent?.Invoke(this, new JobRemovedEvent(downloadItem, context));
    }

    public async Task StartJobAsync(long id, DownloadItemContext? context = null)
    {
        await ResumeAsync(id, context);
    }

    public async Task StopJobAsync(long id, DownloadItemContext? context = null)
    {
        await PauseAsync(id, context);
    }

    public bool CanActivateJob(long id)
    {
        var job = GetDownloadJob(id);
        return job != null && job.Status.CanBeResumed;
    }

    public async Task StopAllAsync(DownloadItemContext? context = null)
    {
        var activeJobs = DownloadJobs.Where(j => j.IsDownloadActive).ToList();
        var tasks = activeJobs.Select(j => PauseAsync(j.Id, context));
        await Task.WhenAll(tasks);
    }

    public int GetActiveCount()
    {
        return DownloadJobs.Count(j => j.IsDownloadActive);
    }

    public string CalculateOutputFile(IDownloadItem downloadItem)
    {
        return Path.Combine(downloadItem.Folder, downloadItem.Name);
    }

    public DownloadJobStatus? GetJobStatusOf(long id)
    {
        return GetDownloadJob(id)?.Status;
    }

    public void LimitGlobalSpeed(long bytePerSecond)
    {
        Throttler.BytesPerSecond = bytePerSecond;
    }

    public void ReloadSetting()
    {
        foreach (var job in DownloadJobs)
        {
            job.ReloadSettings();
        }
    }

    public async Task UpdateDownloadItemAsync(
        long id,
        IDownloadJobExtraConfig? downloadJobExtraConfig,
        Action<IDownloadItem> updater)
    {
        bool wasCreated = false;
        var job = GetDownloadJob(id);
        if (job == null)
        {
            var item = await DlListDb.GetByIdAsync(id);
            if (item != null)
            {
                wasCreated = true;
                job = CreateJob(item);
            }
        }

        if (job == null) return;

        var updated = await job.ChangeConfigAsync(updater, downloadJobExtraConfig);
        if (wasCreated && updated.Status == DownloadStatus.Completed)
        {
            DeleteJob(job.Id);
        }
        OnDownloadItemChange(updated);
    }
}

internal class ContextProvider
{
    private readonly Dictionary<long, DownloadItemContext> _contexts = new();
    private readonly object _lock = new();

    public DownloadItemContext GetContext(long id)
    {
        lock (_lock)
        {
            return _contexts.TryGetValue(id, out var context) ? context : DownloadItemContext.Empty;
        }
    }

    public void SetContext(long id, DownloadItemContext context)
    {
        lock (_lock)
        {
            if (context == DownloadItemContext.Empty)
            {
                _contexts.Remove(id);
                return;
            }
            _contexts[id] = context;
        }
    }

    public void RemoveContext(long id)
    {
        lock (_lock)
        {
            _contexts.Remove(id);
        }
    }

    public void UpdateContext(long id, Func<DownloadItemContext, DownloadItemContext> block)
    {
        lock (_lock)
        {
            SetContext(id, block(GetContext(id)));
        }
    }
}
