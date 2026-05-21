using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Connection;
using Flow.Core.Exceptions;
using Flow.Core.Models;
using Flow.Core.Part;
using Flow.Core.Storage;

namespace Flow.Core.Models;

public class HttpDownloadJob : DownloadJob
{
    private readonly List<RangedPart> _parts = new();
    private SimpleDownloadDestination? _destination;
    
    private readonly HttpDownloaderClient _client;
    private readonly Throttler _jobThrottler = new();
    
    private bool? _supportsConcurrent;
    public bool? SupportsConcurrent => _supportsConcurrent;
    
    private long? _serverLastModified;
    public long? ServerLastModified => _serverLastModified;

    private readonly SemaphoreSlim _itemSaveLock = new(1, 1);
    private readonly SemaphoreSlim _partLock = new(1, 1);
    private readonly SemaphoreSlim _partLoopLock = new(1, 1);
    private readonly SemaphoreSlim _retryLock = new(1, 1);
    
    private readonly object _partSplitLock = new();
    private readonly Dictionary<long, HttpPartDownloader> _partDownloaderList = new();
    
    private bool _strictDownload = true;
    private long _downloadedSizeBeforeRetry = 0;
    private int _failedDownloadTries = 0;
    private const long _delayForEachRetry = 3000;
    
    public override IDownloadItem DownloadItem { get; }

    public HttpDownloadJob(HttpDownloadItem downloadItem, DownloadManager downloadManager, HttpDownloaderClient client)
        : base(downloadManager)
    {
        DownloadItem = downloadItem ?? throw new ArgumentNullException(nameof(downloadItem));
        _client = client ?? throw new ArgumentNullException(nameof(client));
    }

    public override DownloadDestination GetDestination()
    {
        if (_destination == null)
        {
            throw new InvalidOperationException("Destination not initialized.");
        }
        return _destination;
    }

    protected override async Task ActualBootAsync()
    {
        InitializeDestination();
        await LoadPartStateAsync();
        
        _supportsConcurrent = _parts.Count switch
        {
            >= 2 => true,
            _ => null
        };
        
        ApplySpeedLimit();
        _downloadedSizeBeforeRetry = GetDownloadedSize();
    }

    public override void InitializeDestination()
    {
        var outFile = DownloadManager.CalculateOutputFile(DownloadItem);
        _destination = new SimpleDownloadDestination(
            outFile,
            DownloadManager.Settings.AppendExtensionToIncompleteDownloads,
            Id,
            DownloadManager.EmptyFileCreator
        );
    }

    private void SetParts(List<RangedPart> list)
    {
        lock (_parts)
        {
            _parts.Clear();
            foreach (var part in list)
            {
                if (part.IsCompleted)
                {
                    part.Status = PartDownloadStatus.Completed;
                }
                _parts.Add(part);
            }
        }
    }

    private async Task LoadPartStateAsync()
    {
        await _partLock.WaitAsync();
        try
        {
            var rangedParts = await DownloadManager.PartListDb.GetPartsAsync(Id) as RangedParts;
            SetParts(rangedParts?.List ?? new List<RangedPart>());
        }
        finally
        {
            _partLock.Release();
        }
    }

    public override async Task ResetAsync()
    {
        await PauseAsync();
        ClearPartDownloaderList();
        SetParts(new List<RangedPart>());
        DownloadItem.ContentLength = IDownloadItem.LengthUnknown;
        ((HttpDownloadItem)DownloadItem).ServerETag = null;
        DownloadItem.Status = DownloadStatus.Added;
        DownloadItem.StartTime = null;
        DownloadItem.CompleteTime = null;
        _strictDownload = true;
        _downloadedSizeBeforeRetry = 0;
        await SaveStateAsync();
        DownloadManager.OnDownloadItemChange(DownloadItem);
    }

    public override async Task ResumeAsync()
    {
        if (IsDownloadActive) return;
        
        SetActive(true);
        _activeCts?.Cancel();
        _activeCts = new CancellationTokenSource();
        
        // Run resume workflow asynchronously without blocking the call
        _ = Task.Run(async () => await ResumeWithNewScopeAsync(_activeCts.Token, true));
        await Task.CompletedTask;
    }

