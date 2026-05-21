using System;
using System.Threading;
using System.Threading.Tasks;

namespace Flow.Core.Part;

public class Throttler
{
    private long _bytesPerSecond;
    private long _allocatedBytes;
    private long _lastResetTimeTicks;
    private readonly object _lock = new();

    public long BytesPerSecond
    {
        get => Interlocked.Read(ref _bytesPerSecond);
        set => Interlocked.Exchange(ref _bytesPerSecond, value);
    }

    public Throttler(long bytesPerSecond = 0)
    {
        _bytesPerSecond = bytesPerSecond;
        _lastResetTimeTicks = DateTime.UtcNow.Ticks;
    }

    public async Task ThrottleAsync(int bytes, CancellationToken cancellationToken = default)
    {
        long limit = BytesPerSecond;
        if (limit <= 0) return;

        while (true)
        {
            cancellationToken.ThrowIfCancellationRequested();

            long now = DateTime.UtcNow.Ticks;
            long elapsedTicks = now - _lastResetTimeTicks;
            double elapsedSeconds = (double)elapsedTicks / TimeSpan.TicksPerSecond;

            lock (_lock)
            {
                // Reset bucket every 1 second
                if (elapsedSeconds >= 1.0)
                {
                    _allocatedBytes = 0;
                    _lastResetTimeTicks = now;
                    elapsedSeconds = 0;
                }

                if (_allocatedBytes + bytes <= limit)
                {
                    _allocatedBytes += bytes;
                    return;
                }
            }

            double timeToWaitSeconds = 1.0 - elapsedSeconds;
            int delayMs = (int)(timeToWaitSeconds * 1000);
            if (delayMs > 0)
            {
                await Task.Delay(Math.Min(delayMs, 100), cancellationToken);
            }
        }
    }
}
