using Avalonia;
using Avalonia.Controls;
using Flow.Desktop.Controls;
using FluentAvalonia.UI.Controls;
using Flow.Desktop.ViewModels;

namespace Flow.Desktop.Views;

public partial class MainWindow : Window
{
    private MainWindowViewModel? _vm;
    private ToastContainer? _toastHost;

    public MainWindow()
    {
        InitializeComponent();
        _toastHost = this.FindControl<ToastContainer>("ToastHost");
        DataContextChanged += (_, _) => _vm = DataContext as MainWindowViewModel;
#if DEBUG
        this.AttachDevTools();
#endif
    }

    public async void ShowToast(string title, string? message, ToastType type)
    {
        if (_toastHost != null)
        {
            await _toastHost.ShowToastAsync(title, message, type);
        }
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
