namespace Flow.Core.Connection.Proxy;

public record DownloadProxy(
    ProxyType Type,
    string Host,
    int Port,
    string? Username,
    string? Password
)
{
    public static DownloadProxy Default() => new(
        Type: ProxyType.Http,
        Host: "127.0.0.1",
        Port: 2080,
        Username: null,
        Password: null
    );
}
