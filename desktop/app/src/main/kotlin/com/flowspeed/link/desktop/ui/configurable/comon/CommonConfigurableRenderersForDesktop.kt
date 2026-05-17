package com.flowspeed.link.desktop.ui.configurable.comon

import com.flowspeed.link.desktop.ui.configurable.comon.renderer.BooleanConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.DayOfWeekConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.EnumConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.FileChecksumConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.FloatConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.FolderConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.IntConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.LongConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.PerHostSettingsConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.SpeedLimitConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.StringConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.ThemeConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.TimeConfigurableRenderer
import com.flowspeed.link.desktop.ui.configurable.comon.renderer.ProxyConfigurableRenderer
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
