using System;
using System.Collections.Generic;
using Avalonia.Threading;
using Flow.Core;
using Flow.Core.Models;
using Flow.Desktop.Views;
using Flow.Shared.Utils;

namespace Flow.Desktop.Services;

/// <summary>
/// Singleton that manages one DownloadProgressWindow per download ID,
/// mirroring the IDM experience of a separate floating progress dialog
/// for each active download.
/// </summary>
public class DownloadWindowManager
{
    private static readonly Lazy<DownloadWindowManager> _instance = new(() => new DownloadWindowManager());
    public static DownloadWindowManager Instance => _instance.Value;

    // Map: downloadId -> open window
    private readonly Dictionary<long, DownloadProgressWindow> _openWindows = new();
    private readonly object _lock = new();

    private Core.DownloadManager? _manager;

    private DownloadWindowManager() { }

    /// <summary>
    /// Call once from AppBootstrapper.StartAsync() to wire into DownloadManager events.
    /// </summary>
    public void Initialize(Core.DownloadManager manager)
    {
        _manager = manager;
        manager.OnJobEvent += OnJobEvent;
    }

    private void OnJobEvent(object? sender, DownloadManagerEvent ev)
    {
        switch (ev)
        {
            case JobStartedEvent started:
                // Auto-open a progress window when a download begins
                Dispatcher.UIThread.Post(() => ShowProgressWindow(started.DownloadItem.Id));
                break;

            case JobCompletedEvent completed:
                // Keep window open so user sees 100% Completed; don't auto-close
                Dispatcher.UIThread.Post(() => RefreshWindowIfOpen(completed.DownloadItem.Id));
                break;

            case JobRemovedEvent removed:
                // Close window if download was deleted
                Dispatcher.UIThread.Post(() => CloseProgressWindow(removed.DownloadItem.Id));
                break;
        }
    }

    /// <summary>
    /// Opens a new progress window for the given download, or focuses the existing one.
    /// Must be called on the UI thread.
    /// </summary>
    public void ShowProgressWindow(long downloadId)
    {
        lock (_lock)
        {
            if (_openWindows.TryGetValue(downloadId, out var existing))
            {
                // Bring existing window to front
                existing.Activate();
                if (existing.WindowState == Avalonia.Controls.WindowState.Minimized)
                    existing.WindowState = Avalonia.Controls.WindowState.Normal;
                return;
            }

            Logger.Info($"[WindowManager] Opening progress window for download ID={downloadId}");
            var win = new DownloadProgressWindow(downloadId);

            win.Closed += (_, _) =>
            {
                lock (_lock)
                {
                    _openWindows.Remove(downloadId);
                    Logger.Info($"[WindowManager] Progress window closed for ID={downloadId}");
                }
            };

            _openWindows[downloadId] = win;
            win.Show();
        }
    }

    private void RefreshWindowIfOpen(long downloadId)
    {
        lock (_lock)
        {
            // Window's own timer will pick up the state; nothing extra needed here
            _ = _openWindows.ContainsKey(downloadId);
        }
    }

    /// <summary>
    /// Programmatically closes the progress window for a given download.
    /// Must be called on the UI thread.
    /// </summary>
    public void CloseProgressWindow(long downloadId)
    {
        DownloadProgressWindow? win;
        lock (_lock)
        {
            if (!_openWindows.TryGetValue(downloadId, out win)) return;
        }
        win.Close();
    }

    public void CloseAll()
    {
        List<DownloadProgressWindow> wins;
        lock (_lock)
        {
            wins = new List<DownloadProgressWindow>(_openWindows.Values);
        }
        foreach (var w in wins) w.Close();
    }
}
