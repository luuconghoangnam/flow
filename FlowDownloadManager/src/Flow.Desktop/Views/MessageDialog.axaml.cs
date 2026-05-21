using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using Avalonia.Media;

namespace Flow.Desktop.Views;

public enum MessageDialogType
{
    Info,
    Success,
    Warning,
    Error
}

public partial class MessageDialog : Window
{
    public MessageDialog()
    {
        InitializeComponent();
    }

    public MessageDialog(string title, string message, MessageDialogType type = MessageDialogType.Info, bool showCancel = false)
    {
        InitializeComponent();
        TitleTextBlock.Text = title;
        MessageTextBlock.Text = message;
        CancelButton.IsVisible = showCancel;

        if (!showCancel)
        {
            OkButton.Margin = new Avalonia.Thickness(0);
        }

        switch (type)
        {
            case MessageDialogType.Success:
                IconTextBlock.Text = "✓";
                IconTextBlock.Foreground = Brush.Parse("#22C55E");
                break;
            case MessageDialogType.Warning:
                IconTextBlock.Text = "!";
                IconTextBlock.Foreground = Brush.Parse("#EAB308");
                break;
            case MessageDialogType.Error:
                IconTextBlock.Text = "✗";
                IconTextBlock.Foreground = Brush.Parse("#EF4444");
                break;
            default:
                IconTextBlock.Text = "i";
                IconTextBlock.Foreground = Brush.Parse("#3B82F6");
                break;
        }
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private void OnOkClick(object? sender, RoutedEventArgs e) => Close(true);
    private void OnCancelClick(object? sender, RoutedEventArgs e) => Close(false);
}
