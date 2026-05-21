using System;
using System.Windows.Input;
using Avalonia.Controls;
using Avalonia.Media;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace Flow.Desktop.Controls;

public enum ToastType
{
    Success,
    Error,
    Warning,
    Info
}

public partial class ToastMessage : UserControl
{
    public ToastMessage()
    {
        InitializeComponent();
    }
}

public partial class ToastViewModel : ObservableObject
{
    [ObservableProperty]
    private string _title = string.Empty;

    [ObservableProperty]
    private string? _message;

    [ObservableProperty]
    private string _icon = string.Empty;

    [ObservableProperty]
    private IBrush _iconColor = Brushes.Gray;

    [ObservableProperty]
    private IBrush _borderColor = Brushes.Gray;

    public event EventHandler? CloseRequested;

    public ToastViewModel(string title, string? message, ToastType type)
    {
        Title = title;
        Message = message;

        switch (type)
        {
            case ToastType.Success:
                Icon = "\uE73E"; // CheckMark
                IconColor = new SolidColorBrush(Color.Parse("#22C55E"));
                BorderColor = new SolidColorBrush(Color.Parse("#22C55E"));
                break;
            case ToastType.Error:
                Icon = "\uE711"; // ErrorBadge
                IconColor = new SolidColorBrush(Color.Parse("#CC2200"));
                BorderColor = new SolidColorBrush(Color.Parse("#CC2200"));
                break;
            case ToastType.Warning:
                Icon = "\uE7BA"; // Warning
                IconColor = new SolidColorBrush(Color.Parse("#EAB308"));
                BorderColor = new SolidColorBrush(Color.Parse("#EAB308"));
                break;
            case ToastType.Info:
                Icon = "\uE946"; // Info
                IconColor = new SolidColorBrush(Color.Parse("#3B82F6"));
                BorderColor = new SolidColorBrush(Color.Parse("#3B82F6"));
                break;
        }
    }

    [RelayCommand]
    private void Close()
    {
        CloseRequested?.Invoke(this, EventArgs.Empty);
    }
}
