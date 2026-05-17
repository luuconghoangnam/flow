package com.flowspeed.link.desktop.utils

import com.flowspeed.link.desktop.AppArguments
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.desktop.storage.DesktopDefinedPaths
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.lib.util.platform.Platform
import okio.Path.Companion.toOkioPath
import java.io.File

object AppInfo {
    val name = SharedConstants.appName
    val displayName = SharedConstants.appDisplayName
    val packageName = SharedConstants.packageName
    val website = SharedConstants.projectWebsite
    val sourceCode = SharedConstants.projectSourceCode
    val translationsUrl = SharedConstants.projectTranslations


    val version = AppVersion.get()
    val platform = Platform.getCurrentPlatform()
    val exeFile: String? = run {
//        if (!AppProperties.isAppInstalled()){
//            return@run null
//        }
        System.getProperty("jpackage.app-path")
    }

    private fun File.findAppFolder() = generateSequence(this) { it.parentFile }
        .firstOrNull { it.name.endsWith(".app") }

    val installationFolder: String? = run {
        exeFile?.let(::File)
            ?.parentFile // executable path
            ?.let {
                when (Platform.getCurrentPlatform()) {
                    Platform.Desktop.Linux -> it.parentFile // <installationFolder>/bin/Flow
                    Platform.Desktop.MacOS -> it.findAppFolder() // /Applications/Flow.app
                    Platform.Desktop.Windows -> it // <installationFolder>/Flow.exe
                    else -> null
                }?.path
            }
    }

    private fun getUserDataDir(): File {
        val dataDirName = SharedConstants.dataDirName
        return File(System.getProperty("user.home"), dataDirName)
    }

    val dataDir by lazy {
        PortableUtil.getPortableDataDir(installationFolder) ?: getUserDataDir()
    }
    val definedPaths = DesktopDefinedPaths(dataDir.toOkioPath())
}

fun AppInfo.isAppInstalled(): Boolean {
    return AppInfo.exeFile != null
}

fun AppInfo.isInIDE(): Boolean {
    return !isAppInstalled()
}

fun AppInfo.isInDebugMode(): Boolean {
    return AppArguments.get().debug || AppProperties.isDebugMode() || isInIDE()
}
