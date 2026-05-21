using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Flow.Core.Models;
using Flow.Desktop.Services;

namespace Flow.Desktop.ViewModels;

public partial class SettingsViewModel : ViewModelBase
{
    private readonly DownloadSettings _settings;

    [ObservableProperty]
    private int _defaultThreadCount;

    [ObservableProperty]
    private bool _dynamicPartCreationMode;

    [ObservableProperty]
    private bool _useServerLastModifiedTime;

    [ObservableProperty]
    private long _globalSpeedLimit; // 0 for unlimited

    [ObservableProperty]
    private bool _useSparseFileAllocation;

    [ObservableProperty]
    private bool _appendExtensionToIncompleteDownloads;

    [ObservableProperty]
    private string _defaultDownloadFolder = string.Empty;

    public SettingsViewModel()
    {
        _settings = AppBootstrapper.Instance.Settings;

        // Populate values
        DefaultThreadCount = _settings.DefaultThreadCount;
        DynamicPartCreationMode = _settings.DynamicPartCreationMode;
        UseServerLastModifiedTime = _settings.UseServerLastModifiedTime;
        GlobalSpeedLimit = _settings.GlobalSpeedLimit;
        UseSparseFileAllocation = _settings.UseSparseFileAllocation;
        AppendExtensionToIncompleteDownloads = _settings.AppendExtensionToIncompleteDownloads;
        DefaultDownloadFolder = AppBootstrapper.Instance.DefaultDownloadFolder;
    }

    [RelayCommand]
    public void Save()
    {
        _settings.DefaultThreadCount = DefaultThreadCount;
        _settings.DynamicPartCreationMode = DynamicPartCreationMode;
        _settings.UseServerLastModifiedTime = UseServerLastModifiedTime;
        _settings.GlobalSpeedLimit = GlobalSpeedLimit;
        _settings.UseSparseFileAllocation = UseSparseFileAllocation;
        _settings.AppendExtensionToIncompleteDownloads = AppendExtensionToIncompleteDownloads;
        
        if (Directory.Exists(DefaultDownloadFolder))
        {
            AppBootstrapper.Instance.DefaultDownloadFolder = DefaultDownloadFolder;
        }

        AppBootstrapper.Instance.SaveSettings();
    }
}
