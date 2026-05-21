using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Flow.Desktop.Controls;
using FluentAvalonia.UI.Controls;
using Flow.Desktop.Services;
using Flow.Desktop.ViewModels;
using Flow.Monitor;

namespace Flow.Desktop.Views;

public partial class MainWindow : Window
{
    private MainWindowViewModel? _vm;
    private ToastContainer? _toastHost;
    private bool _allowClose;

    public MainWindow()
    {
        InitializeComponent();
        _toastHost = this.FindControl<ToastContainer>("ToastHost");
        DataContextChanged += OnDataContextChanged;
#if DEBUG
        this.AttachDevTools();
#endif
    }

    private void OnDataContextChanged(object? sender, EventArgs e)
    {
        if (_vm != null)
        {
            UnwireViewModel(_vm);
        }
        _vm = DataContext as MainWindowViewModel;
        if (_vm != null)
        {
            WireViewModel(_vm);
        }
    }

    private void WireViewModel(MainWindowViewModel vm)
    {
        vm.NewDownloadRequested += OnNewDownload;
        vm.NewDownloadFromClipboardRequested += OnNewDownloadFromClipboard;
        vm.BatchDownloadRequested += OnBatchDownload;
        vm.ExitRequested += OnExitRequested;
        vm.AboutRequested += OnAbout;
        vm.StopAllRequested += OnStopAll;
        vm.DeleteAllFinishedRequested += OnDeleteAllFinished;
        vm.DeleteAllUnfinishedRequested += OnDeleteAllUnfinished;
        vm.DeleteAllMissingRequested += OnDeleteAllMissing;
        vm.DeleteEntireListRequested += OnDeleteEntireList;
        vm.OpenFileRequested += OnOpenFile;
        vm.OpenFolderRequested += OnOpenFolder;
        vm.EditDownloadRequested += OnEditDownload;
        vm.PauseSelectedRequested += OnPauseSelected;
        vm.ResumeSelectedRequested += OnResumeSelected;
        vm.ShowDetailsRequested += OnShowDetails;
        vm.DeleteSelectedRequested += OnDeleteSelected;
    }

    private void UnwireViewModel(MainWindowViewModel vm)
    {
        vm.NewDownloadRequested -= OnNewDownload;
        vm.NewDownloadFromClipboardRequested -= OnNewDownloadFromClipboard;
        vm.BatchDownloadRequested -= OnBatchDownload;
        vm.ExitRequested -= OnExitRequested;
        vm.AboutRequested -= OnAbout;
        vm.StopAllRequested -= OnStopAll;
        vm.DeleteAllFinishedRequested -= OnDeleteAllFinished;
        vm.DeleteAllUnfinishedRequested -= OnDeleteAllUnfinished;
        vm.DeleteAllMissingRequested -= OnDeleteAllMissing;
        vm.DeleteEntireListRequested -= OnDeleteEntireList;
        vm.OpenFileRequested -= OnOpenFile;
        vm.OpenFolderRequested -= OnOpenFolder;
        vm.EditDownloadRequested -= OnEditDownload;
        vm.PauseSelectedRequested -= OnPauseSelected;
        vm.ResumeSelectedRequested -= OnResumeSelected;
        vm.ShowDetailsRequested -= OnShowDetails;
        vm.DeleteSelectedRequested -= OnDeleteSelected;
    }

    private IDownloadItemState? GetSelectedDownload()
    {
        var monitor = AppBootstrapper.Instance.DownloadMonitor;
        var selected = _vm?.Downloads.SelectedDownload;
        if (selected != null) return selected;

        var active = monitor.ActiveDownloadList.FirstOrDefault();
        return active ?? monitor.DownloadList.FirstOrDefault();
    }

    // ─── Menu / Shortcut Handlers ───

    private async void OnNewDownload()
    {
        var dialog = new AddDownloadDialog();
        var result = await dialog.ShowDialog<bool>(this);
        if (result) await AddDownloadFromDialog(dialog);
    }

