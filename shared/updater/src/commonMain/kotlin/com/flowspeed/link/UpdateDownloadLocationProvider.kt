package com.flowspeed.link

import java.io.File

fun interface UpdateDownloadLocationProvider {
    fun getSaveLocation(): File
}