    private async Task ResumeWithNewScopeAsync(CancellationToken cancellationToken, bool isInFirstResume)
    {
        try
        {
            await BootAsync();
            
            bool allDone = false;
            lock (_parts)
            {
                allDone = _parts.Count > 0 && _parts.All(p => p.IsCompleted);
            }
            
            if (allDone)
            {
                OnDownloadFinished();
                return;
            }

            OnDownloadResuming();

            await FetchDownloadInfoAndValidateAsync(cancellationToken);
            await CreatePartsIfNotCreatedAsync();
            
            await PrepareDestinationAsync(percent =>
            {
                UpdateStatus(DownloadJobStatus.FromPreparingFile(percent));
            });

            CreatePartDownloaderList();
            BeginDownloadParts();
            StartAutoSaver(cancellationToken);

            DownloadItem.Status = DownloadStatus.Downloading;
            if (!DownloadItem.StartTime.HasValue)
            {
                DownloadItem.StartTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            }

            await SaveStateAsync();
            OnDownloadResumed();
        }
        catch (Exception e)
        {
            if (IsNormalCancellation(e) || (e is DownloadValidationException valEx && valEx.IsCritical))
            {
                await PauseAsync(e);
            }
            else
            {
                await DownloadFailedRetryOrPauseAsync(e, isInFirstResume);
            }
        }
    }

    private async Task PrepareDestinationAsync(Action<int?> onProgressUpdate)
    {
        if (_destination == null) throw new InvalidOperationException("Destination not initialized.");
        
        _destination.OutputSize = _strictDownload && _supportsConcurrent != false 
            ? DownloadItem.ContentLength 
            : IDownloadItem.LengthUnknown;

        try
        {
            _destination.PrepareDestinationFolder();
        }
        catch (Exception e)
        {
            throw new PrepareDestinationFailedException(e);
        }

        if (!await _destination.IsDownloadedPartsIsValidAsync())
        {
            lock (_parts)
            {
                foreach (var part in _parts)
                {
                    part.ResetCurrent();
                }
            }
            await SaveStateAsync();
        }

        try
        {
            await _destination.PrepareFileAsync(onProgressUpdate);
        }
        catch (Exception e)
        {
            throw new PrepareDestinationFailedException(e);
        }

        if (DownloadManager.Settings.UseServerLastModifiedTime && _serverLastModified.HasValue)
        {
            _destination.SetLastModified(_serverLastModified.Value);
        }
    }

    public override long GetDownloadedSize()
    {
        lock (_parts)
        {
            return _parts.Sum(p => p.HowMuchProceed());
        }
    }

    private void OnPreferredConnectionCountChanged()
    {
        BeginDownloadParts();
    }

    public override async Task<IDownloadItem> ChangeConfigAsync(
        Action<IDownloadItem> updater,
        IDownloadJobExtraConfig? extraConfig)
    {
        await BootAsync();
        
        var previousItem = new HttpDownloadItem();
        previousItem.ApplyFrom((HttpDownloadItem)DownloadItem);

        var newItem = new HttpDownloadItem();
        newItem.ApplyFrom((HttpDownloadItem)DownloadItem);
        updater(newItem);

        var previousDestination = DownloadManager.CalculateOutputFile(previousItem);
        var newDestination = DownloadManager.CalculateOutputFile(newItem);
        bool shouldUpdateDestination = previousDestination != newDestination;

        if (shouldUpdateDestination)
        {
            if (IsDownloadActive)
            {
                await PauseAsync();
            }
            GetDestination().MoveOutput(newDestination);
        }

        ((HttpDownloadItem)DownloadItem).ApplyFrom(newItem);

        if (shouldUpdateDestination)
        {
            InitializeDestination();
        }

        if (previousItem.PreferredConnectionCount != DownloadItem.PreferredConnectionCount)
        {
            OnPreferredConnectionCountChanged();
        }

        if (previousItem.Link != DownloadItem.Link)
        {
            OnLinkChanged();
        }

        ApplySpeedLimit();

        if (extraConfig != null)
        {
            await ExtraConfigsReceivedAsync(extraConfig);
        }

        await SaveDownloadItemAsync();
        return DownloadItem;
    }

    private void ApplySpeedLimit()
    {
        _jobThrottler.BytesPerSecond = DownloadItem.SpeedLimit;
    }

    private void OnLinkChanged()
    {
        if (_activeCts != null && !_activeCts.IsCancellationRequested)
        {
            Task.Run(async () =>
            {
                await PauseAsync();
                await ResumeAsync();
            });
        }
    }

    public int GetRequestedPartitionCount()
    {
        return DownloadItem.PreferredConnectionCount ?? DownloadManager.Settings.DefaultThreadCount;
    }

