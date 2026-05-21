using System.Collections.Generic;
using System.Threading.Tasks;

namespace Flow.Integration;

public interface IIntegrationHandler
{
    Task AddDownloadAsync(List<IDownloadCredentialsFromIntegration> list, AddDownloadOptionsFromIntegration options);
    List<ApiQueueModel> ListQueues();
    Task AddDownloadTaskAsync(NewDownloadTask task);
}
