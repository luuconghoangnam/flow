package com.flowspeed.link.desktop.ui.configurable.common.renderer

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.flowspeed.link.desktop.ui.configurable.ConfigTemplate
import com.flowspeed.link.shared.ui.configurable.ConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.TitleAndDescription
import com.flowspeed.link.shared.ui.configurable.ConfigurableUiProps
import com.flowspeed.link.shared.ui.configurable.isConfigEnabled
import com.flowspeed.link.shared.ui.configurable.item.BooleanConfigurable
import com.flowspeed.link.shared.ui.widget.CheckBox
import com.flowspeed.link.shared.ui.widget.Switch

object BooleanConfigurableRenderer : ConfigurableRenderer<BooleanConfigurable> {
    @Composable
    override fun RenderConfigurable(configurable: BooleanConfigurable, configurableUiProps: ConfigurableUiProps) {
        RenderBooleanConfig(configurable, configurableUiProps)
    }

    @Composable
    private fun RenderBooleanConfig(
        cfg: BooleanConfigurable,
        configurableUiProps: ConfigurableUiProps,
    ) {
        val checked = cfg.stateFlow.collectAsState().value
        val setValue = cfg::set
        val enabled = isConfigEnabled()
        ConfigTemplate(
            modifier = configurableUiProps.modifier.padding(configurableUiProps.itemPaddingValues),
            title = {
                TitleAndDescription(cfg, true)
            },
            value = {
                when (cfg.renderMode) {
                    BooleanConfigurable.RenderMode.Checkbox -> {
                        CheckBox(
                            value = checked,
                            enabled = enabled,
                            onValueChange = {
                                setValue(it)
                            }
                        )
                    }

                    BooleanConfigurable.RenderMode.Switch -> {
                        Switch(
                            checked = checked,
                            enabled = enabled,
                            onCheckedChange = {
                                setValue(it)
                            }
                        )
                    }
                }
            })
    }
}
