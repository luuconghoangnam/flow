package com.flowspeed.link.desktop.pages.about

import androidx.compose.foundation.*
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.desktop.utils.AppInfo
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.shared.util.ui.widget.MyIcon
import com.flowspeed.link.shared.ui.widget.LinkText
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.util.ui.LocalContentColor
import com.flowspeed.link.shared.util.ui.theme.myShapes
import com.flowspeed.lib.util.URLOpener
import com.flowspeed.lib.util.HttpUrlUtils
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.resources.myStringResource

@Composable
fun AboutPage(
    onRequestShowOpenSourceLibraries: () -> Unit,
    onRequestShowTranslators: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        // App icon
        Image(
            MyIcons.appIcon.rememberPainter(),
            null,
            Modifier
                .size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        // App name
        Text(
            AppInfo.displayName,
            fontSize = myTextSizes.xl,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        // Version
        WithContentAlpha(0.6f) {
            Text(
                myStringResource(
                    Res.string.version_n,
                    Res.string.version_n_createArgs(
                        value = AppInfo.version.toString(),
                    )
                ),
                fontSize = myTextSizes.base,
            )
        }
        Spacer(Modifier.height(24.dp))

        // Credits section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AboutItem(
                icon = MyIcons.openSource,
                title = Res.string.powered_by_open_source_software.asStringSource(),
                description = Res.string.view_the_open_source_licenses.asStringSource(),
                onClick = { onRequestShowOpenSourceLibraries() }
            )
            AboutItem(
                icon = MyIcons.language,
                title = Res.string.localized_by_translators.asStringSource(),
                description = Res.string.meet_the_translators.asStringSource(),
                onClick = { onRequestShowTranslators() }
            )
        }

        Spacer(Modifier.weight(1f))

        // Footer
        WithContentAlpha(0.5f) {
            val websiteUrl = SharedConstants.projectWebsite
            val websiteDisplayName = remember(websiteUrl) {
                HttpUrlUtils.getHost(websiteUrl) ?: websiteUrl
            }
            LinkText(
                text = websiteDisplayName,
                link = websiteUrl,
                showExternalIndicator = false,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AboutItem(
    icon: IconSource,
    title: StringSource,
    description: StringSource,
    onClick: () -> Unit,
) {
    val shape = myShapes.defaultRounded
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, myColors.onBackground / 0.1f, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(myColors.surface / 0.3f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title.rememberString(),
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            WithContentAlpha(0.6f) {
                Text(description.rememberString(), fontSize = myTextSizes.sm)
            }
        }
    }
}
