package com.flowspeed.link.shared.pages.adddownload.multiple

import com.flowspeed.link.shared.util.category.CategorySelectionMode
import com.flowspeed.lib.downloader.NewDownloadItemProps
import kotlinx.coroutines.Deferred

fun interface OnRequestAdd {
    operator fun invoke(
        items: List<NewDownloadItemProps>,
        queueId: Long?,
        categorySelectionMode: CategorySelectionMode?,
    ): Deferred<List<Long>>
}
