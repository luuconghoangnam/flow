using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;

namespace Flow.Desktop.Views;

public partial class AboutDialog : Window
{
    public AboutDialog()
    {
        InitializeComponent();
        OpenSourceLink.Tapped += OnOpenSourceLinkTapped;
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async void OnOpenSourceLinkTapped(object? sender, TappedEventArgs e)
    {
        var dialog = new OpenSourceLibraries();
        await dialog.ShowDialog(this);
    }

    private void OnCloseClick(object? sender, RoutedEventArgs e) => Close();
}
