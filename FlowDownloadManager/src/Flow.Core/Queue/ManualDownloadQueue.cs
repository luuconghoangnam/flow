using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Models;
using Flow.Shared.Utils;

namespace Flow.Core.Queue;

public class ManualDownloadQueue
{
    private readonly IDownloadManagerMinimalControl _downloadEvents;
    private readonly IGuardedEntry _booted = GuardedEntry.Create();

    private int _maxConcurrent = int.MaxValue;
    public int MaxConcurrent
    {
        get => _maxConcurrent;
        set => SetMaxConcurrent(value);
    }

    private readonly List<long> _activeItems = new();
    private readonly List<long> _totalItems = new();
    private readonly HashSet<long> _trimmedItems = new();
    private readonly object _lock = new();

    public IReadOnlyList<long> ActiveItems
    {
        get
        {
            lock (_lock) return _activeItems.ToList();
        }
    }

    public IReadOnlyList<long> TotalItems
    {
        get
        {
            lock (_lock) return _totalItems.ToList();
        }
    }

    public IReadOnlyList<long> PendingItems
    {
        get
        {
            lock (_lock)
            {
                return _totalItems.Except(_activeItems).ToList();
            }
        }
    }

    public event Action? OnQueueChanged;

    private readonly Debouncer _shakeDebouncer;

    public ManualDownloadQueue(IDownloadManagerMinimalControl downloadEvents)
    {
        _downloadEvents = downloadEvents ?? throw new ArgumentNullException(nameof(downloadEvents));
        _shakeDebouncer = new Debouncer(ActualShake, 500);
    }

    public void Boot()
    {
        _booted.Action(() =>
        {
            StartListener();
        });
    }

    private void StartListener()
    {
        _downloadEvents.OnJobEvent += (sender, ev) =>
        {
            lock (_lock)
            {
                if (!_totalItems.Contains(ev.DownloadItem.Id))
                {
                    return; // Skip events for downloads not in our queue
                }
            }

            switch (ev)
            {
                case JobCanceledEvent cancelEv:
                    OnDownloadCanceled(cancelEv.DownloadItem.Id, cancelEv.Exception);
                    break;

                case JobCompletedEvent completeEv:
                    OnDownloadFinished(completeEv.DownloadItem.Id);
                    break;

                case JobStartingEvent startingEv:
                    // Check if someone else resumed the download
                    var resumedBy = startingEv.Context.Get<ResumedBy>();
                    if (resumedBy?.By != UserActor.Instance)
                    {
                        RemoveFromQueue(startingEv.DownloadItem.Id);
                    }
                    break;

                case JobRemovedEvent removeEv:
                    OnDownloadRemoved(removeEv.DownloadItem.Id);
                    break;
            }
        };
    }

    private void OnDownloadCanceled(long id, Exception e)
    {
        bool wasTrimmed;
        lock (_lock)
        {
            wasTrimmed = _trimmedItems.Remove(id);
            if (wasTrimmed)
            {
                RemoveActiveItemLocked(id);
            }
            else
            {
                RemoveFromQueueLocked(id);
            }
        }
        Shake();
    }

    private void OnDownloadFinished(long id)
    {
        RemoveFromQueue(id);
        Shake();
    }

    private void OnDownloadRemoved(long id)
    {
        RemoveFromQueue(id);
    }

    private void RemoveActiveItemLocked(long id)
    {
        if (_activeItems.Remove(id))
        {
            OnQueueChanged?.Invoke();
        }
    }

    private void AddActiveItemLocked(long id)
    {
        if (!_activeItems.Contains(id))
        {
            _activeItems.Add(id);
            OnQueueChanged?.Invoke();
        }
    }

    private void RemoveFromQueueLocked(long id)
    {
        _trimmedItems.Remove(id);
        bool changed = _totalItems.Remove(id);
        bool changedActive = _activeItems.Remove(id);
        if (changed || changedActive)
        {
            OnQueueChanged?.Invoke();
        }
    }

    public void RemoveFromQueue(long id)
    {
        lock (_lock)
        {
            RemoveFromQueueLocked(id);
        }
    }

    public void RemoveFromQueue(HashSet<long> ids)
    {
        lock (_lock)
        {
            bool changed = false;
            foreach (var id in ids)
            {
                _trimmedItems.Remove(id);
                if (_totalItems.Remove(id)) changed = true;
                if (_activeItems.Remove(id)) changed = true;
            }
            if (changed)
            {
                OnQueueChanged?.Invoke();
            }
        }
    }

    public void ClearQueue()
    {
        lock (_lock)
        {
            _trimmedItems.Clear();
            _activeItems.Clear();
            _totalItems.Clear();
            OnQueueChanged?.Invoke();
        }
    }

    public void SetMaxConcurrent(int value)
    {
        lock (_lock)
        {
            _maxConcurrent = value <= 0 ? int.MaxValue : value;
        }
        Shake();
    }

    public void Resume(long id)
    {
        lock (_lock)
        {
            if (!_totalItems.Contains(id))
            {
                _totalItems.Add(id);
                OnQueueChanged?.Invoke();
            }
        }
        Shake(delayed: false);
    }

