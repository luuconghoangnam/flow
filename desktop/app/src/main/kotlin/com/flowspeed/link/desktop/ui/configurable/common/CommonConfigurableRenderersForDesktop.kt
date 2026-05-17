package com.flowspeed.link.desktop.ui.configurable.common

import com.flowspeed.link.desktop.ui.configurable.common.renderer.BooleanConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.DayOfWeekConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.EnumConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.FileChecksumConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.FloatConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.FolderConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.IntConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.LongConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.PerHostSettingsConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.SpeedLimitConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.StringConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.ThemeConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.TimeConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.common.renderer.ProxyConfigurableRenderer
import com.flowspeed.link.shared.ui.configurable.CommonConfigurableRenderers

val CommonConfigurableRenderersForDesktop = CommonConfigurableRenderers(
    booleanConfigurableRenderer = BooleanConfigurableRenderer,
    dayOfWeekConfigurableRenderer = DayOfWeekConfigurableRenderer,
    fileChecksumConfigurableRenderer = FileChecksumConfigurableRenderer,
    floatConfigurableRenderer = FloatConfigurableRenderer,
    folderConfigurableRenderer = FolderConfigurableRenderer,
    intConfigurableRenderer = IntConfigurableRenderer,
    longConfigurableRenderer = LongConfigurableRenderer,
    perHostSettingsConfigurableRenderer = PerHostSettingsConfigurableRenderer,
    enumConfigurableRenderer = EnumConfigurableRenderer,
    speedConfigurableRenderer = SpeedLimitConfigurableRenderer,
    stringConfigurableRenderer = StringConfigurableRenderer,
    themeConfigurableRenderer = ThemeConfigurableRenderer,
    timeConfigurableRenderer = TimeConfigurableRenderer,
    proxyConfigurableRenderer = ProxyConfigurableRenderer,
)
