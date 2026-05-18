package com.flowspeed.link.android.util

import android.app.Application
import com.flowspeed.link.android.BuildConfig
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.lib.util.platform.Platform
import okio.Path.Companion.toOkioPath

object AppInfo {
    val isInDebugMode: Boolean = BuildConfig.DEBUG
    lateinit var context: Application
    fun init(context: Application) {
        this.context = context
    }

    val platform = Platform.Android
    val version = AppVersion.get()

    val definedPaths by lazy {
        AndroidDefinedPaths(
            dataDir = context.filesDir.resolve(
                SharedConstants.dataDirName
            ).toOkioPath()
        )
    }
}
