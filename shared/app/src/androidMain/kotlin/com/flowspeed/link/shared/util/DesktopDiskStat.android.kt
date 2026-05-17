package com.flowspeed.link.shared.util

import com.flowspeed.lib.downloader.utils.IDiskStat
import java.io.File

actual typealias PlatformDiskStat = AndroidDiskStat

class AndroidDiskStat : IDiskStat {
    override fun getRemainingSpace(path: File): Long {
        return path.freeSpace
    }
}
