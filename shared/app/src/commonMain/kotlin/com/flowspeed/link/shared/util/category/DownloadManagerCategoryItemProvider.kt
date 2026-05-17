package com.flowspeed.link.shared.util.category

import com.flowspeed.lib.downloader.DownloadManager

class DownloadManagerCategoryItemProvider(
    private val dowManager: DownloadManager,
) : ICategoryItemProvider {
    override suspend fun getAll(): List<CategoryItemWithId> {
        return dowManager.getDownloadList().map {
            CategoryItemWithId(
                id = it.id,
                fileName = it.name,
                url = it.link
            )
        }
    }
}
