using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Connection;
using Flow.Core.Models;
using Flow.Core.Storage;

namespace Flow.Core.Part;

public abstract class PartDownloader<TPart> where TPart : class, IDownloadPart
{
    public const int PartMaxTries = 10;
    public const int RetryDelayMs = 1000;

    private readonly Func<DestWriter> _getDestWriter;
    private CancellationTokenSource? _cts;
    private Task? _downloadTask;

    public TPart Part { get; }
    public bool Active { get; private set; }
    public int Tries { get; protected set; }
    public bool Stop { get; private set; }

    public Exception? LastCriticalException { get; private set; }
    public Exception? LastException { get; private set; }

    public Action<Exception>? OnTooManyErrors { get; set; }

    protected PartDownloader(TPart part, Func<DestWriter> getDestWriter)
    {
        Part = part ?? throw new ArgumentNullException(nameof(part));
        _getDestWriter = getDestWriter ?? throw new ArgumentNullException(nameof(getDestWriter));
    }

    public abstract long HowMuchCanRead(long maxAllowed);
    public abstract Task<Connection<HttpResponseInfo>> ConnectAndVerifyAsync(CancellationToken cancellationToken);

    public void Start()
    {
        lock (this)
        {
            if (Active) return;
            Stop = false;
            Active = true;
        }

        _cts = new CancellationTokenSource();
        var cancellationToken = _cts.Token;

        _downloadTask = Task.Run(async () =>
        {
            Tries = 0;
            LastCriticalException = null;
            LastException = null;

            try
            {
                while (!cancellationToken.IsCancellationRequested && !Stop)
                {
                    if (Tries > 0)
                    {
                        await Task.Delay(RetryDelayMs, cancellationToken);
                    }

                    if (HaveTooManyErrors())
                    {
                        ICantRetryAnymore(new Exception($"Too many errors on part {Part.GetId()}", LastException));
                        break;
                    }

                    try
                    {
                        await DownloadLoopAsync(cancellationToken);
                    }
                    catch (Exception ex)
                    {
                        Tries++;
                        OnCanceled(ex);
                        var retryResult = CanRetry(ex);
                        if (retryResult == CanRetryResult.Yes)
                        {
                            continue;
                        }
                        if (retryResult == CanRetryResult.NoAndStopDownloadJob)
                        {
                            ICantRetryAnymore(ex);
                        }
                        break;
                    }

                    // Download started but wait for finish or cancel
                    var status = await AwaitFinishOrErrorAsync();
                    if (status.State == PartDownloadState.Canceled)
                    {
                        Tries++;
                        var retryResult = CanRetry(LastException ?? new Exception("Canceled"));
                        if (retryResult == CanRetryResult.Yes)
                        {
                            continue;
                        }
                        if (retryResult == CanRetryResult.NoAndStopDownloadJob)
                        {
                            ICantRetryAnymore(LastException ?? new Exception("Canceled"));
                        }
                        break;
                    }
                    else if (status.State == PartDownloadState.Completed)
                    {
                        break;
                    }
                }
            }
            finally
            {
                Active = false;
                if (!Part.IsCompleted)
                {
                    OnNewStatus(PartDownloadStatus.Idle);
                }
            }
        }, cancellationToken);
    }

    public void StopDownloader()
    {
        Stop = true;
        _cts?.Cancel();
    }

    public async Task JoinAsync()
    {
        if (_downloadTask != null)
        {
            try
            {
                await _downloadTask;
            }
            catch
            {
                // Ignore background exceptions since they are handled
            }
        }
    }

    private CanRetryResult CanRetry(Exception ex)
    {
        if (ex is OperationCanceledException)
        {
            return CanRetryResult.No;
        }

        if (ex is Flow.Core.Exceptions.DownloadValidationException validationEx)
        {
            return validationEx.IsCritical ? CanRetryResult.NoAndStopDownloadJob : CanRetryResult.Yes;
        }

        return CanRetryResult.Yes;
    }

    private void ICantRetryAnymore(Exception ex)
    {
        LastCriticalException = ex;
        OnTooManyErrors?.Invoke(ex);
    }

    public bool HaveTooManyErrors() => Tries >= PartMaxTries;
    public bool HaveCriticalError() => LastCriticalException != null;
    public bool Injured() => HaveTooManyErrors() || HaveCriticalError();

    private async Task DownloadLoopAsync(CancellationToken cancellationToken)
    {
        OnNewStatus(PartDownloadStatus.Connecting);

        var conn = await ConnectAndVerifyAsync(cancellationToken);
        if (Stop || cancellationToken.IsCancellationRequested)
        {
            conn.Dispose();
            OnCanceled(new OperationCanceledException(cancellationToken));
            return;
        }

        _ = Task.Run(async () =>
        {
            try
            {
                using (conn)
                using (var writer = _getDestWriter())
                {
                    writer.Prepare();
                    await CopyDataAsync(conn.Stream, writer, cancellationToken);
                }
            }
            catch (Exception ex)
            {
                OnCanceled(ex);
            }
        }, cancellationToken);
    }

