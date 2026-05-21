using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using System.IO;
using System.Linq;

namespace Flow.Desktop.Views;

public partial class SettingsView : UserControl
{
    public SettingsView()
    {
        InitializeComponent();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async void OnLocateClick(object? sender, RoutedEventArgs e)
    {
        var window = TopLevel.GetTopLevel(this) as Window;
        if (window != null && DataContext is ViewModels.SettingsViewModel vm)
        {
            var folders = await window.StorageProvider.OpenFolderPickerAsync(new Avalonia.Platform.Storage.FolderPickerOpenOptions
            {
                Title = "SELECT DEFAULT DOWNLOAD DIRECTORY",
                AllowMultiple = false
            });

            if (folders != null && folders.Count > 0)
            {
                vm.DefaultDownloadFolder = folders[0].Path.LocalPath;
            }
        }
    }
}
