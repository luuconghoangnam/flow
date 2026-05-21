using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using System;

namespace Flow.Desktop.Views;

public partial class AddDownloadDialog : Window
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
        var folders = await this.StorageProvider.OpenFolderPickerAsync(new Avalonia.Platform.Storage.FolderPickerOpenOptions
        {
            Title = "SELECT DESTINATION FOLDER",
            AllowMultiple = false
        });

        if (folders != null && folders.Count > 0)
        {
            FolderTextBox.Text = folders[0].Path.LocalPath;
        }
    }

    private void OnOkClick(object? sender, RoutedEventArgs e)
    {
        Close(true);
    }

    private void OnCancelClick(object? sender, RoutedEventArgs e)
    {
        Close(false);
    }
}
