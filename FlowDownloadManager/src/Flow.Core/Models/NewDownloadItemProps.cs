using Flow.Core.Models;

namespace Flow.Core.Models;

public record NewDownloadItemProps(
    IDownloadItem DownloadItem,
    IDownloadJobExtraConfig? ExtraConfig,
    OnDuplicateStrategy OnDuplicateStrategy,
    DownloadItemContext Context
);
