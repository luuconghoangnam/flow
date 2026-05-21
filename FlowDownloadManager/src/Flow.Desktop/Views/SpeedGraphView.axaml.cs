using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace Flow.Desktop.Views;

public partial class SpeedGraphView : UserControl
{
    public SpeedGraphView()
    {
        InitializeComponent();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }
}
