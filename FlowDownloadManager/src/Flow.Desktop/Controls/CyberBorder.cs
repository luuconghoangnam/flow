using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;

namespace Flow.Desktop.Controls;

public class CyberBorder : ContentControl
{
    public static readonly StyledProperty<double> ChamferSizeProperty =
        AvaloniaProperty.Register<CyberBorder, double>(nameof(ChamferSize), 12.0);

    public double ChamferSize
    {
        get => GetValue(ChamferSizeProperty);
        set => SetValue(ChamferSizeProperty, value);
    }

    static CyberBorder()
    {
        AffectsRender<CyberBorder>(ChamferSizeProperty, BorderBrushProperty, BorderThicknessProperty, BackgroundProperty);
    }

    public override void Render(DrawingContext context)
    {
        var bounds = Bounds;
        double cut = ChamferSize;
        var thickness = BorderThickness;
        var borderBrush = BorderBrush;
        var background = Background;

        if (bounds.Width <= 0 || bounds.Height <= 0)
            return;

        // Create the chamfered path (top-left cut at 45 degrees)
        var geometry = new PathGeometry();
        var figure = new PathFigure { StartPoint = new Point(cut, 0), IsClosed = true };
        
        figure.Segments?.Add(new LineSegment { Point = new Point(bounds.Width, 0) });
        figure.Segments?.Add(new LineSegment { Point = new Point(bounds.Width, bounds.Height) });
        figure.Segments?.Add(new LineSegment { Point = new Point(0, bounds.Height) });
        figure.Segments?.Add(new LineSegment { Point = new Point(0, cut) });
        
        geometry.Figures?.Add(figure);

        // Fill background
        if (background != null)
        {
            context.DrawGeometry(background, null, geometry);
        }

        // Draw border outline
        if (borderBrush != null && (thickness.Left > 0 || thickness.Top > 0 || thickness.Right > 0 || thickness.Bottom > 0))
        {
            var pen = new Pen(borderBrush, thickness.Left); // Use left thickness for ease
            context.DrawGeometry(null, pen, geometry);
        }
    }
}
