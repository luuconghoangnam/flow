package com.flowspeed.link.android.di

import com.flowspeed.lib.util.compose.IIconResolver
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager
import com.flowspeed.lib.util.compose.localizationmanager.LanguageSourceProvider
import com.flowspeed.link.resources.FlowLanguageResources
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.link.shared.ui.widget.NotificationManager
import com.flowspeed.link.shared.util.ui.IMyIcons
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * UI DI module (Android).
 *
 * Provides: theme manager, language manager, icon resolver,
 * and notification manager.
 */
val uiModule = module {
    single {
        ThemeManager(get(), get(), get())
    }
    single {
        LanguageManager(
            get(),
            LanguageSourceProvider(
                FlowLanguageResources.defaultLanguageResource,
                FlowLanguageResources.languages,
            )
        )
    }
    single {
        MyIcons
    }.apply {
        bind<IMyIcons>()
        bind<IIconResolver>()
    }
    single { NotificationManager() }
}