    private async void OnNewDownloadFromClipboard()
    {
        var clipboardText = Clipboard != null ? await Clipboard.GetTextAsync() : null;
        if (string.IsNullOrEmpty(clipboardText))
        {
            ShowToast("Clipboard Empty", "No URL found in clipboard", ToastType.Info);
            return;
        }

        var dialog = new AddDownloadDialog { UrlTextBox = { Text = clipboardText } };
        var result = await dialog.ShowDialog<bool>(this);
        if (result) await AddDownloadFromDialog(dialog);
    }

    private async void OnBatchDownload()
    {
        var dialog = new BatchDownloadDialog();
        var result = await dialog.ShowDialog<bool>(this);
        if (result)
        {
            try
            {
                var links = dialog.GeneratedLinks;
                string folder = dialog.FolderTextBox.Text ?? string.Empty;
                if (string.IsNullOrWhiteSpace(folder))
                    folder = AppBootstrapper.Instance.DefaultDownloadFolder;

                int count = 0;
                foreach (var url in links)
                {
                    var name = System.IO.Path.GetFileName(new Uri(url).LocalPath);
                    if (string.IsNullOrWhiteSpace(name))
                        name = "download_" + Guid.NewGuid().ToString("N").Substring(0, 8);

                    var item = new Core.Models.HttpDownloadItem
                    {
                        Link = url, Name = name, Folder = folder,
                        DateAdded = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                        Status = Core.Models.DownloadStatus.Added
                    };
                    var props = new Core.Models.NewDownloadItemProps(item, null, Core.Models.OnDuplicateStrategy.AddNumbered, Core.Models.DownloadItemContext.Empty);
                    long id = await AppBootstrapper.Instance.DownloadManager.AddDownloadAsync(props);
                    await AppBootstrapper.Instance.DownloadManager.ResumeAsync(id);
                    DownloadWindowManager.Instance.ShowProgressWindow(id);
                    count++;
                }
                ShowToast("Batch Added", $"Successfully queued {count} jobs", ToastType.Success);
            }
            catch (Exception ex)
            {
                ShowToast("Batch Failed", ex.Message, ToastType.Error);
            }
        }
    }

    private async void OnExitRequested()
    {
        var activeCount = AppBootstrapper.Instance.DownloadMonitor.ActiveDownloadList.Count;
        if (activeCount > 0)
        {
            var confirm = new MessageDialog(
                "Confirm Exit",
                $"There { (activeCount == 1 ? "is 1 active download" : $"are {activeCount} active downloads") }. Are you sure you want to exit? Active downloads will be paused.",
                MessageDialogType.Warning,
                showCancel: true
            );
            var result = await confirm.ShowDialog<bool>(this);
            if (!result) return;
        }

        _allowClose = true;
        _trayService?.Hide();
        await AppBootstrapper.Instance.StopAsync();
        Close();
    }

    // Store TrayService reference for cleanup
    private Services.TrayService? _trayService;
    public void SetTrayService(Services.TrayService trayService) => _trayService = trayService;

    private async void OnAbout()
    {
        var dialog = new AboutDialog();
        await dialog.ShowDialog(this);
    }

    private async void OnStopAll()
    {
        try
        {
            await AppBootstrapper.Instance.DownloadManager.StopAllAsync(
                new Core.Models.DownloadItemContext(new[] { new Core.Models.StoppedBy(Core.Models.UserActor.Instance) })
            );
            ShowToast("All Downloads Paused", null, ToastType.Info);
        }
        catch (Exception ex)
        {
            ShowToast("Pause Failed", ex.Message, ToastType.Error);
        }
    }

    private async void OnDeleteAllFinished()
    {
        var monitor = AppBootstrapper.Instance.DownloadMonitor;
        var finished = monitor.DownloadList.Where(d => d is CompletedDownloadItemState).ToList();
        await BulkDelete(finished, "All finished downloads");
    }

    private async void OnDeleteAllUnfinished()
    {
        var monitor = AppBootstrapper.Instance.DownloadMonitor;
        var unfinished = monitor.DownloadList.Where(d => d is IProcessingDownloadItemState).ToList();
        await BulkDelete(unfinished, "All unfinished downloads");
    }

