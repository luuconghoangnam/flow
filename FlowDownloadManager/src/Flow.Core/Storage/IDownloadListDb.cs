using System.Collections.Generic;
using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core.Storage;

public interface IDownloadListDb
{
    Task<List<IDownloadItem>> GetAllAsync();
    Task<IDownloadItem?> GetByIdAsync(long id);
    Task AddAsync(IDownloadItem item);
    Task UpdateAsync(IDownloadItem item);
    Task RemoveAsync(IDownloadItem item);
    Task RemoveByIdAsync(long itemId);
    Task<long> GetLastIdAsync();
}
