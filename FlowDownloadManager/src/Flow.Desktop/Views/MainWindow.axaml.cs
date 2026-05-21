using Avalonia;
using Avalonia.Controls;
using FluentAvalonia.UI.Controls;
using Flow.Desktop.ViewModels;

namespace Flow.Desktop.Views;

public partial class MainWindow : Window
{
    private MainWindowViewModel? _vm;

    public MainWindow()
    {
        InitializeComponent();
        DataContextChanged += (_, _) => _vm = DataContext as MainWindowViewModel;
#if DEBUG
        this.AttachDevTools();
#endif
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