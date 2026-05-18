package com.flowspeed.link.desktop.di

import com.flowspeed.link.UpdateDownloadLocationProvider
import com.flowspeed.link.UpdateManager
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.github.GithubApi
import com.flowspeed.link.shared.updater.UpdateDownloaderViaDownloadSystem
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.updateapplier.DesktopDirectLinkUpdateApplier
import com.flowspeed.link.updateapplier.UpdateApplier
import com.flowspeed.link.updatechecker.GithubUpdateChecker
import com.flowspeed.link.updatechecker.UpdateChecker
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Auto-update DI module.
 *
 * Provides: update checker (GitHub releases), update applier,
 * and the UpdateManager that orchestrates the update flow.
 */
val updaterModule = module {
    single {
        val definedPaths = get<DefinedPaths>()
        UpdateDownloadLocationProvider { definedPaths.updateDownloadLocation.toFile() }
    }
    single<UpdateApplier> {
        val definedPaths = get<DefinedPaths>()
        DesktopDirectLinkUpdateApplier(
            installationFolder = AppInfo.installationFolder,
            updateFolder = definedPaths.updateDir.toString(),
            logDir = definedPaths.logDir.toString(),
            appName = AppInfo.name,
            updatePreparer = UpdateDownloaderViaDownloadSystem(get(), get()),
        )
    }
    single<UpdateChecker> {
        GithubUpdateChecker(
            AppVersion.get(),
            githubApi = GithubApi(
                owner = SharedConstants.projectGithubOwner,
                repo = SharedConstants.projectGithubRepo,
                client = get<OkHttpClient>().newBuilder().build()
            )
        )
    }
    single {
        UpdateManager(
            updateChecker = get(),
            updateApplier = get(),
            appVersionTracker = get(),
        )
    }
}
