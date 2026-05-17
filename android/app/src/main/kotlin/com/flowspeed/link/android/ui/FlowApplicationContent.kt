package com.flowspeed.link.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.flowspeed.link.android.ui.configurable.common.CommonConfigurableRenderersForAndroid
import com.flowspeed.link.android.ui.configurable.common.ConfigurableRenderersForAndroid
import com.flowspeed.link.android.util.AppInfo
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.ui.ProvideCommonSettings
import com.flowspeed.link.shared.ui.ProvideSizeUnits
import com.flowspeed.link.shared.ui.configurable.ConfigurableRendererRegistry
import com.flowspeed.link.shared.ui.theme.FlowTheme
import com.flowspeed.link.shared.ui.theme.ThemeManager
import com.flowspeed.link.shared.ui.widget.NotificationManager
import com.flowspeed.link.shared.ui.widget.ProvideLanguageManager
import com.flowspeed.link.shared.ui.widget.ProvideNotificationManager
import com.flowspeed.link.shared.util.PopUpContainer
import com.flowspeed.link.shared.util.ResponsiveBox
import com.flowspeed.link.shared.util.ui.ProvideDebugInfo
import com.flowspeed.lib.util.compose.IIconResolver
import com.flowspeed.lib.util.compose.localizationmanager.LanguageManager
import kotlin.collections.component1
import kotlin.collections.component2

@Composable
fun FlowApplicationContent(
    languageManager: LanguageManager,
    themeManager: ThemeManager,
    appSettingsStorage: BaseAppSettingsStorage,
    iconResolver: IIconResolver,
    appRepository: BaseAppRepository,
    notificationManager: NotificationManager,
    content: @Composable () -> Unit,
) {
    val configurableRendererRegistry = remember {
        ConfigurableRendererRegistry {
            listOf(
                CommonConfigurableRenderersForAndroid,
                ConfigurableRenderersForAndroid
            ).forEach {
                it.getAllRenderers().forEach { (key, renderer) ->
                    this.register(key, renderer)
                }
            }
        }
    }
    ProvideDebugInfo(AppInfo.isInDebugMode) {
        ProvideLanguageManager(languageManager) {
            ProvideCommonSettings(
                appSettings = appSettingsStorage,
                iconProvider = iconResolver,
                configurableRendererRegistry = configurableRendererRegistry,
            ) {
                ProvideNotificationManager(notificationManager) {
                    val myColors by themeManager.currentThemeColor.collectAsState()
                    val uiScale by appSettingsStorage.uiScale.collectAsState()
                    FlowTheme(
                        myColors = myColors,
                        fontFamily = null,
                        uiScale = uiScale,
                    ) {
                        ResponsiveBox {
                            ProvideSizeUnits(
                                appRepository
                            ) {
                                PopUpContainer {
                                    content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
