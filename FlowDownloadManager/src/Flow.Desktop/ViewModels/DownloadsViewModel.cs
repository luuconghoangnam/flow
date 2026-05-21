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
    public event Action<string, string?, bool>? OperationNotified;

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

    [ObservableProperty]
    private string _selectedStatusFilter = "ALL";

    [ObservableProperty]
    private string _searchText = string.Empty;

    partial void OnSearchTextChanged(string value) => RefreshList();

    public ObservableCollection<string> StatusFilters { get; } = new() { "ALL", "FINISHED", "UNFINISHED" };

    partial void OnSelectedStatusFilterChanged(string value) => RefreshList();

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

    public void RefreshList()
    {
        var currentSelectedId = SelectedDownload?.Id;
        
        var list = _monitor.DownloadList.AsEnumerable();

        // Filter by Status
        if (SelectedStatusFilter == "FINISHED")
        {
            list = list.Where(d => d is CompletedDownloadItemState);
        }
        else if (SelectedStatusFilter == "UNFINISHED")
        {
            list = list.Where(d => d is IProcessingDownloadItemState);
        }

        // Filter by search text (like main app's search box)
        if (!string.IsNullOrWhiteSpace(SearchText))
        {
            list = list.Where(d => d.Name.Contains(SearchText, StringComparison.OrdinalIgnoreCase));
        }

        var sortedList = list.OrderByDescending(d => d.DateAdded).ToList();
        
        Downloads.Clear();
        foreach (var item in sortedList)
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
            try
            {
                await _manager.PauseAsync(target.Id);
                OperationNotified?.Invoke("DOWNLOAD PAUSED", target.Name, true);
            }
            catch (Exception ex)
            {
                OperationNotified?.Invoke("PAUSE FAILED", ex.Message, false);
            }
        }
    }

    [RelayCommand]
    public async Task ResumeDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            try
            {
                await _manager.ResumeAsync(target.Id);
                OperationNotified?.Invoke("DOWNLOAD RESUMED", target.Name, true);
            }
            catch (Exception ex)
            {
                OperationNotified?.Invoke("RESUME FAILED", ex.Message, false);
            }
        }
    }

    [RelayCommand]
    public async Task ResetDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            try
            {
                await _manager.ResetAsync(target.Id);
                OperationNotified?.Invoke("DOWNLOAD RESET", target.Name, true);
            }
            catch (Exception ex)
            {
                OperationNotified?.Invoke("RESET FAILED", ex.Message, false);
            }
        }
    }

    [RelayCommand]
    public async Task DeleteDownloadAsync(IDownloadItemState? item)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            try
            {
                await _manager.DeleteDownloadAsync(target.Id, _ => true);
                if (SelectedDownload?.Id == target.Id)
                {
                    SelectedDownload = null;
                    IsDetailOpen = false;
                }

                OperationNotified?.Invoke("DOWNLOAD DELETED", target.Name, true);
            }
            catch (Exception ex)
            {
                OperationNotified?.Invoke("DELETE FAILED", ex.Message, false);
            }
        }
    }

    public async Task DeleteDownloadWithOptionAsync(IDownloadItemState? item, bool alsoDeleteFile)
    {
        var target = item ?? SelectedDownload;
        if (target != null)
        {
            try
            {
                await _manager.DeleteDownloadAsync(target.Id, _ => alsoDeleteFile);
                if (SelectedDownload?.Id == target.Id)
                {
                    SelectedDownload = null;
                    IsDetailOpen = false;
                }

                OperationNotified?.Invoke("DOWNLOAD DELETED", target.Name, true);
            }
            catch (Exception ex)
            {
                OperationNotified?.Invoke("DELETE FAILED", ex.Message, false);
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
