using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Flow.Core.Models;
using Flow.Desktop.Services;
using Flow.Monitor;
using Flow.Shared.Utils;

namespace Flow.Desktop.ViewModels;

/// <summary>
/// ViewModel for the standalone Download Progress Window (IDM-style).
/// Tracks a single download job, polls Monitor for live state, and exposes
/// controls to Resume / Pause / Cancel and open folder.
/// </summary>
public partial class DownloadProgressViewModel : ViewModelBase, IDisposable
{
    private readonly long _downloadId;
    private readonly IDownloadMonitor _monitor;
    private readonly Core.DownloadManager _manager;
    private readonly DispatcherTimer _pollTimer;
    private bool _disposed;

    // ─── File identity ─────────────────────────────────────────────────────
    [ObservableProperty] private string _name = string.Empty;
    [ObservableProperty] private string _url = string.Empty;
    [ObservableProperty] private string _saveFolder = string.Empty;

    // ─── Progress numbers ──────────────────────────────────────────────────
    [ObservableProperty] private int _percent = 0;
    [ObservableProperty] private bool _hasPercent = false;
    [ObservableProperty] private string _downloadedText = "0 B";
    [ObservableProperty] private string _totalSizeText = "Unknown";
    [ObservableProperty] private string _speedText = "0 B/s";
    [ObservableProperty] private string _timeLeftText = "--:--";
    [ObservableProperty] private string _statusText = "Idle";
    [ObservableProperty] private string _resumeSupportText = "—";

    // ─── Button state ──────────────────────────────────────────────────────
    [ObservableProperty] private bool _canResume = false;
    [ObservableProperty] private bool _canPause = false;
    [ObservableProperty] private bool _isCompleted = false;

    // ─── Parts (connection threads) ────────────────────────────────────────
    public ObservableCollection<IUiPart> Parts { get; } = new();

    public long DownloadId => _downloadId;

    public DownloadProgressViewModel(long downloadId)
    {
        _downloadId = downloadId;
        _monitor = AppBootstrapper.Instance.DownloadMonitor;
        _manager = AppBootstrapper.Instance.DownloadManager;

        // Poll every 500ms for smooth live updates (same approach as IDM)
        _pollTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
        _pollTimer.Tick += OnPollTick;
        _pollTimer.Start();

        // Immediate first refresh
        UpdateFromMonitor();
    }

    private void OnPollTick(object? sender, EventArgs e) => UpdateFromMonitor();

    private void UpdateFromMonitor()
    {
        // Check active list first (live data with speed)
        var activeState = _monitor.ActiveDownloadList.FirstOrDefault(d => d.Id == _downloadId);
        if (activeState != null)
        {
            ApplyProcessingState(activeState);
            IsCompleted = false;
            return;
        }

        // Check static list (added / completed states)
        var staticState = _monitor.DownloadList.FirstOrDefault(d => d.Id == _downloadId);
        if (staticState == null) return;

        if (staticState is IProcessingDownloadItemState procState)
        {
            ApplyProcessingState(procState);
            IsCompleted = false;
        }
        else if (staticState is CompletedDownloadItemState completedState)
        {
            // Completed
            Name = completedState.Name;
            Url = completedState.DownloadLink;
            SaveFolder = completedState.Folder;
        TotalSizeText = completedState.ContentLength <= 0 ? "Unknown" : SizeFormatter.FormatBytes(completedState.ContentLength);
            DownloadedText = TotalSizeText;
            Percent = 100;
            HasPercent = true;
            SpeedText = "—";
            TimeLeftText = "Done";
            StatusText = "COMPLETED";
            ResumeSupportText = "—";
            CanResume = false;
            CanPause = false;
            IsCompleted = true;
            Parts.Clear();
        }
    }

    private void ApplyProcessingState(IProcessingDownloadItemState state)
    {
        Name = state.Name;
        Url = state.DownloadLink;
        SaveFolder = state.Folder;
        TotalSizeText = state.ContentLength <= 0 ? "Unknown" : SizeFormatter.FormatBytes(state.ContentLength);
        DownloadedText = SizeFormatter.FormatBytes(state.Progress);
        SpeedText = state.Speed > 0 ? SizeFormatter.FormatSpeed(state.Speed) : (state.Status.IsActive ? "Connecting…" : "—");
        StatusText = FormatStatus(state.Status);
        ResumeSupportText = state.SupportResume.HasValue ? (state.SupportResume.Value ? "Yes" : "No") : "—";

        if (state.Percent.HasValue)
        {
            Percent = Math.Clamp(state.Percent.Value, 0, 100);
            HasPercent = true;
        }
        else
        {
            Percent = 0;
            HasPercent = false;
        }

        if (state.RemainingTime.HasValue && state.RemainingTime.Value > 0)
        {
            var ts = TimeSpan.FromSeconds(state.RemainingTime.Value);
            TimeLeftText = ts.TotalHours >= 1
                ? $"{(int)ts.TotalHours:D2}:{ts.Minutes:D2}:{ts.Seconds:D2}"
                : $"{ts.Minutes:D2}:{ts.Seconds:D2}";
        }
        else
        {
            TimeLeftText = state.Status.IsActive ? "calculating…" : "—";
        }

        CanResume = state.Status.CanBeResumed && !state.IsWaiting;
        CanPause = state.CanBePaused();

        // Sync parts list without full clear (avoids flicker)
        var newParts = state.Parts;
        if (Parts.Count != newParts.Count)
        {
            Parts.Clear();
            foreach (var p in newParts) Parts.Add(p);
        }
        else
        {
            for (int i = 0; i < newParts.Count; i++)
            {
                if (Parts[i].Id != newParts[i].Id || Parts[i].Status != newParts[i].Status)
                {
                    Parts[i] = newParts[i];
                }
            }
        }
    }

    private static string FormatStatus(DownloadJobStatus status) => status.State switch
    {
        DownloadJobState.Downloading => "DOWNLOADING",
        DownloadJobState.Resuming => "CONNECTING…",
        DownloadJobState.PreparingFile => $"PREPARING FILE {status.Percent}%",
        DownloadJobState.Retrying => $"RETRYING ({status.TimeUntilRetry / 1000}s)…",
        DownloadJobState.Idle => "IDLE",
        DownloadJobState.Canceled when status.Error is OperationCanceledException => "PAUSED",
        DownloadJobState.Canceled => $"ERROR: {status.Error?.Message ?? "Unknown"}",
        DownloadJobState.Finished => "COMPLETED",
        _ => "UNKNOWN"
    };

    // ─── Commands ──────────────────────────────────────────────────────────

    [RelayCommand]
    private async System.Threading.Tasks.Task ResumeAsync()
    {
        try { await _manager.ResumeAsync(_downloadId); }
        catch (Exception ex) { Logger.Error("[ProgressWindow] Resume failed", ex); }
    }

    [RelayCommand]
    private async System.Threading.Tasks.Task PauseAsync()
    {
        try { await _manager.PauseAsync(_downloadId); }
        catch (Exception ex) { Logger.Error("[ProgressWindow] Pause failed", ex); }
    }

    [RelayCommand]
    private async System.Threading.Tasks.Task CancelAsync()
    {
        try
        {
            await _manager.PauseAsync(_downloadId);
            // Signal the window to close (handled in code-behind)
        }
        catch (Exception ex) { Logger.Error("[ProgressWindow] Cancel/Pause failed", ex); }
    }

    [RelayCommand]
    private void OpenFolder()
    {
        string folder = SaveFolder;
        if (System.IO.Directory.Exists(folder))
            FileUtils.OpenFolder(folder);
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _pollTimer.Stop();
    }
}
