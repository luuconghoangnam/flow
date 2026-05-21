using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Flow.Core.Models;
using Flow.Desktop.Services;
using Flow.Monitor;
using Flow.Shared.Utils;

namespace Flow.Desktop.ViewModels;

public partial class DownloadsViewModel : ViewModelBase
{
    [ObservableProperty]
    private ObservableCollection<IDownloadItemState> _downloads = new();

    [ObservableProperty]
    private IDownloadItemState? _selectedDownload;

    [ObservableProperty]
    private string _globalSpeedText = "0 B/s";

    [ObservableProperty]
    private bool _isDetailOpen;

    [ObservableProperty]
    private IProcessingDownloadItemState? _detailItem;

    [ObservableProperty]
    private ObservableCollection<IUiPart> _detailParts = new();

    private readonly IDownloadMonitor _monitor;
    private readonly Core.DownloadManager _manager;

    public DownloadsViewModel()
    {
        _manager = AppBootstrapper.Instance.DownloadManager;
        _monitor = AppBootstrapper.Instance.DownloadMonitor;

        _monitor.OnDownloadListChanged += OnDownloadListChanged;
        _monitor.OnActiveDownloadListChanged += OnActiveDownloadListChanged;

        RefreshList();
    }

    private void OnDownloadListChanged(object? sender, EventArgs e)
    {
        Dispatcher.UIThread.Post(RefreshList);
    }

    private void OnActiveDownloadListChanged(object? sender, EventArgs e)
    {
        Dispatcher.UIThread.Post(UpdateActiveItems);
    }

    private void RefreshList()
    {
        var currentSelectedId = SelectedDownload?.Id;
        
        var list = _monitor.DownloadList.OrderByDescending(d => d.DateAdded).ToList();
        
        Downloads.Clear();
        foreach (var item in list)
        {
            Downloads.Add(item);
        }

        if (currentSelectedId.HasValue)
        {
            SelectedDownload = Downloads.FirstOrDefault(d => d.Id == currentSelectedId.Value);
        }

        UpdateGlobalSpeed();
    }

    private void UpdateActiveItems()
    {
        // Update speeds and percentages on items without resetting the whole list
        foreach (var activeItem in _monitor.ActiveDownloadList)
        {
            var existing = Downloads.FirstOrDefault(d => d.Id == activeItem.Id);
            if (existing != null)
            {
                int index = Downloads.IndexOf(existing);
                Downloads[index] = activeItem; // Swap out with latest active state
            }
        }

        if (SelectedDownload != null)
        {
            var updatedSelected = Downloads.FirstOrDefault(d => d.Id == SelectedDownload.Id);
            if (updatedSelected != SelectedDownload)
            {
                SelectedDownload = updatedSelected;
            }
        }

        UpdateGlobalSpeed();
        UpdateDetailsIfOpen();
    }

    private void UpdateGlobalSpeed()
    {
        long totalSpeed = _monitor.ActiveDownloadList.Sum(d => d.Speed);
        GlobalSpeedText = SizeFormatter.FormatSpeed(totalSpeed);
    }

    private void UpdateDetailsIfOpen()
    {
        if (IsDetailOpen && SelectedDownload != null)
        {
            var activeState = _monitor.ActiveDownloadList.FirstOrDefault(d => d.Id == SelectedDownload.Id);
            if (activeState != null)
            {
                DetailItem = activeState;
                DetailParts.Clear();
                foreach (var part in activeState.Parts)
                {
                    DetailParts.Add(part);
                }
            }
            else
            {
                // Fallback to static detailed item if not currently active
                DetailItem = null;
                DetailParts.Clear();
            }
        }
    }

    [RelayCommand]
    public async Task StartDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            await _manager.ResumeAsync(target.Id);
        }
    }

    [RelayCommand]
    public async Task PauseDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            await _manager.PauseAsync(target.Id);
        }
    }

    [RelayCommand]
    public async Task ResumeDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            await _manager.ResumeAsync(target.Id);
        }
    }

    [RelayCommand]
    public async Task ResetDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            await _manager.ResetAsync(target.Id);
        }
    }

    [RelayCommand]
    public async Task DeleteDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            await _manager.DeleteDownloadAsync(target.Id, _ => true);
            if (SelectedDownload?.Id == target.Id)
            {
                SelectedDownload = null;
                IsDetailOpen = false;
            }
        }
    }

    [RelayCommand]
    public void ShowDetails(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            SelectedDownload = target;
            IsDetailOpen = true;
            UpdateDetailsIfOpen();
        }
    }

    [RelayCommand]
    public void CloseDetails()
    {
        IsDetailOpen = false;
        DetailItem = null;
        DetailParts.Clear();
    }
}
