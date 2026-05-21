using System;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Models;
using Flow.Core.Storage;
using Flow.Shared.Utils;

namespace Flow.Core.Models;

public abstract class DownloadJob
{
    public DownloadManager DownloadManager { get; }
    
    private bool _isDownloadActive;
    public bool IsDownloadActive => _isDownloadActive;

    public abstract IDownloadItem DownloadItem { get; }
    public long Id => DownloadItem.Id;

    protected readonly CancellationTokenSource _jobCts = new();
    protected CancellationTokenSource? _activeCts;
    
    private readonly ISuspendGuardedEntry _booted = GuardedEntry.CreateSuspend();

    private DownloadJobStatus _status = DownloadJobStatus.Idle;
    public DownloadJobStatus Status
    {
        get => _status;
        private set
        {
            if (_status != value)
            {
                _status = value;
                OnStatusChanged?.Invoke(this, value);
            }
        }
    }

    public event EventHandler<DownloadJobStatus>? OnStatusChanged;

    protected DownloadJob(DownloadManager downloadManager)
    {
        DownloadManager = downloadManager;
    }

    public async Task BootAsync()
    {
        await _booted.ActionAsync(async () =>
        {
            await ActualBootAsync();
        });
    }

    protected abstract Task ActualBootAsync();
    public abstract void InitializeDestination();
    public abstract Task ResetAsync();
    public abstract Task ResumeAsync();
    public abstract Task PauseAsync(Exception? throwable = null);
    public abstract Task SaveStateAsync();
    
    public abstract DownloadDestination GetDestination();

    protected void EnsureBooted()
    {
        if (!_booted.IsDone())
        {
            throw new InvalidOperationException("DownloadJob is not booted! Call BootAsync() before using this object.");
        }
    }

    protected void StartAutoSaver(CancellationToken cancellationToken)
    {
        Task.Run(async () =>
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                try
                {
                    await Task.Delay(1000, cancellationToken);
                    await SaveStateAsync();
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch
                {
                    // Ignore errors in auto saver
                }
            }
        }, cancellationToken);
    }

    protected void UpdateStatus(DownloadJobStatus newStatus)
    {
        Status = newStatus;
    }

    protected void OnDownloadResuming()
    {
        UpdateStatus(DownloadJobStatus.Resuming);
        DownloadManager.OnDownloadResuming(DownloadItem);
    }

    protected void OnDownloadResumed()
    {
        UpdateStatus(DownloadJobStatus.Downloading);
        DownloadManager.OnDownloadResumed(DownloadItem);
    }

    protected async Task OnDownloadCanceledAsync(Exception throwable)
    {
        UpdateStatus(DownloadJobStatus.FromCanceled(throwable));
        if (IsNormalCancellation(throwable))
        {
            DownloadItem.Status = DownloadStatus.Paused;
        }
        else
        {
            DownloadItem.Status = DownloadStatus.Error;
        }
        _isDownloadActive = false;
        await SaveStateAsync();
        DownloadManager.OnDownloadCanceled(DownloadItem, throwable);
    }

    protected void OnDownloadFinished()
    {
        // run completion handler asynchronously
        Task.Run(async () =>
        {
            try
            {
                // In C#, OnAllPartsCompleted takes an Action<int?>
                GetDestination().OnAllPartsCompleted(percent =>
                {
                    UpdateStatus(DownloadJobStatus.FromPreparingFile(percent));
                });
            }
            catch (Exception e)
            {
                await PauseAsync(e);
                return;
            }

            DownloadItem.Status = DownloadStatus.Completed;
            DownloadItem.CompleteTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            UpdateStatus(DownloadJobStatus.Finished);
            _isDownloadActive = false;
            OnDownloadFinishedBeforeSave();
            await SaveStateAsync();
            DownloadManager.OnDownloadFinished(DownloadItem);
        });
    }

    public virtual void OnDownloadFinishedBeforeSave() { }

    public abstract long GetDownloadedSize();

    public void DownloadRemoved(bool removeOutputFile = true)
    {
        EnsureBooted();
        GetDestination().CleanUpJunkFiles();
        if (removeOutputFile)
        {
            GetDestination().DeleteOutputFile();
        }
    }

    public abstract void ReloadSettings();

    public void Close()
    {
        try
        {
            _activeCts?.Cancel();
            _activeCts?.Dispose();
        }
        catch { }
        try
        {
            _jobCts.Cancel();
            _jobCts.Dispose();
        }
        catch { }
    }

    public abstract Task<IDownloadItem> ChangeConfigAsync(
        Action<IDownloadItem> updater,
        IDownloadJobExtraConfig? extraConfig);

    public abstract Task ExtraConfigsReceivedAsync(IDownloadJobExtraConfig config);

    protected static bool IsNormalCancellation(Exception e)
    {
        return e is OperationCanceledException;
    }

    protected void SetActive(bool active)
    {
        _isDownloadActive = active;
    }
}
