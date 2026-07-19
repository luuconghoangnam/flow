package com.flowspeed.link.desktop.di

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.flowspeed.lib.util.compose.IIconResolver
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager
import com.flowspeed.lib.util.compose.localizationmanager.LanguageSourceProvider
import com.flowspeed.link.desktop.AppComponent
import com.flowspeed.link.desktop.DesktopAddDownloadDialogManager
import com.flowspeed.link.desktop.DesktopDownloadDialogManager
import com.flowspeed.link.desktop.PowerActionManager
import com.flowspeed.link.desktop.pages.category.DesktopCategoryDialogManager
import com.flowspeed.link.desktop.pages.settings.FontManager
import com.flowspeed.link.desktop.repository.AppRepository
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.shared.util.DownloadItemOpener
import com.flowspeed.link.resources.FlowLanguageResources
import com.flowspeed.link.shared.pagemanager.EditDownloadDialogManager
import com.flowspeed.link.shared.pagemanager.FileChecksumDialogManager
import com.flowspeed.link.shared.pagemanager.NotificationSender
import com.flowspeed.link.shared.pagemanager.PerHostSettingsPageManager
import com.flowspeed.link.shared.pagemanager.QueuePageManager
import com.flowspeed.link.shared.pagemanager.SettingsPageManager
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.link.shared.ui.widget.NotificationManager
import com.flowspeed.link.shared.util.SizeAndSpeedUnitProvider
import com.flowspeed.link.shared.util.ui.IMyIcons
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * UI & presentation DI module.
 *
 * Provides: AppComponent (root navigation), theme manager,
 * font manager, language manager, notification manager,
 * and the app repository.
 */
val uiModule = module {
    single {
        AppInfo.definedPaths
    }.bind<com.flowspeed.link.shared.util.DefinedPaths>()
    single {
        AppRepository(get(), get(), get(), get(), get(), get(), get(), get())
    }.apply {
        bind<BaseAppRepository>()
        bind<SizeAndSpeedUnitProvider>()
    }
    single { ThemeManager(get(), get(), get()) }
    single { FontManager(get()) }
    single {
        LanguageManager(
            get(),
            LanguageSourceProvider(
                FlowLanguageResources.defaultLanguageResource,
                FlowLanguageResources.languages,
            )
        )
    }
    single { MyIcons }.apply {
        bind<IMyIcons>()
        bind<IIconResolver>()
    }
    single { NotificationManager() }
    single {
        val lifecycle = LifecycleRegistry(Lifecycle.State.RESUMED)
        val context = DefaultComponentContext(lifecycle)
        AppComponent(context)
    }.apply {
        bind<DesktopDownloadDialogManager>()
        bind<DesktopAddDownloadDialogManager>()
        bind<DesktopCategoryDialogManager>()
        bind<EditDownloadDialogManager>()
        bind<FileChecksumDialogManager>()
        bind<QueuePageManager>()
        bind<NotificationSender>()
        bind<DownloadItemOpener>()
        bind<PerHostSettingsPageManager>()
        bind<PowerActionManager>()
        bind<SettingsPageManager>()
    }
}
