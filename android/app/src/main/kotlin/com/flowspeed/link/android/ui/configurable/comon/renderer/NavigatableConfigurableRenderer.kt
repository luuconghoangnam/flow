package com.flowspeed.link.android.ui.configurable.comon.renderer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowspeed.link.android.ui.configurable.ConfigTemplate
import com.flowspeed.link.android.ui.configurable.NextIcon
import com.flowspeed.link.android.ui.configurable.TitleAndDescription
import com.flowspeed.link.shared.ui.configurable.ConfigurableRenderer
import com.flowspeed.link.shared.ui.configurable.ConfigurableUiProps
import com.flowspeed.link.shared.ui.configurable.item.NavigatableConfigurable
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.widget.MyIcon

object NavigatableConfigurableRenderer : ConfigurableRenderer<NavigatableConfigurable> {
    @Composable
    override fun RenderConfigurable(
        configurable: NavigatableConfigurable,
        configurableUiProps: ConfigurableUiProps
    ) {
        RenderPerHostSettingsConfigurable(
            cfg = configurable,
            configurableUiProps = configurableUiProps,
            onRequestOpenConfigWindow = configurable.onRequestNavigate,
        )
    }

    @Composable
    private fun RenderPerHostSettingsConfigurable(
        cfg: NavigatableConfigurable,
        configurableUiProps: ConfigurableUiProps,
        onRequestOpenConfigWindow: () -> Unit
    ) {
//    val value by cfg.stateFlow.collectAsState()
//    val setValue = cfg::set
//    val enabled = isConfigEnabled()

        ConfigTemplate(
            modifier = configurableUiProps.modifier
                .clickable {
                    onRequestOpenConfigWindow()
                }
                .padding(configurableUiProps.itemPaddingValues),
            title = {
                TitleAndDescription(cfg, true)
            },
            value = {
                NextIcon()
            },
        )
    }

}
