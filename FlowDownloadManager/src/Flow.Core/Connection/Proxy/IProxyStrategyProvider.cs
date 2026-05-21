namespace Flow.Core.Connection.Proxy;

public interface IProxyStrategyProvider
{
    ProxyStrategy GetProxyStrategyFor(string url);
}
