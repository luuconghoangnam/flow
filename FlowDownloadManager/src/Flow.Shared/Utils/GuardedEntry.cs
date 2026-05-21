using System;
using System.Threading;
using System.Threading.Tasks;

namespace Flow.Shared.Utils;

public interface IBaseGuardedEntry
{
    Task AwaitDoneAsync();
    bool IsDone();
}

public interface IGuardedEntry : IBaseGuardedEntry
{
    T? Action<T>(Func<T> block);
}

public interface ISuspendGuardedEntry : IBaseGuardedEntry
{
    Task<T?> ActionAsync<T>(Func<Task<T>> block);
    Task ActionAsync(Func<Task> block);
}

public abstract class BaseGuardedEntryImpl : IBaseGuardedEntry
{
    protected readonly TaskCompletionSource<bool> _tcs = new(TaskCreationOptions.RunContinuationsAsynchronously);

    public bool IsDone() => _tcs.Task.IsCompleted;

    public Task AwaitDoneAsync() => _tcs.Task;

    protected void SetIsDone()
    {
        _tcs.TrySetResult(true);
    }
}

public class GuardedEntryImpl : BaseGuardedEntryImpl, IGuardedEntry
{
    private readonly object _lock = new();

    public T? Action<T>(Func<T> block)
    {
        if (IsDone()) return default;

        lock (_lock)
        {
            if (IsDone()) return default;
            var result = block();
            SetIsDone();
            return result;
        }
    }
}

public class SuspendGuardedEntryImpl : BaseGuardedEntryImpl, ISuspendGuardedEntry
{
    private readonly SemaphoreSlim _semaphore = new(1, 1);

    public async Task<T?> ActionAsync<T>(Func<Task<T>> block)
    {
        if (IsDone()) return default;

        await _semaphore.WaitAsync();
        try
        {
            if (IsDone()) return default;
            var result = await block();
            SetIsDone();
            return result;
        }
        finally
        {
            _semaphore.Release();
        }
    }

    public async Task ActionAsync(Func<Task> block)
    {
        if (IsDone()) return;

        await _semaphore.WaitAsync();
        try
        {
            if (IsDone()) return;
            await block();
            SetIsDone();
        }
        finally
        {
            _semaphore.Release();
        }
    }
}

public static class GuardedEntry
{
    public static IGuardedEntry Create() => new GuardedEntryImpl();
    public static ISuspendGuardedEntry CreateSuspend() => new SuspendGuardedEntryImpl();
}
