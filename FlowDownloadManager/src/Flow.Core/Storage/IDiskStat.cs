namespace Flow.Core.Storage;

public interface IDiskStat
{
    long GetRemainingSpace(string path);
}
