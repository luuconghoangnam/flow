package com.flowspeed.link.android.di

import com.flowspeed.link.shared.util.DownloadFoldersRegistry
import com.flowspeed.link.android.pages.onboarding.permissions.FlowPermissions
import com.flowspeed.link.android.pages.onboarding.permissions.PermissionManager
import com.flowspeed.link.android.storage.AndroidExtraDownloadItemSettings
import com.flowspeed.link.android.storage.AndroidExtraQueueSettings
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.storage.ExtraDownloadSettingsStorage
import com.flowspeed.link.shared.storage.ExtraQueueSettingsStorage
import com.flowspeed.link.shared.storage.IExtraDownloadSettingsStorage
import com.flowspeed.link.shared.storage.IExtraQueueSettingsStorage
import com.flowspeed.link.shared.util.DefinedPaths
import com.flowspeed.link.shared.util.DownloadSystem
import com.flowspeed.link.shared.util.FileIconProvider
import com.flowspeed.link.shared.util.FileIconProviderUsingCategoryIcons
import com.flowspeed.link.shared.util.category.CategoryFileStorage
import com.flowspeed.link.shared.util.category.CategoryManager
import com.flowspeed.link.shared.util.category.CategoryStorage
import com.flowspeed.link.shared.util.category.DefaultCategories
import com.flowspeed.link.shared.util.category.DownloadManagerCategoryItemProvider
import com.flowspeed.link.shared.util.category.ICategoryItemProvider
import com.flowspeed.link.shared.util.ondownloadcompletion.NoOpOnDownloadCompletionActionProvider
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionProvider
import com.flowspeed.link.shared.util.ondownloadcompletion.OnDownloadCompletionActionRunner
import com.flowspeed.link.shared.util.onqueuecompletion.NoopOnQueueCompletionActionProvider
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import com.flowspeed.link.shared.util.onqueuecompletion.OnQueueEventActionRunner
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * High-level download system DI module (Android).
 *
 * Provides: DownloadSystem facade, category manager,
 * file icon provider, extra settings storage, and event action runners.
 */
val downloadSystemModule = module {
    single {
        val definedPaths = get<DefinedPaths>()
        get<DownloadFoldersRegistry>().registerAndGet(definedPaths.categoriesDir)
        CategoryFileStorage(
            file = definedPaths.categoriesFile.toFile(),
            fileSaver = get()
        )
    }.bind<CategoryStorage>()
    single {
        FileIconProviderUsingCategoryIcons(get(), get(), get(), get())
    }.bind<FileIconProvider>()
    single {
        DefaultCategories(
            icons = get(),
            getDefaultDownloadFolder = {
                get<BaseAppSettingsStorage>().defaultDownloadFolder.value
            }
        )
    }
    single { DownloadManagerCategoryItemProvider(get()) }.bind<ICategoryItemProvider>()
    single {
        CategoryManager(
            categoryStorage = get(),
            scope = get(),
            defaultCategoriesFactory = get(),
            categoryItemProvider = get(),
        )
    }
    single {
        DownloadSystem(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    single {
        val definedPaths = get<DefinedPaths>()
        val folder = get<DownloadFoldersRegistry>().registerAndGet(definedPaths.extraDownloadSettings)
        ExtraDownloadSettingsStorage(folder, get(), AndroidExtraDownloadItemSettings)
    }.bind<IExtraDownloadSettingsStorage<*>>()
    single {
        val definedPaths = get<DefinedPaths>()
        val folder = get<DownloadFoldersRegistry>().registerAndGet(definedPaths.extraQueueSettings)
        ExtraQueueSettingsStorage(folder, get(), AndroidExtraQueueSettings)
    }.bind<IExtraQueueSettingsStorage<*>>()
    single<OnDownloadCompletionActionProvider> { NoOpOnDownloadCompletionActionProvider() }
    single<OnQueueCompletionActionProvider> { NoopOnQueueCompletionActionProvider() }
    single {
        OnDownloadCompletionActionRunner(
            downloadManagerMinimalControl = get(),
            scope = get(),
            onDownloadCompletionActionProvider = get(),
        )
    }
    single {
        OnQueueEventActionRunner(
            queueManager = get(),
            scope = get(),
            onQueueCompletionActionProvider = get(),
        )
    }
    single {
        PermissionManager(
            FlowPermissions.importantPermissions,
            get(),
        )
    }
}
