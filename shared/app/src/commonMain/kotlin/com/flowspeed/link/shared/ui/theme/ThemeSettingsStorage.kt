package com.flowspeed.link.shared.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow

interface ThemeSettingsStorage {
    val theme: MutableStateFlow<String>
    val defaultDarkTheme: MutableStateFlow<String>
    val defaultLightTheme: MutableStateFlow<String>
}
