using System;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace Flow.Desktop.ViewModels;

public partial class MainWindowViewModel : ViewModelBase
{
    [ObservableProperty]
    private ViewModelBase _currentPage = null!;

    public DownloadsViewModel Downloads { get; }
    public SpeedGraphViewModel SpeedGraph { get; }
    public SettingsViewModel Settings { get; }

    public event Action? NewDownloadRequested;
    public event Action? NewDownloadFromClipboardRequested;
    public event Action? BatchDownloadRequested;
    public event Action? ExitRequested;
    public event Action? AboutRequested;
    public event Action? StopAllRequested;
    public event Action? OpenFileRequested;
    public event Action? OpenFolderRequested;
    public event Action? EditDownloadRequested;
    public event Action? PauseSelectedRequested;
    public event Action? ResumeSelectedRequested;
    public event Action? ShowDetailsRequested;
    public event Action? DeleteSelectedRequested;

    public MainWindowViewModel()
    {
        Downloads = new DownloadsViewModel();
        SpeedGraph = new SpeedGraphViewModel();
        Settings = new SettingsViewModel();

        // Default to Downloads page
        CurrentPage = Downloads;
    }

    [RelayCommand]
    private void NavigateToDownloads() => CurrentPage = Downloads;

    [RelayCommand]
    private void NavigateToSpeedGraph() => CurrentPage = SpeedGraph;

    [RelayCommand]
    private void NavigateToSettings() => CurrentPage = Settings;

    [RelayCommand]
    private void NewDownload() => NewDownloadRequested?.Invoke();

    [RelayCommand]
    private void NewDownloadFromClipboard() => NewDownloadFromClipboardRequested?.Invoke();

    [RelayCommand]
    private void BatchDownload() => BatchDownloadRequested?.Invoke();

    [RelayCommand]
    private void Exit() => ExitRequested?.Invoke();

    [RelayCommand]
    private void About() => AboutRequested?.Invoke();

    [RelayCommand]
    private void StopAll() => StopAllRequested?.Invoke();

    [RelayCommand]
    private void OpenFile() => OpenFileRequested?.Invoke();

    [RelayCommand]
    private void OpenFolder() => OpenFolderRequested?.Invoke();

    [RelayCommand]
    private void EditDownload() => EditDownloadRequested?.Invoke();

    [RelayCommand]
    private void PauseSelected() => PauseSelectedRequested?.Invoke();

    [RelayCommand]
    private void ResumeSelected() => ResumeSelectedRequested?.Invoke();

    [RelayCommand]
    private void ShowDetails() => ShowDetailsRequested?.Invoke();

    [RelayCommand]
    private void DeleteSelected() => DeleteSelectedRequested?.Invoke();
}
