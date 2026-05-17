package com.flowspeed.link.desktop.ui.configurable.comon.renderer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowspeed.link.desktop.ui.configurable.ConfigTemplate
import com.flowspeed.link.shared.ui.configurable.ConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.TitleAndDescription
import com.flowspeed.link.shared.ui.configurable.ConfigurableUiProps
import com.flowspeed.link.shared.ui.configurable.item.StringConfigurable
import com.flowspeed.link.shared.ui.widget.MyTextField
import com.flowspeed.link.shared.util.ui.theme.myShapes

object StringConfigurableRenderer : ConfigurableRenderer<StringConfigurable> {
    @Composable
    override fun RenderConfigurable(configurable: StringConfigurable, configurableUiProps: ConfigurableUiProps) {
        RenderStringConfig(configurable, configurableUiProps)
    }

    @Composable
    fun RenderStringConfig(cfg: StringConfigurable, configurableUiProps: ConfigurableUiProps) {
        val value by cfg.stateFlow.collectAsState()
        val setValue = cfg::set
        ConfigTemplate(
            modifier = configurableUiProps.modifier.padding(configurableUiProps.itemPaddingValues),
            title = {
                TitleAndDescription(cfg, true)
            },
            value = {
                MyTextField(
                    modifier = Modifier.fillMaxWidth(),
                    text = value,
                    onTextChange = {
                        setValue(it)
                    },
                    shape = myShapes.defaultRounded,
                    textPadding = PaddingValues(4.dp),
                    placeholder = "",
                )
            }
        )
    }
}
