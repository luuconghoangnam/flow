using System;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using FluentAvalonia.UI.Controls;
using Flow.Desktop.ViewModels;
using System.Threading.Tasks;

namespace Flow.Desktop.Views;

public partial class DownloadsView : UserControl
{
    public DownloadsView()
    {
        InitializeComponent();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async void OnAddDownloadClick(object? sender, RoutedEventArgs e)
    {
        var dialog = new ContentDialog
        {
            Title = "ADD NEW DOWNLOAD INSTANCE",
            PrimaryButtonText = "INJECT LINK",
            CloseButtonText = "ABORT",
            DefaultButton = ContentDialogButton.Primary
        };

        var dialogView = new AddDownloadDialog();
        dialog.Content = dialogView;

        var result = await dialog.ShowAsync();
        if (result == ContentDialogResult.Primary)
        {
            string url = dialogView.UrlTextBox.Text ?? string.Empty;
            string name = dialogView.NameTextBox.Text ?? string.Empty;
            string folder = dialogView.FolderTextBox.Text ?? string.Empty;

            if (!string.IsNullOrWhiteSpace(url))
            {
                var item = new Core.Models.HttpDownloadItem
                {
                    Link = url,
                    Name = string.IsNullOrWhiteSpace(name) ? System.IO.Path.GetFileName(new Uri(url).LocalPath) : name,
                    Folder = string.IsNullOrWhiteSpace(folder) ? Services.AppBootstrapper.Instance.DefaultDownloadFolder : folder,
                    DateAdded = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                    Status = Core.Models.DownloadStatus.Added
                };

                if (string.IsNullOrWhiteSpace(item.Name))
                {
                    item.Name = "download_" + Guid.NewGuid().ToString("N").Substring(0, 8);
                }

                var props = new Core.Models.NewDownloadItemProps(
                    item,
                    null,
                    Core.Models.OnDuplicateStrategy.AddNumbered,
                    Core.Models.DownloadItemContext.Empty
                );

                long id = await Services.AppBootstrapper.Instance.DownloadManager.AddDownloadAsync(props);
                await Services.AppBootstrapper.Instance.DownloadManager.ResumeAsync(id);
            }
        }
    }

    private async void OnPauseAllClick(object? sender, RoutedEventArgs e)
    {
        if (DataContext is DownloadsViewModel vm)
        {
            await Services.AppBootstrapper.Instance.DownloadManager.StopAllAsync(
                new Core.Models.DownloadItemContext(new[] { new Core.Models.StoppedBy(Core.Models.UserActor.Instance) })
            );
        }
    }
}
