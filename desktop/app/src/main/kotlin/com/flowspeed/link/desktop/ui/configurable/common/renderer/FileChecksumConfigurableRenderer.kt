package com.flowspeed.link.desktop.ui.configurable.common.renderer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.flowspeed.link.resources.Res
import com.flowspeed.link.desktop.ui.configurable.ConfigTemplate
import com.flowspeed.link.shared.ui.configurable.ConfigurableRenderer
import com.flowspeed.link.shared.ui.configurable.RenderSpinner
import com.flowspeed.link.desktop.ui.configurable.TitleAndDescription
import com.flowspeed.link.shared.ui.configurable.ConfigurableUiProps
import com.flowspeed.link.shared.ui.configurable.isConfigEnabled
import com.flowspeed.link.shared.ui.configurable.item.FileChecksumConfigurable
import com.flowspeed.link.shared.ui.widget.CheckBox
import com.flowspeed.link.shared.ui.widget.MyTextField
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.FileChecksum
import com.flowspeed.link.shared.util.FileChecksumAlgorithm
import com.flowspeed.lib.util.compose.resources.myStringResource

object FileChecksumConfigurableRenderer : ConfigurableRenderer<FileChecksumConfigurable> {
    @Composable
    override fun RenderConfigurable(configurable: FileChecksumConfigurable, configurableUiProps: ConfigurableUiProps) {
        RenderFileChecksumConfig(configurable, configurableUiProps)
    }

    @Composable
    private fun RenderFileChecksumConfig(cfg: FileChecksumConfigurable, configurableUiProps: ConfigurableUiProps) {
        val value by cfg.stateFlow.collectAsState()
        val setValue = cfg::set

        val enabled = isConfigEnabled()
        val hasFileChecksum = value != null
        ConfigTemplate(
            configurableUiProps.modifier.padding(configurableUiProps.itemPaddingValues),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleAndDescription(cfg, true)
                }
            },
            nestedContent = {
                Column(Modifier.align(Alignment.End)) {
                    AnimatedVisibility(
                        hasFileChecksum,
                    ) {
                        value?.let { value ->
                            Row(
                                Modifier
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RenderSpinner(
                                    possibleValues = FileChecksumAlgorithm
                                        .all()
                                        .map { it.algorithm },
                                    value = value.algorithm,
                                    modifier = Modifier.Companion,
                                    enabled = enabled,
                                    onSelect = {
                                        setValue(value.copy(algorithm = it))
                                    }
                                ) {
                                    Text(it)
                                }
                                Text(":", Modifier.padding(horizontal = 4.dp))
                                MyTextField(
                                    text = value.value,
                                    onTextChange = {
                                        setValue(value.copy(value = it))
                                    },
                                    shape = RectangleShape,
                                    textPadding = PaddingValues(4.dp),
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f),
                                    placeholder = myStringResource(Res.string.file_checksum),
                                )
                            }
                        }
                    }
                }
            },
            value = {
                CheckBox(
                    value = hasFileChecksum,
                    enabled = enabled,
                    onValueChange = {
                        if (it) {
                            setValue(
                                FileChecksum(
                                    FileChecksumAlgorithm.default().algorithm,
                                    "",
                                )
                            )
                        } else {
                            setValue(null)
                        }
                    })
            }
        )
    }
}
