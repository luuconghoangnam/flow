namespace Flow.Core.Connection.Proxy;

public abstract record ProxyStrategy
{
    public record DirectStrategy : ProxyStrategy;
    public record UseSystemStrategy : ProxyStrategy;
    public record ManualProxyStrategy(DownloadProxy Proxy) : ProxyStrategy;
    public record ByScriptStrategy(string ScriptPath) : ProxyStrategy;

    public static readonly ProxyStrategy Direct = new DirectStrategy();
    public static readonly ProxyStrategy UseSystem = new UseSystemStrategy();
}