    private async Task CopyDataAsync(Stream source, DestWriter destWriter, CancellationToken cancellationToken)
    {
        byte[] buffer = new byte[8192];
        bool firstLoop = true;

        while (true)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (Stop)
            {
                throw new OperationCanceledException(cancellationToken);
            }

            long howMuchICanReadAllowed = HowMuchCanRead(buffer.Length);
            long howMuchReadFromBuffer = Math.Min(buffer.Length, howMuchICanReadAllowed);

            if (howMuchICanReadAllowed <= 0)
            {
                if (Part.IsCompleted)
                {
                    OnFinish();
                }
                else
                {
                    throw new OperationCanceledException($"Part split occurred, cancel: {Part.GetId()}");
                }
                break;
            }

            int bytesToRead = (int)howMuchReadFromBuffer;
            int readCount = await source.ReadAsync(buffer.AsMemory(0, bytesToRead), cancellationToken);

            if (readCount <= 0)
            {
                OnFinish();
                break;
            }

            destWriter.SeekPos = Part.Current;
            await destWriter.WriteAsync(buffer.AsMemory(0, readCount));
            Part.Current += readCount;
            await OnDataReadAsync(readCount, cancellationToken);

            if (firstLoop)
            {
                Tries = 0;
                OnNewStatus(PartDownloadStatus.ReceivingData);
                firstLoop = false;
            }
        }
    }

    protected virtual Task OnDataReadAsync(int bytesRead, CancellationToken cancellationToken)
    {
        return Task.CompletedTask;
    }

    protected virtual void OnCanceled(Exception ex)
    {
        LastException = ex;
        OnNewStatus(PartDownloadStatus.FromCanceled(ex));
    }

    protected virtual void OnFinish()
    {
        OnNewStatus(PartDownloadStatus.Completed);
    }

    public void OnNewStatus(PartDownloadStatus status)
    {
        Part.Status = status;
    }

    public async Task<PartDownloadStatus> AwaitFinishOrErrorAsync()
    {
        var tcs = new TaskCompletionSource<PartDownloadStatus>();

        Action<PartDownloadStatus>? handler = null;
        handler = status =>
        {
            if (status == PartDownloadStatus.Completed || status.State == PartDownloadState.Canceled)
            {
                Part.StatusChanged -= handler;
                tcs.TrySetResult(status);
            }
        };

        Part.StatusChanged += handler;

        if (Part.Status == PartDownloadStatus.Completed || Part.Status.State == PartDownloadState.Canceled)
        {
            Part.StatusChanged -= handler;
            tcs.TrySetResult(Part.Status);
        }

        return await tcs.Task;
    }

    public async Task<bool> AwaitToEnsureDataBeingTransferredAsync()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var tcs = new TaskCompletionSource<bool>();

        Action<PartDownloadStatus>? handler = null;
        handler = status =>
        {
            if (status == PartDownloadStatus.Completed || status == PartDownloadStatus.ReceivingData)
            {
                Part.StatusChanged -= handler;
                tcs.TrySetResult(true);
            }
            else if (status.State == PartDownloadState.Canceled)
            {
                Part.StatusChanged -= handler;
                tcs.TrySetResult(false);
            }
        };

        Part.StatusChanged += handler;

        if (Part.Status == PartDownloadStatus.Completed || Part.Status == PartDownloadStatus.ReceivingData)
        {
            Part.StatusChanged -= handler;
            return true;
        }
        else if (Part.Status.State == PartDownloadState.Canceled)
        {
            Part.StatusChanged -= handler;
            return false;
        }

        using (cts.Token.Register(() =>
        {
            Part.StatusChanged -= handler;
            tcs.TrySetResult(false);
        }))
        {
            return await tcs.Task;
        }
    }

    public async Task AwaitIdleAsync()
    {
        var tcs = new TaskCompletionSource();
        Action<PartDownloadStatus>? handler = null;
        handler = status =>
        {
            if (status.State == PartDownloadState.Canceled || status == PartDownloadStatus.Completed || status == PartDownloadStatus.Idle)
            {
                Part.StatusChanged -= handler;
                tcs.TrySetResult();
            }
        };

        Part.StatusChanged += handler;

        if (Part.Status.State == PartDownloadState.Canceled || Part.Status == PartDownloadStatus.Completed || Part.Status == PartDownloadStatus.Idle)
        {
            Part.StatusChanged -= handler;
            return;
        }

        await tcs.Task;
    }

    private enum CanRetryResult
    {
        Yes,
        No,
        NoAndStopDownloadJob
    }
}
