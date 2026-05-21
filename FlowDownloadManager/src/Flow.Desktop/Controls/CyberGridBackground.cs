using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;

namespace Flow.Desktop.Controls;

public class CyberGridBackground : Control
{
    public static readonly StyledProperty<IBrush> GridColorProperty =
        AvaloniaProperty.Register<CyberGridBackground, IBrush>(nameof(GridColor), new SolidColorBrush(Color.Parse("#1E1E1E")));

    public static readonly StyledProperty<double> GridSpacingProperty =
        AvaloniaProperty.Register<CyberGridBackground, double>(nameof(GridSpacing), 24.0);

    public IBrush GridColor
    {
        get => GetValue(GridColorProperty);
        set => SetValue(GridColorProperty, value);
    }

    public double GridSpacing
    {
        get => GetValue(GridSpacingProperty);
        set => SetValue(GridSpacingProperty, value);
    }

    static CyberGridBackground()
    {
        AffectsRender<CyberGridBackground>(GridColorProperty, GridSpacingProperty);
    }

    public override void Render(DrawingContext context)
    {
        var bounds = Bounds;
        if (bounds.Width <= 0 || bounds.Height <= 0)
            return;

        // Draw solid background
        context.DrawRectangle(new SolidColorBrush(Color.Parse("#0A0A0A")), null, new Rect(0, 0, bounds.Width, bounds.Height));

        // Draw radial glow in the center for a blueprint/cyberpunk aesthetic depth
        var radialGlow = new RadialGradientBrush
        {
            Center = new RelativePoint(0.5, 0.5, RelativeUnit.Relative),
            GradientOrigin = new RelativePoint(0.5, 0.5, RelativeUnit.Relative),
            RadiusX = new RelativeScalar(0.8, RelativeUnit.Relative),
            RadiusY = new RelativeScalar(0.8, RelativeUnit.Relative),
            GradientStops =
            {
                new GradientStop(Color.Parse("#160A02"), 0.0), // Faint warm orange hue in center
                new GradientStop(Color.Parse("#00000000"), 1.0)
            }
        };
        context.DrawRectangle(radialGlow, null, new Rect(0, 0, bounds.Width, bounds.Height));

        // Draw grid lines
        var gridPen = new Pen(GridColor, 0.5);
        double spacing = GridSpacing;

        // Vertical lines
        for (double x = 0; x < bounds.Width; x += spacing)
        {
            context.DrawLine(gridPen, new Point(x, 0), new Point(x, bounds.Height));
        }

        // Horizontal lines
        for (double y = 0; y < bounds.Height; y += spacing)
        {
            context.DrawLine(gridPen, new Point(0, y), new Point(bounds.Width, y));
        }
    }
}
