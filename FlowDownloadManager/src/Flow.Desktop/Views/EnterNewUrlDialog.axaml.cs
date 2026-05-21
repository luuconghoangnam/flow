using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;

namespace Flow.Desktop.Views;

public partial class EnterNewUrlDialog : Window
{
    public string DownloadUrl => UrlTextBox.Text ?? string.Empty;

    public EnterNewUrlDialog()
    {
        InitializeComponent();
        Loaded += (_, _) => UrlTextBox.Focus();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async void OnPasteClick(object? sender, RoutedEventArgs e)
    {
        if (Clipboard == null) return;
        var text = await Clipboard.GetTextAsync();
        if (!string.IsNullOrEmpty(text))
        {
            UrlTextBox.Text = text;
            UrlTextBox.Focus();
        }
    }

    private void OnOkClick(object? sender, RoutedEventArgs e)
    {
        Close(!string.IsNullOrWhiteSpace(UrlTextBox.Text));
    }

    private void OnCancelClick(object? sender, RoutedEventArgs e) => Close(false);
}
