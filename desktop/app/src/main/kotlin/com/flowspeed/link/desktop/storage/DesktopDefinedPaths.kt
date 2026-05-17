package com.flowspeed.link.desktop.storage

import com.flowspeed.link.shared.util.DefinedPaths
import okio.Path
import java.io.File

class DesktopDefinedPaths(
    dataDir: Path
) : DefinedPaths(
    dataDir
) {
    val pageStatesStorageFile: Path = configDir.resolve("pageStatesStorage.json")
    val renderApiFile: Path = optionsDir.resolve("renderApi.txt")
}
