using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core.Storage;

public interface IDownloadPartListDb
{
    Task<IParts?> GetPartsAsync(long id);
    Task SetPartsAsync(long id, IParts parts);
    Task RemovePartsAsync(long id);
    Task ClearAsync();
}
