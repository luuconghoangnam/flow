package com.flowspeed.lib.downloader.utils

import java.io.File

interface IDiskStat {
    fun getRemainingSpace(path: File): Long
}
