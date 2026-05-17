package com.flowspeed.link.android.ui.configurable.android

import com.flowspeed.link.android.ui.configurable.android.item.PermissionConfigurable
import com.flowspeed.link.shared.ui.configurable.Configurable
import com.flowspeed.link.shared.ui.configurable.ConfigurableRenderer
import com.flowspeed.link.shared.ui.configurable.ContainsConfigurableRenderers

data class AndroidConfigurableRenderers(
    val permissionConfigurableRenderers: ConfigurableRenderer<PermissionConfigurable>,
) : ContainsConfigurableRenderers {
    override fun getAllRenderers(): Map<Configurable.Key, ConfigurableRenderer<*>> {
        return mapOf(
            PermissionConfigurable.Key to permissionConfigurableRenderers,
        )
    }
}
