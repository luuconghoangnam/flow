using System.Text.Json.Serialization;

namespace Flow.Core.Connection.Proxy;

[JsonConverter(typeof(JsonStringEnumConverter))]
public enum ProxyType
{
    Http,
    Socks
}
