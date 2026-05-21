using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;

namespace Flow.Desktop.Views;

public partial class BatchDownloadDialog : Window
{
    public BatchDownloadDialog()
    {
        InitializeComponent();
        
        // Auto populate default path
        FolderTextBox.Text = Services.AppBootstrapper.Instance.DefaultDownloadFolder;
        PaddingComboBox.SelectedIndex = 0; // Auto by default
        
        UpdatePreview();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async void OnBrowseClick(object? sender, RoutedEventArgs e)
    {
        var folders = await this.StorageProvider.OpenFolderPickerAsync(new Avalonia.Platform.Storage.FolderPickerOpenOptions
        {
            Title = "Select Download Folder",
            AllowMultiple = false
        });

        if (folders != null && folders.Count > 0)
        {
            FolderTextBox.Text = folders[0].Path.LocalPath;
        }
    }

    private void OnInputsChanged(object? sender, TextChangedEventArgs e)
    {
        UpdatePreview();
    }

    private void OnPaddingChanged(object? sender, SelectionChangedEventArgs e)
    {
        UpdatePreview();
    }

    public List<string> GeneratedLinks { get; } = new();

    private void UpdatePreview()
    {
        if (FirstUrlTextBlock == null || LastUrlTextBlock == null || TotalCountTextBlock == null || InjectButton == null)
            return;

        GeneratedLinks.Clear();
        string urlTemplate = UrlTemplateTextBox.Text?.Trim() ?? string.Empty;
        string startText = StartIndexTextBox.Text?.Trim() ?? string.Empty;
        string endText = EndIndexTextBox.Text?.Trim() ?? string.Empty;

        bool isValid = true;
        string errorMsg = string.Empty;

        if (string.IsNullOrWhiteSpace(urlTemplate))
        {
            isValid = false;
            errorMsg = "URL Template cannot be empty";
        }
        else if (!urlTemplate.Contains("*"))
        {
            isValid = false;
            errorMsg = "URL Template must contain '*' wildcard";
        }
        else if (!Uri.TryCreate(urlTemplate.Replace("*", "1"), UriKind.Absolute, out _))
        {
            isValid = false;
            errorMsg = "Invalid URL structure";
        }

        if (isValid)
        {
            if (!int.TryParse(startText, out int start) || start < 0)
            {
                isValid = false;
                errorMsg = "Start index must be non-negative integer";
            }
            else if (!int.TryParse(endText, out int end) || end < start)
            {
                isValid = false;
                errorMsg = "End index must be greater or equal to start";
            }
            else if (end - start + 1 > 1000)
            {
                isValid = false;
                errorMsg = "Max range exceeded (1000 limit)";
            }
            else
            {
                int count = end - start + 1;
                int padLength = 0;

                if (PaddingComboBox.SelectedIndex == 0) // Auto
                {
                    padLength = Math.Max(startText.Length, endText.Length);
                }
                else if (PaddingComboBox.SelectedIndex == 2) // Fixed (e.g. 3)
                {
                    padLength = 3;
                }

                for (int i = start; i <= end; i++)
                {
                    string indexStr = padLength > 0 ? i.ToString().PadLeft(padLength, '0') : i.ToString();
                    GeneratedLinks.Add(urlTemplate.Replace("*", indexStr));
                }

                FirstUrlTextBlock.Text = GeneratedLinks.First();
                LastUrlTextBlock.Text = GeneratedLinks.Last();
                TotalCountTextBlock.Text = $"{count} items";
                TotalCountTextBlock.Foreground = Avalonia.Media.Brushes.Green;
            }
        }

        if (!isValid)
        {
            FirstUrlTextBlock.Text = "-";
            LastUrlTextBlock.Text = "-";
            TotalCountTextBlock.Text = string.IsNullOrEmpty(errorMsg) ? "Invalid parameters" : errorMsg;
            TotalCountTextBlock.Foreground = Avalonia.Media.Brushes.Red;
        }

        InjectButton.IsEnabled = isValid && GeneratedLinks.Any();
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
