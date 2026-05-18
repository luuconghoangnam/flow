package com.flowspeed.link.android.util

import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager
import com.flowspeed.lib.util.guardedEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AndroidUi : KoinComponent {
    val themeManager: ThemeManager by inject()
    val languageManager: LanguageManager by inject()
    private var booted = guardedEntry()
    fun boot() {
        booted.action {
            themeManager.boot()
            languageManager.boot()
        }
    }
}
