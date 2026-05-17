package com.flowspeed.link.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.flowspeed.link.shared.repository.BaseAppRepository
import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.ui.configurable.ConfigurableRendererRegistry
import com.flowspeed.link.shared.ui.configurable.LocalConfigurationRendererRegistry
import com.flowspeed.link.shared.util.LocalUseRelativeDateTime
import com.flowspeed.link.shared.util.ProvideSizeAndSpeedUnit
import com.flowspeed.lib.util.compose.IIconResolver
import com.flowspeed.lib.util.compose.LocalIconFromUriResolver


@Composable
fun ProvideCommonSettings(
    appSettings: BaseAppSettingsStorage,
    iconProvider: IIconResolver,
    configurableRendererRegistry: ConfigurableRendererRegistry,
    content: @Composable () -> Unit,
) {
    val useNativeDateTime by appSettings.useRelativeDateTime.collectAsState()
    CompositionLocalProvider(
        LocalUseRelativeDateTime provides useNativeDateTime,
        LocalIconFromUriResolver provides iconProvider,
        LocalConfigurationRendererRegistry provides configurableRendererRegistry,
    ) {
        content()
    }
}

@Composable
fun ProvideSizeUnits(
    appRepository: BaseAppRepository,
    content: @Composable () -> Unit,
) {
    ProvideSizeAndSpeedUnit(
        sizeUnitConfig = appRepository.sizeUnit.collectAsState().value,
        speedUnitConfig = appRepository.speedUnit.collectAsState().value,
        content = content
    )
}