    private async void OnDeleteAllMissing()
    {
        var monitor = AppBootstrapper.Instance.DownloadMonitor;
        var missing = monitor.DownloadList.Where(d =>
        {
            string path = d.GetFullPath();
            return !System.IO.File.Exists(path) || new System.IO.FileInfo(path).Length == 0;
        }).ToList();
        await BulkDelete(missing, "All missing files");
    }

    private async void OnDeleteEntireList()
    {
        var monitor = AppBootstrapper.Instance.DownloadMonitor;
        var all = monitor.DownloadList.ToList();
        await BulkDelete(all, "Entire download list");
    }

    private async Task BulkDelete(List<IDownloadItemState> items, string label)
    {
        if (items.Count == 0)
        {
            ShowToast("Nothing to delete", $"No items found for: {label}", ToastType.Info);
            return;
        }

        var confirm = new MessageDialog(
            "Confirm Bulk Delete",
            $"Delete {items.Count} item(s)? ({label})",
            MessageDialogType.Warning,
            showCancel: true
        );
        var result = await confirm.ShowDialog<bool>(this);
        if (!result) return;

        int deleted = 0;
        var manager = AppBootstrapper.Instance.DownloadManager;
        foreach (var item in items)
        {
            try
            {
                await manager.DeleteDownloadAsync(item.Id, _ => false);
                deleted++;
            }
            catch { }
        }

        ShowToast("Bulk Delete Complete", $"Deleted {deleted} of {items.Count} items", ToastType.Success);
        _vm?.Downloads.RefreshList();
    }

    private void OnOpenFile()
    {
        var selected = GetSelectedDownload();
        if (selected == null) { ShowToast("No Selection", "Select a download first", ToastType.Info); return; }
        string path = selected.GetFullPath();
        if (System.IO.File.Exists(path))
            Flow.Shared.Utils.FileUtils.OpenFile(path);
        else
            ShowToast("File Not Found", "The downloaded file does not exist", ToastType.Error);
    }

    private void OnOpenFolder()
    {
        var selected = GetSelectedDownload();
        if (selected == null) { ShowToast("No Selection", "Select a download first", ToastType.Info); return; }
        string path = selected.GetFullPath();
        if (System.IO.File.Exists(path))
            Flow.Shared.Utils.FileUtils.OpenFolderOfFile(path);
        else
            Flow.Shared.Utils.FileUtils.OpenFolder(selected.Folder);
    }

    private async void OnEditDownload()
    {
        var selected = GetSelectedDownload();
        if (selected == null) { ShowToast("No Selection", "Select a download first", ToastType.Info); return; }

        var item = await AppBootstrapper.Instance.DownloadManager.DlListDb.GetByIdAsync(selected.Id);
        if (item == null) { ShowToast("Edit Failed", "Download not found in database", ToastType.Error); return; }

        var dialog = new EditDownloadDialog(item);
        var result = await dialog.ShowDialog<bool>(this);
        if (result)
        {
            try
            {
                dialog.SaveChanges();
                await AppBootstrapper.Instance.DownloadManager.UpdateDownloadItemAsync(item.Id, null, updater =>
                {
                    updater.Folder = item.Folder;
                    updater.Name = item.Name;
                    updater.Link = dialog.EditedUrl;
                    updater.PreferredConnectionCount = item.PreferredConnectionCount;
                    updater.SpeedLimit = item.SpeedLimit;
                    updater.FileChecksum = item.FileChecksum;
                });
                ShowToast("Properties Saved", $"Updated config for {item.Name}", ToastType.Success);
                _vm?.Downloads.RefreshList();
            }
            catch (Exception ex)
            {
                ShowToast("Save Failed", ex.Message, ToastType.Error);
            }
        }
    }

    private async void OnPauseSelected()
    {
        var selected = GetSelectedDownload();
        if (selected == null) { ShowToast("No Selection", "Select a download first", ToastType.Info); return; }
        try
        {
            await AppBootstrapper.Instance.DownloadManager.PauseAsync(selected.Id);
            ShowToast("Download Paused", selected.Name, ToastType.Success);
        }
        catch (Exception ex)
        {
            ShowToast("Pause Failed", ex.Message, ToastType.Error);
        }
    }

