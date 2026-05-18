package com.flowspeed.link.android.pages.about

import androidx.compose.runtime.Composable
import androidx.compose.foundation.*
import com.flowspeed.link.shared.util.ui.icon.MyIcons
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.shared.ui.widget.Text
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowspeed.link.android.ui.page.PageHeader
import com.flowspeed.link.android.ui.page.PageTitle
import com.flowspeed.link.android.ui.page.PageUi
import com.flowspeed.link.android.ui.page.createAlphaForHeader
import com.flowspeed.link.android.util.compose.useBack
import com.flowspeed.link.shared.util.SharedConstants
import com.flowspeed.link.shared.util.ui.widget.MyIcon
import com.flowspeed.link.shared.ui.widget.TransparentIconActionButton
import com.flowspeed.link.shared.util.div
import com.flowspeed.link.resources.Res
import com.flowspeed.link.shared.util.AppVersion
import com.flowspeed.link.shared.util.ui.theme.myShapes
import com.flowspeed.link.shared.util.ui.theme.mySpacings
import com.flowspeed.lib.util.URLOpener
import com.flowspeed.lib.util.HttpUrlUtils
import com.flowspeed.lib.util.compose.IconSource
import com.flowspeed.lib.util.compose.StringSource
import com.flowspeed.lib.util.compose.asStringSource
import com.flowspeed.lib.util.compose.dpToPx
import com.flowspeed.lib.util.compose.resources.myStringResource

@Composable
fun AboutPage(
    onRequestShowOpenSourceLibraries: () -> Unit,
    onRequestShowTranslators: () -> Unit,
) {
    val state = rememberScrollState()
    var paddings by remember { mutableStateOf(PaddingValues.Zero) }
    val headerAlpha =
        createAlphaForHeader(state.value.toFloat(), paddings.calculateTopPadding().dpToPx(LocalDensity.current))
    PageUi(
        header = {
            val onBack = useBack()
            PageHeader(
                leadingIcon = {
                    TransparentIconActionButton(
                        icon = MyIcons.back,
                        contentDescription = Res.string.back.asStringSource()
                    ) {
                        onBack?.onBackPressed()
                    }
                },
                headerTitle = {
                    PageTitle(myStringResource(Res.string.about))
                },
                modifier = Modifier
                    .background(
                        myColors.background.copy(
                            alpha = headerAlpha * 0.75f
                        )
                    )
                    .statusBarsPadding(),
            )
        },
        footer = {},
    ) {
        paddings = it.paddingValues
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(state)
                .padding(it.paddingValues)
                .padding(horizontal = mySpacings.largeSpace),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            // App icon
            Image(
                MyIcons.appIcon.rememberPainter(),
                null,
                Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            // App name
            Text(
                SharedConstants.appDisplayName,
                fontSize = myTextSizes.xl,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            WithContentAlpha(0.6f) {
                Text(
                    myStringResource(
                        Res.string.version_n,
                        Res.string.version_n_createArgs(
                            value = AppVersion.get().toString(),
                        )
                    ),
                    fontSize = myTextSizes.base,
                )
            }
            Spacer(Modifier.height(32.dp))

            // Credits
            AboutItem(
                icon = MyIcons.openSource,
                title = Res.string.powered_by_open_source_software.asStringSource(),
                description = Res.string.view_the_open_source_licenses.asStringSource(),
                onClick = { onRequestShowOpenSourceLibraries() }
            )
            Spacer(Modifier.height(8.dp))
            AboutItem(
                icon = MyIcons.language,
                title = Res.string.localized_by_translators.asStringSource(),
                description = Res.string.meet_the_translators.asStringSource(),
                onClick = { onRequestShowTranslators() }
            )

            Spacer(Modifier.height(32.dp))

            // Website link
            val uriHandler = LocalUriHandler.current
            val websiteUrl = SharedConstants.projectWebsite
            val websiteDisplayName = remember(websiteUrl) {
                HttpUrlUtils.getHost(websiteUrl) ?: websiteUrl
            }
            WithContentAlpha(0.5f) {
                Text(
                    text = websiteDisplayName,
                    color = myColors.info,
                    modifier = Modifier.clickable { uriHandler.openUri(websiteUrl) }
                )
            }
            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
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
            .border(1.dp, myColors.onBackground / 0.15f, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(myColors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
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
