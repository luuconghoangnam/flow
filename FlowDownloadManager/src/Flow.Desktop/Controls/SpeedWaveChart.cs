using System;
using System.Collections.Generic;
using System.Collections.Specialized;
using System.Linq;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;

namespace Flow.Desktop.Controls;

public class SpeedWaveChart : Control
{
    public static readonly StyledProperty<IEnumerable<long>?> SpeedPointsProperty =
        AvaloniaProperty.Register<SpeedWaveChart, IEnumerable<long>?>(nameof(SpeedPoints));

    public IEnumerable<long>? SpeedPoints
    {
        get => GetValue(SpeedPointsProperty);
        set => SetValue(SpeedPointsProperty, value);
    }

    static SpeedWaveChart()
    {
        AffectsRender<SpeedWaveChart>(SpeedPointsProperty);
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);

        if (change.Property == SpeedPointsProperty)
        {
            if (change.OldValue is INotifyCollectionChanged oldCollection)
            {
                oldCollection.CollectionChanged -= OnPointsCollectionChanged;
            }

            if (change.NewValue is INotifyCollectionChanged newCollection)
            {
                newCollection.CollectionChanged += OnPointsCollectionChanged;
            }
        }
    }

    private void OnPointsCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        InvalidateVisual();
    }

    public override void Render(DrawingContext context)
    {
        var bounds = Bounds;
        if (bounds.Width <= 0 || bounds.Height <= 0)
            return;

        // Draw solid background grid
        var bgBrush = new SolidColorBrush(Color.Parse("#0F0F0F"));
        context.DrawRectangle(bgBrush, null, new Rect(0, 0, bounds.Width, bounds.Height));

        var points = SpeedPoints?.ToList();
        if (points == null || points.Count < 2)
            return;

        long maxVal = points.Max();
        if (maxVal < 1024) maxVal = 1024; // Lower bound (1KB/s) to avoid division by zero and extreme jumps on silence

        double stepX = bounds.Width / (points.Count - 1);
        
        // Draw grid lines
        var gridPen = new Pen(new SolidColorBrush(Color.Parse("#1E1E1E")), 0.5);
        int gridCount = 4;
        for (int i = 1; i < gridCount; i++)
        {
            double y = bounds.Height * i / gridCount;
            context.DrawLine(gridPen, new Point(0, y), new Point(bounds.Width, y));
            
            // Speed label text on grid
            long speedLabel = maxVal - (maxVal * i / gridCount);
            var text = new FormattedText(
                Shared.Utils.SizeFormatter.FormatSpeed(speedLabel),
                System.Globalization.CultureInfo.InvariantCulture,
                FlowDirection.LeftToRight,
                new Typeface("JetBrains Mono, Courier New, monospace"),
                10,
                new SolidColorBrush(Color.Parse("#555555"))
            );
            context.DrawText(text, new Point(8, y - 12));
        }

        // Generate waveform coordinates
        var lineGeometry = new PathGeometry();
        var fillGeometry = new PathGeometry();

        var lineFigure = new PathFigure { StartPoint = GetPointCoord(0, points[0], maxVal, stepX, bounds), IsClosed = false };
        var fillFigure = new PathFigure { StartPoint = new Point(0, bounds.Height), IsClosed = true };
        
        fillFigure.Segments?.Add(new LineSegment { Point = GetPointCoord(0, points[0], maxVal, stepX, bounds) });

        for (int i = 1; i < points.Count; i++)
        {
            var p = GetPointCoord(i, points[i], maxVal, stepX, bounds);
            lineFigure.Segments?.Add(new LineSegment { Point = p });
            fillFigure.Segments?.Add(new LineSegment { Point = p });
        }

        fillFigure.Segments?.Add(new LineSegment { Point = new Point(bounds.Width, bounds.Height) });

        lineGeometry.Figures?.Add(lineFigure);
        fillGeometry.Figures?.Add(fillFigure);

        // Fill area below the curve with linear gradient glow
        var glowGradient = new LinearGradientBrush
        {
            StartPoint = new RelativePoint(0.5, 0, RelativeUnit.Relative),
            EndPoint = new RelativePoint(0.5, 1, RelativeUnit.Relative),
            GradientStops =
            {
                new GradientStop(Color.Parse("#33E64A00"), 0.0), // Faint warm orange hue at top
                new GradientStop(Color.Parse("#00000000"), 1.0)
            }
        };

        context.DrawGeometry(glowGradient, null, fillGeometry);

        // Draw active waveform outline line
        var linePen = new Pen(new SolidColorBrush(Color.Parse("#E64A00")), 1.5);
        context.DrawGeometry(null, linePen, lineGeometry);
    }

    private Point GetPointCoord(int index, long value, long maxVal, double stepX, Rect bounds)
    {
        double x = index * stepX;
        double ratio = (double)value / maxVal;
        double y = bounds.Height - (ratio * (bounds.Height - 16)); // Keep padding at top
        return new Point(x, y);
    }
}