    private async Task CreatePartsIfNotCreatedAsync()
    {
        lock (_parts)
        {
            if (_parts.Count > 0) return;
        }

        var newParts = new List<RangedPart>();
        if (DownloadItem.ContentLength == IDownloadItem.LengthUnknown)
        {
            newParts.Add(new RangedPart(0, null, 0));
        }
        else
        {
            if (_supportsConcurrent == true)
            {
                var ranges = RangeSplitter.SplitToRange(
                    DownloadItem.ContentLength,
                    DownloadManager.Settings.MinPartSize,
                    GetRequestedPartitionCount()
                );
                foreach (var range in ranges)
                {
                    newParts.Add(new RangedPart(range.Start, range.End));
                }
            }
            else
            {
                long toVal = DownloadItem.ContentLength - 1;
                newParts.Add(new RangedPart(0, toVal >= 0 ? toVal : 0, 0));
            }
        }

        SetParts(newParts);
        await SaveStateAsync();
    }

    private void BeginDownloadParts()
    {
        if (_activeCts == null || _activeCts.IsCancellationRequested) return;
        
        Task.Run(async () =>
        {
            if (!await _partLoopLock.WaitAsync(0)) return;
            try
            {
                var activeCount = GetPartDownloaderList().Count(d => d.Active);
                int howMuchCreate = GetRequestedPartitionCount() - activeCount;
                
                if (howMuchCreate > 0)
                {
                    var mutableInactive = GetPartDownloaderList()
                        .Where(d => !d.Active && !d.Part.IsCompleted)
                        .OrderBy(d => d.Part.From)
                        .ToList();

                    Func<HttpPartDownloader?> getPartDownloader = () =>
                    {
                        if (mutableInactive.Count > 0)
                        {
                            var inactivePart = mutableInactive[0];
                            mutableInactive.RemoveAt(0);
                            return inactivePart;
                        }
                        if (_supportsConcurrent == true && DownloadManager.Settings.DynamicPartCreationMode)
                        {
                            lock (_partSplitLock)
                            {
                                var candidates = GetPartDownloaderList()
                                    .Where(d => d.CanBeSplit())
                                    .OrderByDescending(d => d.Part.RemainingLength ?? 0)
                                    .ToList();
                                    
                                foreach (var i in candidates)
                                {
                                    var newPart = i.SplitPart();
                                    if (newPart != null)
                                    {
                                        lock (_parts)
                                        {
                                            _parts.Add(newPart);
                                            _parts.Sort((a, b) => a.From.CompareTo(b.From));
                                        }
                                        return GetOrCreatePartDownloader(newPart);
                                    }
                                }
                            }
                        }
                        return null;
                    };

                    for (int i = 0; i < howMuchCreate; i++)
                    {
                        var pd = getPartDownloader();
                        if (pd == null) break;
                        if (pd.Part.IsCompleted) continue;
                        pd.Start();
                    }
                }
                else if (howMuchCreate < 0)
                {
                    var activeToStop = GetPartDownloaderList()
                        .Where(d => d.Active)
                        .OrderByDescending(d => d.Part.From)
                        .Take(-howMuchCreate)
                        .ToList();

                    foreach (var pd in activeToStop)
                    {
                        pd.StopDownloader();
                    }

                    foreach (var pd in activeToStop)
                    {
                        await pd.JoinAsync();
                        await pd.AwaitIdleAsync();
                    }
                }
            }
            catch (Exception)
            {
                // Silence unexpected thread errors in part downloader scheduling loop
            }
            finally
            {
                _partLoopLock.Release();
            }
        });
    }

    private void OnPartHaveToManyError(Exception throwable)
    {
        bool paused = false;
        if (throwable is DownloadValidationException valEx && valEx.IsCritical)
        {
            paused = true;
            _ = PauseAsync(throwable);
        }

        var allHaveError = GetPartDownloaderList()
            .Where(d => d.Active)
            .All(d => d.Injured());

        if (allHaveError && !paused)
        {
            _ = DownloadFailedRetryOrPauseAsync(throwable, false);
        }
    }

    public int GetMaxAllowedRetries()
    {
        return DownloadManager.Settings.MaxDownloadRetryCount;
    }

