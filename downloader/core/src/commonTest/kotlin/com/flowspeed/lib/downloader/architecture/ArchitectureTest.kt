package com.flowspeed.lib.downloader.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureTest {

    @Test
    fun `downloader core must not depend on compose or UI packages`() {
        val commonMainDir = File("src/commonMain/kotlin")
        assertTrue(commonMainDir.exists(), "Directory commonMain/kotlin must exist")

        val disallowedImports = listOf(
            "androidx.compose",
            "com.flowspeed.link.shared.ui"
        )

        val violations = mutableListOf<String>()

        commonMainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (line.trim().startsWith("import ")) {
                            val importPath = line.substringAfter("import ").trim()
                            disallowedImports.forEach { disallowed ->
                                if (importPath.startsWith(disallowed)) {
                                    violations.add("${file.path}:${index + 1} - imports $importPath")
                                }
                            }
                        }
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Architecture violation: downloader:core depends on UI. Violations found:\n" +
                    violations.joinToString("\n")
        )
    }
}
