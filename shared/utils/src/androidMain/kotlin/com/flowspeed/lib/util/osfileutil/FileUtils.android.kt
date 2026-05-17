package com.flowspeed.lib.util.osfileutil

actual fun getPlatformFileUtil(): FileUtils {
    return AndroidFileUtil()
}