    private async Task DownloadFailedRetryOrPauseAsync(Exception e, bool isInFirstResume)
    {
        if (isInFirstResume && _failedDownloadTries == 0 && ShouldRetryIfInitialFailed())
        {
            if (e is not DownloadValidationException)
            {
                await PauseAsync(e);
                return;
            }
        }

        if (e is DownloadValidationException valEx && valEx.IsCritical)
        {
            await PauseAsync(e);
            return;
        }

        long downloadedSize = GetDownloadedSize();
        if (downloadedSize > _downloadedSizeBeforeRetry)
        {
            _failedDownloadTries = 0;
        }
        else
        {
            _failedDownloadTries++;
        }
        _downloadedSizeBeforeRetry = downloadedSize;

        int retriedCount = Math.Max(0, _failedDownloadTries - 1);
        if (retriedCount < GetMaxAllowedRetries())
        {
            await RetryAsync(isInFirstResume);
        }
        else
        {
            await PauseAsync(new TooManyErrorException(e));
        }
    }

    private async Task RetryAsync(bool isInFirstResume)
    {
        await _retryLock.WaitAsync();
        try
        {
            await SaveStateAsync();
            if (_activeCts != null)
            {
                _activeCts.Cancel();
                _activeCts = null;
            }
            await StopAllPartsAsync();
            UpdateStatus(DownloadJobStatus.FromRetrying(_delayForEachRetry));
            await Task.Delay((int)_delayForEachRetry);
            _activeCts = new CancellationTokenSource();
        }
        finally
        {
            _retryLock.Release();
        }

        _ = ResumeWithNewScopeAsync(_activeCts.Token, isInFirstResume);
    }

    public bool ShouldRetryIfInitialFailed() => true;

    private void OnPartStatusChanged(HttpPartDownloader partDownloader, PartDownloadStatus partStatus)
    {
        if (partStatus.State == PartDownloadState.Canceled)
        {
            _destination?.OnPartCancelled(partDownloader.Part);
        }
        else if (partStatus.State == PartDownloadState.Completed)
        {
            _destination?.OnPartCancelled(partDownloader.Part);
            
            bool allDone = false;
            lock (_parts)
            {
                allDone = _parts.All(p => p.IsCompleted);
            }

            if (allDone)
            {
                OnDownloadFinished();
            }
            else
            {
                BeginDownloadParts();
            }
        }
    }

    private void CreatePartDownloaderList()
    {
        lock (_partDownloaderList)
        {
            List<RangedPart> partsCopy;
            lock (_parts)
            {
                partsCopy = _parts.ToList();
            }
            foreach (var part in partsCopy)
            {
                GetOrCreatePartDownloader(part);
            }
        }
    }

    private void ClearPartDownloaderList()
    {
        lock (_partDownloaderList)
        {
            List<RangedPart> partsCopy;
            lock (_parts)
            {
                partsCopy = _parts.ToList();
            }
            foreach (var part in partsCopy)
            {
                DestroyPartDownloader(part);
            }
        }
    }

    private List<HttpPartDownloader> GetPartDownloaderList()
    {
        lock (_partDownloaderList)
        {
            return _partDownloaderList.Values.ToList();
        }
    }

    private HttpPartDownloader GetOrCreatePartDownloader(RangedPart part)
    {
        if (_destination == null) throw new InvalidOperationException("Destination not initialized.");
        
        lock (_partDownloaderList)
        {
            if (_partDownloaderList.TryGetValue(part.From, out var existing))
            {
                return existing;
            }

            var pd = new HttpPartDownloader(
                (HttpDownloadItem)DownloadItem,
                () => _destination.GetWriterFor(part),
                part,
                _client,
                new List<Throttler> { DownloadManager.Throttler, _jobThrottler },
                _strictDownload,
                _partSplitLock
            );

            pd.OnTooManyErrors = err =>
            {
                OnPartHaveToManyError(err);
            };

            Action<PartDownloadStatus>? statusHandler = null;
            statusHandler = status =>
            {
                OnPartStatusChanged(pd, status);
            };

            part.StatusChanged += statusHandler;
            _partDownloaderList[part.From] = pd;
            return pd;
        }
    }

    private void DestroyPartDownloader(RangedPart part)
    {
        lock (_partDownloaderList)
        {
            if (_partDownloaderList.TryGetValue(part.From, out var pd))
            {
                // Clean up listeners
                pd.StopDownloader();
                _partDownloaderList.Remove(part.From);
            }
        }
    }

    private bool IsDownloadItemIsAWebpage()
    {
        return DownloadItem.Name.EndsWith(".html", StringComparison.OrdinalIgnoreCase) || 
               DownloadItem.Name.EndsWith(".htm", StringComparison.OrdinalIgnoreCase);
    }