    public async Task PauseAsync(long id)
    {
        lock (_lock)
        {
            _trimmedItems.Remove(id);
        }
        await _downloadEvents.StopJobAsync(id, new DownloadItemContext(new[] { new StoppedBy(UserActor.Instance) }));
    }

    private void Shake(bool delayed = true)
    {
        if (delayed)
        {
            _shakeDebouncer.Trigger();
        }
        else
        {
            ActualShake();
        }
    }

    private bool ActualShake()
    {
        int activeCount;
        int max;
        lock (_lock)
        {
            activeCount = _activeItems.Count;
            max = _maxConcurrent;
        }

        if (activeCount < max)
        {
            Extend();
        }
        else if (activeCount > max)
        {
            Trim();
        }
        return true;
    }

    private void Trim()
    {
        while (true)
        {
            long idToTrim = -1;
            lock (_lock)
            {
                if (_activeItems.Count <= _maxConcurrent) break;
                idToTrim = _activeItems.LastOrDefault();
            }

            if (idToTrim == -1) break;

            lock (_lock)
            {
                _trimmedItems.Add(idToTrim);
                _activeItems.Remove(idToTrim);
                OnQueueChanged?.Invoke();
            }

            // Fire stop job asynchronously
            var id = idToTrim;
            Task.Run(async () =>
            {
                await _downloadEvents.StopJobAsync(id, new DownloadItemContext(new[] { new StoppedBy(UserActor.Instance) }));
            });
        }
    }

    private void Extend()
    {
        while (true)
        {
            lock (_lock)
            {
                if (_activeItems.Count >= _maxConcurrent) break;
            }

            if (!DownloadAQueueItemIfPossible())
            {
                break;
            }
        }
    }

    private bool DownloadAQueueItemIfPossible()
    {
        long? targetId = GetAnInactiveItemFromTheQueue();
        if (targetId == null) return false;

        lock (_lock)
        {
            AddActiveItemLocked(targetId.Value);
        }

        var id = targetId.Value;
        Task.Run(async () =>
        {
            await _downloadEvents.StartJobAsync(id, new DownloadItemContext(new[] { new ResumedBy(UserActor.Instance) }));
        });
        return true;
    }

    private long? GetAnInactiveItemFromTheQueue()
    {
        while (true)
        {
            long? candidate = null;
            lock (_lock)
            {
                candidate = _totalItems.FirstOrDefault(id => !_activeItems.Contains(id));
            }

            if (candidate == null) return null;

            if (!_downloadEvents.CanActivateJob(candidate.Value))
            {
                RemoveFromQueue(candidate.Value);
                continue;
            }

            return candidate;
        }
    }

    public int GetOrder(long item)
    {
        lock (_lock)
        {
            return _totalItems.IndexOf(item);
        }
    }

    public long? GetQueueItemFromOrder(int order)
    {
        lock (_lock)
        {
            if (order >= 0 && order < _totalItems.Count)
            {
                return _totalItems[order];
            }
            return null;
        }
    }

    public void Move(List<long> listOfIds, int diff)
    {
        if (diff == 0 || listOfIds == null || listOfIds.Count == 0) return;

        lock (_lock)
        {
            var movingIndexes = listOfIds
                .Select(id => _totalItems.IndexOf(id))
                .Where(index => index != -1)
                .OrderByDescending(index => index)
                .ToList();

            if (diff < 0)
            {
                movingIndexes.Reverse();
            }

            if (movingIndexes.Count == 0) return;

            bool changed = false;
            var dontMovedPositions = new HashSet<int>();

            foreach (var index in movingIndexes)
            {
                int newPosition = index + diff;
                if (newPosition < 0 || newPosition >= _totalItems.Count)
                {
                    dontMovedPositions.Add(index);
                    continue;
                }
                if (dontMovedPositions.Contains(newPosition))
                {
                    dontMovedPositions.Add(index);
                    continue;
                }

                // Swap in C# list
                var temp = _totalItems[index];
                _totalItems[index] = _totalItems[newPosition];
                _totalItems[newPosition] = temp;
                changed = true;
            }

            if (changed)
            {
                OnQueueChanged?.Invoke();
            }
        }
    }

    public void MoveUp(List<long> listOfIds) => Move(listOfIds, -1);
    public void MoveDown(List<long> listOfIds) => Move(listOfIds, 1);
}

internal class Debouncer
{
    private readonly Func<bool> _action;
    private readonly int _delayMs;
    private CancellationTokenSource? _cts;
    private readonly object _lock = new();

    public Debouncer(Func<bool> action, int delayMs)
    {
        _action = action;
        _delayMs = delayMs;
    }

    public void Trigger()
    {
        lock (_lock)
        {
            _cts?.Cancel();
            _cts?.Dispose();
            _cts = new CancellationTokenSource();
            var token = _cts.Token;
            Task.Run(async () =>
            {
                try
                {
                    await Task.Delay(_delayMs, token);
                    if (!token.IsCancellationRequested)
                    {
                        _action();
                    }
                }
                catch (OperationCanceledException) { }
            }, token);
        }
    }
}
