using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Data.Core;
using Avalonia.Data.Core.Plugins;
using System.Linq;
using Avalonia.Markup.Xaml;
using Flow.Desktop.ViewModels;
using Flow.Desktop.Views;

namespace Flow.Desktop;

public partial class App : Application
{
    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override async void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            desktop.MainWindow = new MainWindow
            {
                DataContext = new MainWindowViewModel(),
            };

            desktop.Exit += async (s, e) =>
            {
                await Services.AppBootstrapper.Instance.StopAsync();
            };
        }

        base.OnFrameworkInitializationCompleted();

        // Boot system background processes and databases
        if (Services.AppBootstrapper.Instance != null)
        {
            await Services.AppBootstrapper.Instance.StartAsync();
        }
    }
}