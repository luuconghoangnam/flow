using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace Flow.Desktop.Views;

public partial class DeleteConfirmationDialog : UserControl
{
    public DeleteConfirmationDialog()
    {
        InitializeComponent();
    }

    public DeleteConfirmationDialog(string fileName) : this()
    {
        FileNameTextBlock.Text = fileName;
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }
}
