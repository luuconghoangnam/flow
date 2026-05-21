using System;
using System.IO;
using System.Security.Cryptography;
using System.Threading.Tasks;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using Avalonia.Media;
using Avalonia.Threading;

namespace Flow.Desktop.Views;

public partial class ChecksumCalculatorDialog : UserControl
{
    private readonly string _filePath;
    private string? _calculatedHash;

    public ChecksumCalculatorDialog()
    {
        InitializeComponent();
        _filePath = string.Empty;
    }

    public ChecksumCalculatorDialog(string fileName, string filePath) : this()
    {
        _filePath = filePath;
        FileNameTextBlock.Text = fileName;
        FilePathTextBlock.Text = filePath;
        
        // Start calculation automatically in background
        Task.Run(CalculateHashAsync);
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }

    private async Task CalculateHashAsync()
    {
        if (string.IsNullOrEmpty(_filePath) || !File.Exists(_filePath))
        {
            Dispatcher.UIThread.Post(() =>
            {
                CalculatedHashTextBox.Text = "ERROR: FILE NOT FOUND";
                CalculatedHashTextBox.Foreground = Brushes.Red;
            });
            return;
        }

        string algorithm = "SHA-256";
        Dispatcher.UIThread.Post(() =>
        {
            if (Md5Radio.IsChecked == true) algorithm = "MD5";
            else if (Sha1Radio.IsChecked == true) algorithm = "SHA-1";
            else algorithm = "SHA-256";

            CalculatedHashTextBox.Text = $"Calculating {algorithm}...";
            CalculatedHashTextBox.Foreground = Brushes.Gray;
            CalculationProgressBar.Value = 0;
            ProgressPercentTextBlock.Text = "0%";
        });

        try
        {
            using var stream = new FileStream(_filePath, FileMode.Open, FileAccess.Read, FileShare.Read, 4096, useAsync: true);
            using var hashAlgorithm = GetHashAlgorithm(algorithm);
            
            long fileLength = stream.Length;
            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int bytesRead;
            int lastPercent = -1;

            while ((bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length)) > 0)
            {
                hashAlgorithm.TransformBlock(buffer, 0, bytesRead, null, 0);
                totalRead += bytesRead;

                if (fileLength > 0)
                {
                    int percent = (int)((totalRead * 100) / fileLength);
                    if (percent != lastPercent)
                    {
                        lastPercent = percent;
                        Dispatcher.UIThread.Post(() =>
                        {
                            CalculationProgressBar.Value = percent;
                            ProgressPercentTextBlock.Text = $"{percent}%";
                        });
                    }
                }
            }

            hashAlgorithm.TransformFinalBlock(Array.Empty<byte>(), 0, 0);
            byte[] hashBytes = hashAlgorithm.Hash ?? Array.Empty<byte>();
            string hashHex = BitConverter.ToString(hashBytes).Replace("-", "").ToLowerInvariant();

            _calculatedHash = hashHex;

            Dispatcher.UIThread.Post(() =>
            {
                CalculatedHashTextBox.Text = hashHex;
                CalculatedHashTextBox.Foreground = Brush.Parse("#E5E7EB");
                CopyButton.IsEnabled = true;
                UpdateComparison();
            });
        }
        catch (Exception ex)
        {
            Dispatcher.UIThread.Post(() =>
            {
                CalculatedHashTextBox.Text = $"ERROR: {ex.Message}";
                CalculatedHashTextBox.Foreground = Brushes.Red;
            });
        }
    }

    private HashAlgorithm GetHashAlgorithm(string name)
    {
        return name switch
        {
            "MD5" => MD5.Create(),
            "SHA-1" => SHA1.Create(),
            _ => SHA256.Create()
        };
    }

    private void OnCopyClick(object? sender, RoutedEventArgs e)
    {
        if (!string.IsNullOrEmpty(_calculatedHash))
        {
            var topLevel = TopLevel.GetTopLevel(this);
            topLevel?.Clipboard?.SetTextAsync(_calculatedHash);
        }
    }

    private void OnCompareTextChanged(object? sender, TextChangedEventArgs e)
    {
        UpdateComparison();
    }

    private void UpdateComparison()
    {
        if (string.IsNullOrEmpty(_calculatedHash))
        {
            ResultBorder.IsVisible = false;
            return;
        }

        string expected = CompareTextBox.Text?.Trim() ?? string.Empty;
        if (string.IsNullOrEmpty(expected))
        {
            ResultBorder.IsVisible = false;
            return;
        }

        bool match = string.Equals(_calculatedHash, expected, StringComparison.OrdinalIgnoreCase);

        ResultBorder.IsVisible = true;
        if (match)
        {
            ResultBorder.Background = Brush.Parse("#122E1A");
            ResultBorder.BorderBrush = Brush.Parse("#22C55E");
            ResultIconTextBlock.Text = "✓";
            ResultIconTextBlock.Foreground = Brush.Parse("#22C55E");
            ResultTextBlock.Text = "CHECKSUMS MATCH";
            ResultTextBlock.Foreground = Brush.Parse("#22C55E");
        }
        else
        {
            ResultBorder.Background = Brush.Parse("#2C1212");
            ResultBorder.BorderBrush = Brush.Parse("#EF4444");
            ResultIconTextBlock.Text = "✗";
            ResultIconTextBlock.Foreground = Brush.Parse("#EF4444");
            ResultTextBlock.Text = "CHECKSUMS MISMATCH";
            ResultTextBlock.Foreground = Brush.Parse("#EF4444");
        }
    }
}
