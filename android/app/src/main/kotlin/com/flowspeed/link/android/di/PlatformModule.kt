package com.flowspeed.link.android.di

import android.app.Application
import android.content.Context
import com.flowspeed.lib.util.AppVersionTracker
import com.flowspeed.lib.util.startup.AbstractStartupManager
import com.flowspeed.lib.util.startup.Startup
import com.flowspeed.link.android.FlowApp
import com.flowspeed.link.android.receiver.StartOnBootBroadcastReceiver
import com.flowspeed.link.android.repository.AppRepository
import com.flowspeed.link.android.util.AndroidDefinedPaths
import com.flowspeed.link.android.util.AndroidDownloadItemOpener
import com.flowspeed.link.android.util.AppInfo
import com.flowspeed.link.android.util.FlowAppManager
import com.flowspeed.link.android.util.FlowServiceNotificationManager
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.DownloadItemOpener
import com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider
import com.flowspeed.link.shared.util.appinfo.PreviousVersion
import com.flowspeed.link.shared.util.autoremove.RemovedDownloadsFromDiskTracker
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Platform services DI module (Android).
 *
 * Provides: app context, OS startup manager, version tracking,
 * download item opener, notification manager, and Android-specific managers.
 */
fun platformModule(context: FlowApp) = module {
    single { context }.apply {
        bind<FlowApp>()
        bind<Application>()
        bind<Context>()
    }
    single {
        AppInfo.definedPaths
    }.apply {
        bind<DefinedPaths>()
        bind<AndroidDefinedPaths>()
    }
    single {
        Startup.getStartUpManager(get(), StartOnBootBroadcastReceiver::class.java)
    }.bind<AbstractStartupManager>()
    single {
        RemovedDownloadsFromDiskTracker(get(), get(), get())
    }
    single {
        val definedPaths = get<DefinedPaths>()
        PreviousVersion(
            systemPath = definedPaths.systemDir.toFile(),
            currentVersion = AppVersion.get(),
        )
    }
    single {
        AppVersionTracker(
            previousVersion = { get<PreviousVersion>().get() },
            currentVersion = AppVersion.get(),
        )
    }
    single {
        AppRepository(get(), get(), get(), get(), get(), get(), get())
    }.apply {
        bind<BaseAppRepository>()
        bind<SizeAndSpeedUnitProvider>()
    }
    single {
        FlowAppManager(get(), get(), get(), get(), get(), get(), get())
    }
    single {
        FlowServiceNotificationManager(get(), get(), get(), get(), get())
    }
    single {
        AndroidDownloadItemOpener(get())
    }.bind<DownloadItemOpener>()
}
