using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using System.IO;
using System.Linq;

namespace Flow.Desktop.Views;

public partial class AddDownloadDialog : UserControl
{
    public AddDownloadDialog()
    {
        InitializeComponent();
        
        // Auto populate default path
        FolderTextBox.Text = Services.AppBootstrapper.Instance.DefaultDownloadFolder;
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async void OnBrowseClick(object? sender, RoutedEventArgs e)
    {
        var window = TopLevel.GetTopLevel(this) as Window;
        if (window != null)
        {
            var folders = await window.StorageProvider.OpenFolderPickerAsync(new Avalonia.Platform.Storage.FolderPickerOpenOptions
            {
                Title = "SELECT DESTINATION FOLDER",
                AllowMultiple = false
            });

            if (folders != null && folders.Count > 0)
            {
                FolderTextBox.Text = folders[0].Path.LocalPath;
            }
        }
    }
}
