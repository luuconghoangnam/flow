using System;
using System.Collections.Generic;
using System.Linq;

namespace Flow.Core.Models;

public interface IDownloadItemContextElement
{
}

public class DownloadItemContext
{
    private readonly Dictionary<Type, IDownloadItemContextElement> _elements = new();

    public DownloadItemContext(IEnumerable<IDownloadItemContextElement>? elements = null)
    {
        if (elements != null)
        {
            foreach (var el in elements)
            {
                _elements[el.GetType()] = el;
            }
        }
    }

    public T? Get<T>() where T : class, IDownloadItemContextElement
    {
        return _elements.TryGetValue(typeof(T), out var el) ? (T)el : null;
    }

    public DownloadItemContext Plus(IDownloadItemContextElement element)
    {
        var copy = new DownloadItemContext(_elements.Values);
        copy._elements[element.GetType()] = element;
        return copy;
    }

    public DownloadItemContext Plus(DownloadItemContext other)
    {
        var copy = new DownloadItemContext(_elements.Values);
        foreach (var pair in other._elements)
        {
            copy._elements[pair.Key] = pair.Value;
        }
        return copy;
    }

    public DownloadItemContext Minus<T>() where T : IDownloadItemContextElement
    {
        var copy = new DownloadItemContext(_elements.Values.Where(el => el.GetType() != typeof(T)));
        return copy;
    }

    public static readonly DownloadItemContext Empty = new();
}

public interface ICanPerformRemove { }
public interface ICanPerformResume { }
public interface ICanPerformPause { }

public class UserActor : ICanPerformPause, ICanPerformResume, ICanPerformRemove
{
    public static readonly UserActor Instance = new();
    private UserActor() { }
}

public class DuplicateRemovalActor : ICanPerformRemove
{
    public static readonly DuplicateRemovalActor Instance = new();
    private DuplicateRemovalActor() { }
}

public class QueueActor : ICanPerformPause, ICanPerformResume, ICanPerformRemove
{
    public long QueueId { get; }
    public QueueActor(long queueId) => QueueId = queueId;
}

public class StoppedBy : IDownloadItemContextElement
{
    public ICanPerformPause By { get; }
    public StoppedBy(ICanPerformPause by) => By = by;
}

public class ResumedBy : IDownloadItemContextElement
{
    public ICanPerformPause By { get; }
    public ResumedBy(ICanPerformPause by) => By = by;
}

public class RemovedBy : IDownloadItemContextElement
{
    public ICanPerformRemove By { get; }
    public RemovedBy(ICanPerformRemove by) => By = by;
}
