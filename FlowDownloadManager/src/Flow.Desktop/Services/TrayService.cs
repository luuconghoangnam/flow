using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Flow.Desktop.Views;

namespace Flow.Desktop.Services;

public class TrayService
{
    private readonly MainWindow _mainWindow;
    private TrayIcon? _trayIcon;

    public TrayService(MainWindow mainWindow)
    {
        _mainWindow = mainWindow;
    }

    public void Show()
    {
        if (_trayIcon != null) return;

        var menu = new NativeMenu();

        var showItem = new NativeMenuItem("Show Downloads");
        showItem.Click += (_, _) => RestoreMainWindow();
        menu.Items.Add(showItem);

        menu.Items.Add(new NativeMenuItemSeparator());

        var exitItem = new NativeMenuItem("Exit");
        exitItem.Click += (_, _) => ExitApp();
        menu.Items.Add(exitItem);

        _trayIcon = new TrayIcon
        {
            Icon = _mainWindow.Icon,
            ToolTipText = "Flow Download Manager",
            Menu = menu,
            IsVisible = true
        };

        _trayIcon.Clicked += (_, _) => RestoreMainWindow();
    }

    public void Hide()
    {
        _trayIcon?.Dispose();
        _trayIcon = null;
    }

    public void RestoreMainWindow()
    {
        _mainWindow.Show();
        if (_mainWindow.WindowState == WindowState.Minimized)
            _mainWindow.WindowState = WindowState.Normal;
        _mainWindow.Activate();
    }

    private void ExitApp()
    {
        _mainWindow.AllowClose();
        if (Application.Current?.ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            desktop.Shutdown();
        }
    }
}
