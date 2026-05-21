using System;
using System.Collections.Generic;
using System.Globalization;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Data.Converters;
using Avalonia.Markup.Xaml;
using Avalonia.Media;
using Flow.Core.Models;

namespace Flow.Desktop.Views;

public partial class DownloadDetailOverlay : UserControl
{
    public DownloadDetailOverlay()
    {
        InitializeComponent();
    }
}

/// <summary>
/// Converts PartDownloadStatus to a color for the thread segment grid.
/// </summary>
public class PartStatusToColorConverter : IMultiValueConverter
{
    public object? Convert(IList<object?> values, Type targetType, object? parameter, CultureInfo culture)
    {
        if (values.Count > 0 && values[0] is PartDownloadStatus status)
        {
            return status.State switch
            {
                PartDownloadState.Completed => Color.Parse("#22C55E"),      // Green
                PartDownloadState.ReceivingData => Color.Parse("#E64A00"),  // Orange
                PartDownloadState.Connecting => Color.Parse("#EAB308"),     // Yellow
                PartDownloadState.Idle => Color.Parse("#3A3A3A"),           // Gray
                PartDownloadState.Canceled => Color.Parse("#CC2200"),       // Red
                _ => Color.Parse("#3A3A3A")
            };
        }

        return Color.Parse("#3A3A3A");
    }

    public object[] ConvertBack(object value, Type[] targetTypes, object parameter, CultureInfo culture)
    {
        throw new NotImplementedException();
    }
}