    private async Task FetchDownloadInfoAndValidateAsync(CancellationToken cancellationToken)
    {
        var response = await _client.TestAsync((HttpDownloadItem)DownloadItem);
        response.ExpectSuccess();

        if (_supportsConcurrent.HasValue)
        {
            if (_supportsConcurrent.Value && !response.ResumeSupport)
            {
                throw new ServerResumeSupportChangeException();
            }
        }

        _supportsConcurrent = response.ResumeSupport;
        
        _serverLastModified = null;
        if (response.LastModified != null && DateTimeOffset.TryParse(response.LastModified, out var parsedDate))
        {
            _serverLastModified = parsedDate.ToUnixTimeMilliseconds();
        }

        if (response.IsWebPage)
        {
            if (IsDownloadItemIsAWebpage())
            {
                _strictDownload = false;
                _supportsConcurrent = false;
                DownloadItem.ContentLength = IDownloadItem.LengthUnknown;
                ((HttpDownloadItem)DownloadItem).ServerETag = null;
            }
            else
            {
                throw new FileChangedException.GotAWebPage();
            }
        }

        long? totalLength = response.TotalLength;
        string? oldServerETag = ((HttpDownloadItem)DownloadItem).ServerETag;
        string? newServerETag = response.Etag;

        if (DownloadItem.ContentLength == IDownloadItem.LengthUnknown)
        {
            DownloadItem.ContentLength = totalLength ?? -1;
            ((HttpDownloadItem)DownloadItem).ServerETag = newServerETag;
        }
        else
        {
            if (totalLength.HasValue && totalLength.Value != DownloadItem.ContentLength)
            {
                throw new FileChangedException.LengthChangedException(DownloadItem.ContentLength, totalLength.Value);
            }
            if (oldServerETag != null && newServerETag != null)
            {
                if (oldServerETag != newServerETag)
                {
                    throw new FileChangedException.ETagChangedException(oldServerETag, newServerETag);
                }
            }
        }

        await SaveStateAsync();
    }

    public async Task StopAllPartsAsync()
    {
        List<HttpPartDownloader> list;
        lock (_partDownloaderList)
        {
            list = _partDownloaderList.Values.ToList();
        }

        foreach (var pd in list)
        {
            pd.StopDownloader();
        }

        foreach (var pd in list)
        {
            await pd.JoinAsync();
            await pd.AwaitIdleAsync();
        }
    }

    public override async Task PauseAsync(Exception? throwable = null)
    {
        await BootAsync();
        _failedDownloadTries = 0;
        
        if (_activeCts != null)
        {
            _activeCts.Cancel();
            _activeCts = null;
        }

        await StopAllPartsAsync();
        ClearPartDownloaderList();
        
        await OnDownloadCanceledAsync(throwable ?? new OperationCanceledException());
    }

    public override void OnDownloadFinishedBeforeSave()
    {
        if (DownloadItem.ContentLength == IDownloadItem.LengthUnknown)
        {
            lock (_parts)
            {
                if (_parts.Count == 1)
                {
                    DownloadItem.ContentLength = _parts[0].HowMuchProceed();
                }
            }
        }
    }

    private async Task SaveDownloadItemAsync()
    {
        await _itemSaveLock.WaitAsync();
        try
        {
            await DownloadManager.DlListDb.UpdateAsync(DownloadItem);
        }
        finally
        {
            _itemSaveLock.Release();
        }
    }

    private async Task SavePartsAsync()
    {
        await _partLock.WaitAsync();
        try
        {
            _destination?.Flush();
            List<RangedPart> partsCopy;
            lock (_parts)
            {
                partsCopy = _parts.Select(p => new RangedPart(p.From, p.To, p.Current)).ToList();
            }
            await DownloadManager.PartListDb.SetPartsAsync(Id, new RangedParts(partsCopy));
        }
        finally
        {
            _partLock.Release();
        }
    }

    public override async Task SaveStateAsync()
    {
        await SaveDownloadItemAsync();
        await SavePartsAsync();
    }

    public List<RangedPart> GetParts()
    {
        lock (_parts)
        {
            return _parts.ToList();
        }
    }

    public override void ReloadSettings()
    {
        OnPreferredConnectionCountChanged();
    }

    public override Task ExtraConfigsReceivedAsync(IDownloadJobExtraConfig config)
    {
        return Task.CompletedTask;
    }
}
