namespace Flow.Core.Models;

public enum OnDuplicateStrategy
{
    AddNumbered,
    OverrideDownload,
    Abort
}

public static class OnDuplicateStrategyExtensions
{
    public static OnDuplicateStrategy OrDefault(this OnDuplicateStrategy? strategy)
    {
        return strategy ?? OnDuplicateStrategy.AddNumbered;
    }
}
