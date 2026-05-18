package com.flowspeed.link.desktop.di

import com.flowspeed.lib.util.AppVersionTracker
import com.flowspeed.lib.util.desktop.DesktopUtils
import com.flowspeed.lib.util.startup.AbstractStartupManager
import com.flowspeed.lib.util.startup.Startup
import com.flowspeed.link.desktop.AppArguments
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.desktop.utils.KeepAwakeManager
import com.flowspeed.link.desktop.utils.MemoryManager
import com.flowspeed.link.desktop.utils.native_messaging.NativeMessaging
import com.flowspeed.link.desktop.utils.native_messaging.NativeMessagingManifestApplier
import com.flowspeed.link.desktop.utils.renderapi.CustomRenderApi
import com.flowspeed.link.desktop.storage.DesktopDefinedPaths
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.appinfo.PreviousVersion
import com.flowspeed.link.shared.util.autoremove.RemovedDownloadsFromDiskTracker
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Platform services DI module.
 *
 * Provides: OS startup manager, native messaging, keep-awake,
 * memory manager, version tracking, and filesystem watcher.
 */
val platformModule = module {
    single {
        Startup.getStartUpManagerForDesktop(
            name = AppInfo.displayName,
            path = AppInfo.exeFile,
            args = listOf(AppArguments.Args.BACKGROUND),
            packageName = AppInfo.packageName,
        )
    }.bind<AbstractStartupManager>()
    single<NativeMessaging> {
        NativeMessaging(NativeMessagingManifestApplier.getForCurrentPlatform())
    }
    single {
        KeepAwakeManager(DesktopUtils.keepAwakeService(), get(), get())
    }
    single {
        MemoryManager(get(), get())
    }
    single {
        RemovedDownloadsFromDiskTracker(get(), get(), get())
    }
    single {
        val definedPaths = get<DefinedPaths>()
        PreviousVersion(
            systemPath = definedPaths.systemDir.toFile(),
            currentVersion = AppInfo.version,
        )
    }
    single {
        AppVersionTracker(
            previousVersion = { get<PreviousVersion>().get() },
            currentVersion = AppInfo.version,
        )
    }
    single {
        val definedPaths = get<DesktopDefinedPaths>()
        CustomRenderApi(definedPaths.renderApiFile)
    }
}
