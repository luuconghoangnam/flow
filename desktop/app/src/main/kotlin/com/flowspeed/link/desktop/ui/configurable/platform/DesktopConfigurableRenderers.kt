package com.flowspeed.link.desktop.ui.configurable.platform

import com.flowspeed.link.desktop.ui.configurable.platform.item.FontConfigurable
import com.flowspeed.link.shared.ui.configurable.item.ProxyConfigurable
import com.flowspeed.link.desktop.ui.configurable.platform.renderer.FontConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.ProxyConfigurableRenderer
import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.link.shared.ui.configurable.ConfigurableRenderer
import com.flowspeed.link.shared.ui.configurable.ContainsConfigurableRenderers

data class DesktopConfigurableRenderers(
    val fontConfigurableRenderer: ConfigurableRenderer<FontConfigurable>,
) : ContainsConfigurableRenderers {
    override fun getAllRenderers(): Map<Configurable.Key, ConfigurableRenderer<*>> {
        return mapOf(
            FontConfigurable.Key to fontConfigurableRenderer,
        )
    }
}

val PlatformConfigurableRenderersForDesktop = DesktopConfigurableRenderers(
    fontConfigurableRenderer = FontConfigurableRenderer,
)
