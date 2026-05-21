using System;
using System.Globalization;
using Avalonia.Data.Converters;
using Flow.Shared.Utils;

namespace Flow.Desktop.Controls;

public static class FormatHelpers
{
    public static readonly IValueConverter SpeedConverter = new FuncValueConverter<long, string>(
        speed => SizeFormatter.FormatSpeed(speed)
    );

    public static readonly IValueConverter SizeConverter = new FuncValueConverter<long, string>(
        size => SizeFormatter.FormatBytes(size)
    );
}
