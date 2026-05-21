using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using Flow.Desktop.Services;
using Flow.Monitor;
using Flow.Shared.Utils;

namespace Flow.Desktop.ViewModels;

public partial class SpeedGraphViewModel : ViewModelBase
{
    private readonly IDownloadMonitor _monitor;
    private readonly DispatcherTimer _timer;

    [ObservableProperty]
    private string _currentSpeedText = "0 B/s";

    [ObservableProperty]
    private string _averageSpeedText = "0 B/s";

    [ObservableProperty]
    private string _maxSpeedText = "0 B/s";

    public ObservableCollection<long> SpeedPoints { get; } = new();

    public SpeedGraphViewModel()
    {
        _monitor = AppBootstrapper.Instance.DownloadMonitor;

        // Initialize history with zeros (60 data points for 1 minute)
        for (int i = 0; i < 60; i++)
        {
            SpeedPoints.Add(0);
        }

        _timer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(1)
        };
        _timer.Tick += OnTimerTick;
        _timer.Start();
    }

    private void OnTimerTick(object? sender, EventArgs e)
    {
        long currentSpeed = _monitor.ActiveDownloadList.Sum(d => d.Speed);
        
        // Update history (shift left)
        SpeedPoints.RemoveAt(0);
        SpeedPoints.Add(currentSpeed);

        CurrentSpeedText = SizeFormatter.FormatSpeed(currentSpeed);

        long max = SpeedPoints.Max();
        MaxSpeedText = SizeFormatter.FormatSpeed(max);

        double avg = SpeedPoints.Average();
        AverageSpeedText = SizeFormatter.FormatSpeed((long)avg);
    }
}
