using System;
using System.IO;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using Flow.Core.Models;

namespace Flow.Desktop.Views;

public partial class EditDownloadDialog : UserControl
{
    private readonly IDownloadItem _item;

    public EditDownloadDialog()
    {
        InitializeComponent();
        _item = new HttpDownloadItem(); // Fallback
    }

    public EditDownloadDialog(IDownloadItem item) : this()
    {
        _item = item;

        // Prefill inputs
        UrlTextBox.Text = item.Link;
        NameTextBox.Text = item.Name;
        FolderTextBox.Text = item.Folder;
        ThreadCountTextBox.Text = (item.PreferredConnectionCount ?? 0).ToString();
        SpeedLimitTextBox.Text = item.SpeedLimit.ToString();
        ChecksumTextBox.Text = item.FileChecksum ?? string.Empty;
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

    public void SaveChanges()
    {
        _item.Folder = FolderTextBox.Text?.Trim() ?? _item.Folder;
        _item.Name = NameTextBox.Text?.Trim() ?? _item.Name;

        if (int.TryParse(ThreadCountTextBox.Text, out int tc))
        {
            _item.PreferredConnectionCount = tc > 0 ? tc : null;
        }

        if (long.TryParse(SpeedLimitTextBox.Text, out long sl))
        {
            _item.SpeedLimit = sl >= 0 ? sl : 0;
        }

        string checksum = ChecksumTextBox.Text?.Trim() ?? string.Empty;
        _item.FileChecksum = string.IsNullOrWhiteSpace(checksum) ? null : checksum;

        // Note: URL changes are handled in the updater since it might need re-initialization of job.
    }

    public string EditedUrl => UrlTextBox.Text?.Trim() ?? string.Empty;
    public string EditedName => NameTextBox.Text?.Trim() ?? string.Empty;
    public string EditedFolder => FolderTextBox.Text?.Trim() ?? string.Empty;
    public int? EditedThreadCount => int.TryParse(ThreadCountTextBox.Text, out int tc) && tc > 0 ? tc : null;
    public long EditedSpeedLimit => long.TryParse(SpeedLimitTextBox.Text, out long sl) && sl >= 0 ? sl : 0;
    public string? EditedChecksum => string.IsNullOrWhiteSpace(ChecksumTextBox.Text) ? null : ChecksumTextBox.Text.Trim();
}
