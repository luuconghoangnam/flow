using Flow.Core.Connection.Proxy;

namespace Flow.Desktop.Services;

public class DesktopProxyStrategyProvider : IProxyStrategyProvider
{
    public ProxyStrategy GetProxyStrategyFor(string url)
    {
        return ProxyStrategy.Direct;
    }
}