    private async void OnResumeSelected()
    {
        var selected = GetSelectedDownload();
        if (selected == null) { ShowToast("No Selection", "Select a download first", ToastType.Info); return; }
        try
        {
            await AppBootstrapper.Instance.DownloadManager.ResumeAsync(selected.Id);
            ShowToast("Download Resumed", selected.Name, ToastType.Success);
        }
        catch (Exception ex)
        {
            ShowToast("Resume Failed", ex.Message, ToastType.Error);
        }
    }

    private void OnShowDetails()
    {
        var selected = GetSelectedDownload();
        if (selected == null) { ShowToast("No Selection", "Select a download first", ToastType.Info); return; }
        DownloadWindowManager.Instance.ShowProgressWindow(selected.Id);
    }

    private async void OnDeleteSelected()
    {
        var selected = GetSelectedDownload();
        if (selected == null) { ShowToast("No Selection", "Select a download first", ToastType.Info); return; }

        var dialog = new DeleteConfirmationDialog(selected.Name);
        var result = await dialog.ShowDialog<bool>(this);
        if (result)
        {
            bool alsoRemoveFile = dialog.DeleteFileCheckBox.IsChecked == true;
            if (_vm != null)
                await _vm.Downloads.DeleteDownloadWithOptionAsync(selected, alsoRemoveFile);
        }
    }

    // ─── Shared Helpers ───

    private async Task AddDownloadFromDialog(AddDownloadDialog dialog)
    {
        try
        {
            string url = dialog.UrlTextBox.Text ?? string.Empty;
            string name = dialog.NameTextBox.Text ?? string.Empty;
            string folder = dialog.FolderTextBox.Text ?? string.Empty;

            if (string.IsNullOrWhiteSpace(url))
            {
                ShowToast("Invalid URL", "Download link is required", ToastType.Error);
                return;
            }

            var item = new Core.Models.HttpDownloadItem
            {
                Link = url,
                Name = string.IsNullOrWhiteSpace(name) ? System.IO.Path.GetFileName(new Uri(url).LocalPath) : name,
                Folder = string.IsNullOrWhiteSpace(folder) ? AppBootstrapper.Instance.DefaultDownloadFolder : folder,
                DateAdded = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                Status = Core.Models.DownloadStatus.Added
            };

            if (string.IsNullOrWhiteSpace(item.Name))
                item.Name = "download_" + Guid.NewGuid().ToString("N").Substring(0, 8);

            var props = new Core.Models.NewDownloadItemProps(item, null, Core.Models.OnDuplicateStrategy.AddNumbered, Core.Models.DownloadItemContext.Empty);
            long id = await AppBootstrapper.Instance.DownloadManager.AddDownloadAsync(props);
            await AppBootstrapper.Instance.DownloadManager.ResumeAsync(id);
            DownloadWindowManager.Instance.ShowProgressWindow(id);
            ShowToast("Download Added", item.Name, ToastType.Success);
        }
        catch (Exception ex)
        {
            ShowToast("Add Failed", ex.Message, ToastType.Error);
        }
    }

    public async void ShowToast(string title, string? message, ToastType type)
    {
        if (_toastHost != null)
            await _toastHost.ShowToastAsync(title, message, type);
    }

    public void AllowClose()
    {
        _allowClose = true;
        Close();
    }

    protected override void OnClosing(WindowClosingEventArgs e)
    {
        if (!_allowClose)
        {
            e.Cancel = true;
            Hide();
        }
        base.OnClosing(e);
    }

    private void OnNavItemInvoked(object? sender, NavigationViewItemInvokedEventArgs e)
    {
        if (_vm == null) return;

        string? tag = (e.InvokedItemContainer as NavigationViewItem)?.Tag?.ToString();
        switch (tag)
        {
            case "Downloads":
                _vm.NavigateToDownloadsCommand.Execute(null);
                break;
            case "Speed":
                _vm.NavigateToSpeedGraphCommand.Execute(null);
                break;
            case "Settings":
                _vm.NavigateToSettingsCommand.Execute(null);
                break;
        }
    }
}
