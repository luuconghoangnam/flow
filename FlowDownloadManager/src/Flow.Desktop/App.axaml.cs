using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Data.Core;
using Avalonia.Data.Core.Plugins;
using System.Collections.Generic;
using System.Linq;
using Avalonia.Markup.Xaml;
using Avalonia.Threading;
using Flow.Desktop.Services;
using Flow.Desktop.ViewModels;
using Flow.Desktop.Views;
using Flow.Integration;

namespace Flow.Desktop;

public partial class App : Application
{
    private TrayService? _trayService;

    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override async void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            var mainWindow = new MainWindow
            {
                DataContext = new MainWindowViewModel(),
            };

            desktop.MainWindow = mainWindow;

            // Wire system tray before showing the window
            _trayService = new TrayService(mainWindow);
            _trayService.Show();
            mainWindow.SetTrayService(_trayService);

            desktop.Exit += async (s, e) =>
            {
                _trayService?.Hide();
                await AppBootstrapper.Instance.StopAsync();
            };
        }

        base.OnFrameworkInitializationCompleted();

        // Boot system background processes and databases
        if (AppBootstrapper.Instance != null)
        {
            await AppBootstrapper.Instance.StartAsync();
            WireIntegrationToDialog();
        }
    }

    /// <summary>
    /// Bridges the integration server to the AddDownloadDialog on the UI thread.
    /// When the browser extension sends a download request, a pre-filled dialog
    /// appears for the user to review/modify before adding.
    /// </summary>
    private void WireIntegrationToDialog()
    {
        AppBootstrapper.Instance.OnIntegrationDownloadRequested += async (_, args) =>
        {
            var desktop = ApplicationLifetime as IClassicDesktopStyleApplicationLifetime;
            var mainWindow = desktop?.MainWindow;
            if (mainWindow == null) return;

            foreach (var cred in args.Items)
            {
                var dialog = new AddDownloadDialog
                {
                    UrlTextBox = { Text = cred.Link }
                };
                if (!string.IsNullOrEmpty(cred.SuggestedName))
                    dialog.NameTextBox.Text = cred.SuggestedName;
                if (!string.IsNullOrEmpty(cred.DownloadPage))
                    dialog.UrlTextBox.Watermark = cred.DownloadPage;

                var result = await dialog.ShowDialog<bool>(mainWindow);
                if (result)
                {
                    await AppBootstrapper.Instance.IntegrationHandler.AddDownloadsDirectly(
                        new List<IDownloadCredentialsFromIntegration>
                        {
                            new HttpDownloadCredentialsFromIntegration
                            {
                                Link = dialog.UrlTextBox.Text ?? cred.Link,
                                SuggestedName = dialog.NameTextBox.Text,
                            }
                        },
                        autoStart: true
                    );
                }
            }
        };
    }
}