using System;
using System.IO;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using Avalonia.VisualTree;
using FluentAvalonia.UI.Controls;
using Flow.Desktop.Controls;
using Flow.Desktop.ViewModels;
using System.Threading.Tasks;

namespace Flow.Desktop.Views;

public partial class DownloadsView : UserControl
{
    private DownloadsViewModel? _vm;

    public DownloadsView()
    {
        InitializeComponent();
        DataContextChanged += OnDataContextChanged;
    }

    private void OnDataContextChanged(object? sender, EventArgs e)
    {
        if (_vm != null)
        {
            _vm.OperationNotified -= OnOperationNotified;
        }

        _vm = DataContext as DownloadsViewModel;
        if (_vm != null)
        {
            _vm.OperationNotified += OnOperationNotified;
        }
    }

    private void OnOperationNotified(string title, string? message, bool success)
    {
        ShowToast(title, message, success ? ToastType.Success : ToastType.Error);
    }

    private void ShowToast(string title, string? message, ToastType type)
    {
        var window = this.FindAncestorOfType<MainWindow>();
        window?.ShowToast(title, message, type);
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async void OnAddDownloadClick(object? sender, RoutedEventArgs e)
    {
        var parentWindow = this.VisualRoot as Window;
        if (parentWindow == null) return;

        var dialog = new AddDownloadDialog();
        var result = await dialog.ShowDialog<bool>(parentWindow);
        if (result)
        {
            try
            {
                string url = dialog.UrlTextBox.Text ?? string.Empty;
                string name = dialog.NameTextBox.Text ?? string.Empty;
                string folder = dialog.FolderTextBox.Text ?? string.Empty;

                if (string.IsNullOrWhiteSpace(url))
                {
                    ShowToast("INVALID URL", "Download link is required", ToastType.Error);
                    return;
                }

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
                ShowToast("DOWNLOAD ADDED", item.Name, ToastType.Success);
            }
            catch (Exception ex)
            {
                ShowToast("ADD FAILED", ex.Message, ToastType.Error);
            }
        }
    }

    private async void OnAddBatchClick(object? sender, RoutedEventArgs e)
    {
        var parentWindow = this.VisualRoot as Window;
        if (parentWindow == null) return;

        var dialog = new BatchDownloadDialog();
        var result = await dialog.ShowDialog<bool>(parentWindow);
        if (result)
        {
            try
            {
                var links = dialog.GeneratedLinks;
                string folder = dialog.FolderTextBox.Text ?? string.Empty;
                if (string.IsNullOrWhiteSpace(folder))
                {
                    folder = Services.AppBootstrapper.Instance.DefaultDownloadFolder;
                }

                int successCount = 0;
                foreach (var url in links)
                {
                    string name = Path.GetFileName(new Uri(url).LocalPath);
                    if (string.IsNullOrWhiteSpace(name))
                    {
                        name = "download_" + Guid.NewGuid().ToString("N").Substring(0, 8);
                    }

                    var item = new Core.Models.HttpDownloadItem
                    {
                        Link = url,
                        Name = name,
                        Folder = folder,
                        DateAdded = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                        Status = Core.Models.DownloadStatus.Added
                    };

                    var props = new Core.Models.NewDownloadItemProps(
                        item,
                        null,
                        Core.Models.OnDuplicateStrategy.AddNumbered,
                        Core.Models.DownloadItemContext.Empty
                    );

                    long id = await Services.AppBootstrapper.Instance.DownloadManager.AddDownloadAsync(props);
                    await Services.AppBootstrapper.Instance.DownloadManager.ResumeAsync(id);
                    successCount++;
                }

                ShowToast("BATCH INJECTED", $"Successfully queued {successCount} jobs", ToastType.Success);
            }
            catch (Exception ex)
            {
                ShowToast("BATCH ADD FAILED", ex.Message, ToastType.Error);
            }
        }
    }

    private async void OnPauseAllClick(object? sender, RoutedEventArgs e)
    {
        if (DataContext is DownloadsViewModel)
        {
            try
            {
                await Services.AppBootstrapper.Instance.DownloadManager.StopAllAsync(
                    new Core.Models.DownloadItemContext(new[] { new Core.Models.StoppedBy(Core.Models.UserActor.Instance) })
                );
                ShowToast("ALL DOWNLOADS PAUSED", null, ToastType.Info);
            }
            catch (Exception ex)
            {
                ShowToast("PAUSE FAILED", ex.Message, ToastType.Error);
            }
        }
    }

    private async void OnEditClick(object? sender, RoutedEventArgs e)
    {
        if (sender is Control control && control.DataContext is Flow.Monitor.IDownloadItemState itemState)
        {
            var manager = Services.AppBootstrapper.Instance.DownloadManager;
            var item = await manager.DlListDb.GetByIdAsync(itemState.Id);
            if (item == null)
            {
                ShowToast("EDIT FAILED", "Download transaction not found in database", ToastType.Error);
                return;
            }

            var parentWindow = this.VisualRoot as Window;
            if (parentWindow == null) return;

            var dialog = new EditDownloadDialog(item);
            var result = await dialog.ShowDialog<bool>(parentWindow);
            if (result)
            {
                try
                {
                    dialog.SaveChanges();
                    
                    // Save to database/memory via manager
                    await manager.UpdateDownloadItemAsync(item.Id, null, updater =>
                    {
                        updater.Folder = item.Folder;
                        updater.Name = item.Name;
                        updater.Link = dialog.EditedUrl; // In case the link was changed
                        updater.PreferredConnectionCount = item.PreferredConnectionCount;
                        updater.SpeedLimit = item.SpeedLimit;
                        updater.FileChecksum = item.FileChecksum;
                    });

                    ShowToast("PROPERTIES SAVED", $"Updated config for {item.Name}", ToastType.Success);
                    
                    if (_vm != null)
                    {
                        _vm.RefreshList();
                    }
                }
                catch (Exception ex)
                {
                    ShowToast("SAVE FAILED", ex.Message, ToastType.Error);
                }
            }
        }
    }

    private async void OnChecksumClick(object? sender, RoutedEventArgs e)
    {
        if (sender is Control control && control.DataContext is Flow.Monitor.IDownloadItemState itemState)
        {
            string fullPath = itemState.GetFullPath();
            if (!File.Exists(fullPath))
            {
                ShowToast("CHECKSUM FAILED", "Physical file not found on disk. Ensure download is finished.", ToastType.Error);
                return;
            }

            var parentWindow = this.VisualRoot as Window;
            if (parentWindow == null) return;

            var dialog = new ChecksumCalculatorDialog(itemState.Name, fullPath);
            await dialog.ShowDialog(parentWindow);
        }
    }

    private void OnOpenFolderClick(object? sender, RoutedEventArgs e)
    {
        if (sender is Control control && control.DataContext is Flow.Monitor.IDownloadItemState itemState)
        {
            string fullPath = itemState.GetFullPath();
            if (File.Exists(fullPath))
            {
                Flow.Shared.Utils.FileUtils.OpenFolderOfFile(fullPath);
            }
            else
            {
                Flow.Shared.Utils.FileUtils.OpenFolder(itemState.Folder);
            }
        }
    }

    private async void OnDeleteClick(object? sender, RoutedEventArgs e)
    {
        if (sender is Control control && control.DataContext is Flow.Monitor.IDownloadItemState item)
        {
            var parentWindow = this.VisualRoot as Window;
            if (parentWindow == null) return;

            var dialog = new DeleteConfirmationDialog(item.Name);
            var result = await dialog.ShowDialog<bool>(parentWindow);
            if (result)
            {
                bool alsoRemoveFile = dialog.DeleteFileCheckBox.IsChecked == true;
                if (_vm != null)
                {
                    await _vm.DeleteDownloadWithOptionAsync(item, alsoRemoveFile);
                }
            }
        }
    }
}
