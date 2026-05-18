package com.flowspeed.link.shared.util.downloadlocation

import com.flowspeed.link.shared.util.SystemDownloadLocationProvider
import java.io.File

abstract class DesktopDownloadLocationProvider() : SystemDownloadLocationProvider() {
    override fun getCommonDownloadLocation(): File {
        return File(System.getProperty("user.home"), "Downloads")
    }
}
