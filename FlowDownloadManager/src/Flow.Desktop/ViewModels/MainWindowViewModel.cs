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
}
