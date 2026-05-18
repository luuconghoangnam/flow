package com.flowspeed.link.desktop.pages.about

import com.flowspeed.link.shared.util.ui.theme.myTextSizes
import com.flowspeed.link.shared.util.ui.WithContentAlpha
import com.flowspeed.link.shared.util.ui.myColors
import com.flowspeed.link.shared.util.div
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.flowspeed.link.shared.ui.widget.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import okio.FileSystem
import okio.Path.Companion.toPath

@Composable
internal fun ExternalLibsPage() {
    val libs = rememberLibs()
    Column(Modifier.fillMaxSize()) {
        // Header
        Box(
            Modifier
                .fillMaxWidth()
                .background(myColors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "Third-Party Libraries",
                fontSize = myTextSizes.lg,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(1.dp).fillMaxWidth().background(myColors.onBackground / 0.1f))

        // Library list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(libs.libraries) { library ->
                LibraryItem(library)
            }
        }
    }

    // Dialog removed - just show inline info
}

@Composable
private fun LibraryItem(library: Library) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, myColors.onBackground / 0.05f, RectangleShape)
            .background(myColors.surface / 0.3f, RectangleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${library.name} ${library.artifactVersion ?: ""}",
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            WithContentAlpha(0.5f) {
                Text(
                    library.artifactId,
                    fontSize = myTextSizes.xs,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        WithContentAlpha(0.6f) {
            Text(
                library.licenses.joinToString(", ") { it.name },
                fontSize = myTextSizes.sm,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun rememberLibs(): Libs {
    return remember {
        val jsonContent = FileSystem.RESOURCES.read("aboutlibraries.json".toPath()) {
            readUtf8()
        }
        Libs.Builder().withJson(jsonContent).build()
    }
}
