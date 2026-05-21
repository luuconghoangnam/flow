using System;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using Flow.Desktop.ViewModels;

namespace Flow.Desktop.Views;

public partial class DownloadProgressWindow : Window
{
    private DownloadProgressViewModel? _vm;

    public DownloadProgressWindow()
    {
        InitializeComponent();
    }

    public DownloadProgressWindow(long downloadId)
    {
        InitializeComponent();
        _vm = new DownloadProgressViewModel(downloadId);
        DataContext = _vm;
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    /// <summary>
    /// Close button — just hides/closes the window.
    /// The download continues running in the background queue.
    /// </summary>
    private void OnCloseClick(object? sender, RoutedEventArgs e)
    {
        Close();
    }

    protected override void OnClosing(WindowClosingEventArgs e)
    {
        base.OnClosing(e);
        // Dispose ViewModel timer when window closes
        _vm?.Dispose();
    }
}
