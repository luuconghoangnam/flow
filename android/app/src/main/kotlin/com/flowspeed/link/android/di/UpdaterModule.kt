package com.flowspeed.link.android.di

import AndroidDirectLinkUpdateApplier
import com.flowspeed.lib.util.AppVersionTracker
import com.flowspeed.link.UpdateDownloadLocationProvider
import com.flowspeed.link.UpdateManager
import com.flowspeed.link.github.GithubApi
import com.flowspeed.link.shared.updater.UpdateDownloaderViaDownloadSystem
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.shared.util.appinfo.PreviousVersion
import com.flowspeed.link.updateapplier.UpdateApplier
import com.flowspeed.link.updatechecker.GithubUpdateChecker
import com.flowspeed.link.updatechecker.UpdateChecker
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Auto-update DI module (Android).
 *
 * Provides: update checker (GitHub releases), update applier (direct link),
 * version tracking, and the UpdateManager orchestrator.
 */
val updaterModule = module {
    single {
        val definedPaths = get<DefinedPaths>()
        UpdateDownloadLocationProvider { definedPaths.updateDownloadLocation.toFile() }
    }
    single<UpdateApplier> {
        AndroidDirectLinkUpdateApplier(
            updateDownloader = UpdateDownloaderViaDownloadSystem(get(), get()),
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
