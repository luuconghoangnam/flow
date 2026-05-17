package com.flowspeed.link.shared.util.extractors.linkextractor

import com.flowspeed.link.shared.downloaderinui.DownloaderInUiRegistry
import com.flowspeed.link.shared.util.extractors.Extractor
import com.flowspeed.lib.downloader.downloaditem.IDownloadCredentials
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


interface DownloadCredentialExtractor<T> : Extractor<T, List<IDownloadCredentials>> {
    override fun extract(input: T): List<IDownloadCredentials>
}

object DownloadCredentialFromStringExtractor :
    DownloadCredentialExtractor<String>, KoinComponent {
    val downloaderInUiRegistry: DownloaderInUiRegistry by inject()
    override fun extract(input: String): List<IDownloadCredentials> {
        return StringUrlExtractor.extract(input)
            .mapNotNull {
                downloaderInUiRegistry
                    .bestMatchForThisLink(it)
                    ?.createMinimumCredentials(it)
            }
    }
}
