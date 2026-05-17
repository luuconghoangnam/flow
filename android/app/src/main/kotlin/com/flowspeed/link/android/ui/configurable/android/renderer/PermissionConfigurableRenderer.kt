package com.flowspeed.link.android.ui.configurable.android.renderer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.flowspeed.link.android.pages.onboarding.permissions.AppPermissionState
import com.flowspeed.link.android.pages.onboarding.permissions.rememberAppPermissionState
import com.flowspeed.link.android.ui.configurable.ConfigTemplate
import com.flowspeed.link.android.ui.configurable.NextIcon
import com.flowspeed.link.android.ui.configurable.TitleAndDescription
import com.flowspeed.link.android.ui.configurable.android.item.PermissionConfigurable
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.ui.configurable.ConfigurableRenderer
import com.flowspeed.link.shared.ui.configurable.ConfigurableUiProps
import com.flowspeed.link.shared.util.ui.LocalContentColor
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.lib.util.compose.asStringSource

object PermissionConfigurableRenderer : ConfigurableRenderer<PermissionConfigurable> {
    @Composable
    override fun RenderConfigurable(
        configurable: PermissionConfigurable,
        configurableUiProps: ConfigurableUiProps
    ) {
        val permission by configurable.stateFlow.collectAsState()
        val permissionState = rememberAppPermissionState(permission) { result ->

        }

        RenderPermissionConfigurable(
            cfg = configurable,
            configurableUiProps = configurableUiProps,
            permissionState = permissionState
        )
    }

    @Composable
    fun RenderPermissionConfigurable(
        cfg: PermissionConfigurable,
        configurableUiProps: ConfigurableUiProps,
        permissionState: AppPermissionState,
    ) {
        ConfigTemplate(
            modifier = configurableUiProps.modifier
                .clickable {
                    permissionState.launchRequest()
                }
                .padding(configurableUiProps.itemPaddingValues),
            title = {
                TitleAndDescription(
                    cfg = cfg,
                    describe = true,
                    describeContent = if (permissionState.isGranted) {
                        Res.string.permission_granted
                    } else {
                        Res.string.permission_not_granted
                    }.asStringSource().rememberString(),
                    describeWrapper = { content ->
                        val contentColor =
                            if (permissionState.isGranted) myColors.success else myColors.warning
                        CompositionLocalProvider(
                            LocalContentColor provides contentColor
                        ) {
                            content()
                        }
                    }
                )
            },
            value = {
                NextIcon()
            }
        )
    }
}
