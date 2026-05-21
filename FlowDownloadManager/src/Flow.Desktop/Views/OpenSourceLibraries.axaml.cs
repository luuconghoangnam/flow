using System.Collections.Generic;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;

namespace Flow.Desktop.Views;

public class LibraryInfo
{
    public string DisplayName { get; set; } = string.Empty;
    public string ArtifactId { get; set; } = string.Empty;
    public string License { get; set; } = string.Empty;
}

public partial class OpenSourceLibraries : Window
{
    public List<LibraryInfo> Libraries { get; } = new()
    {
        new() { DisplayName = "Avalonia 11.1.0", ArtifactId = "Avalonia", License = "MIT" },
        new() { DisplayName = "Avalonia.Desktop 11.1.0", ArtifactId = "Avalonia.Desktop", License = "MIT" },
        new() { DisplayName = "Avalonia.Themes.Fluent 11.1.0", ArtifactId = "Avalonia.Themes.Fluent", License = "MIT" },
        new() { DisplayName = "Avalonia.Fonts.Inter 11.1.0", ArtifactId = "Avalonia.Fonts.Inter", License = "MIT" },
        new() { DisplayName = "FluentAvaloniaUI 2.1.0", ArtifactId = "FluentAvaloniaUI", License = "MIT" },
        new() { DisplayName = "CommunityToolkit.Mvvm 8.2.2", ArtifactId = "CommunityToolkit.Mvvm", License = "MIT" },
        new() { DisplayName = "JetBrains Mono", ArtifactId = "JetBrains Mono Font", License = "OFL-1.1" },
        new() { DisplayName = ".NET Runtime 8.0", ArtifactId = "Microsoft.NETCore.App", License = "MIT" },
    };

    public OpenSourceLibraries()
    {
        InitializeComponent();
        DataContext = this;
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private void OnCloseClick(object? sender, RoutedEventArgs e) => Close();
}
