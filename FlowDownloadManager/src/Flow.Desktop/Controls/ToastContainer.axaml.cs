using System;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using Avalonia.Controls;
using Avalonia.Threading;

namespace Flow.Desktop.Controls;

public partial class ToastContainer : UserControl
{
    public ObservableCollection<ToastViewModel> Toasts { get; } = new();

    public ToastContainer()
    {
        InitializeComponent();
        DataContext = this;
    }

    public async Task ShowToastAsync(string title, string? message, ToastType type, int durationMs = 5000)
    {
        var toast = new ToastViewModel(title, message, type);
        toast.CloseRequested += OnCloseRequested;

        await Dispatcher.UIThread.InvokeAsync(() =>
        {
            Toasts.Insert(0, toast);
            toast.BeginShow();
        });

        _ = AutoDismissAsync(toast, durationMs);
    }

    private async Task AutoDismissAsync(ToastViewModel toast, int durationMs)
    {
        await Task.Delay(durationMs);
        await Dispatcher.UIThread.InvokeAsync(() => toast.BeginHide());
        await Task.Delay(240);
        await Dispatcher.UIThread.InvokeAsync(() => RemoveToast(toast));
    }

    private void OnCloseRequested(object? sender, EventArgs e)
    {
        if (sender is ToastViewModel toast)
        {
            _ = CloseWithAnimationAsync(toast);
        }
    }

    private async Task CloseWithAnimationAsync(ToastViewModel toast)
    {
        await Dispatcher.UIThread.InvokeAsync(() => toast.BeginHide());
        await Task.Delay(240);
        await Dispatcher.UIThread.InvokeAsync(() => RemoveToast(toast));
    }

    private void RemoveToast(ToastViewModel toast)
    {
        toast.CloseRequested -= OnCloseRequested;
        Toasts.Remove(toast);
    }
}
