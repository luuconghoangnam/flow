using Flow.Core.Connection;

namespace Flow.Desktop.Services;

public class DesktopUserAgentProvider : IUserAgentProvider
{
    public string? GetUserAgent()
    {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }
}
