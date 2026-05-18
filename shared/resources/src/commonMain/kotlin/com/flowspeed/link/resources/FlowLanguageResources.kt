package com.flowspeed.link.resources

import com.flowspeed.lib.resources.contracts.MyLanguageResource
import okio.FileSystem
import okio.Path.Companion.toPath

object FlowLanguageResources {
    private const val LOCALES_DIRECTORY = "com/flowspeed/link/resources/locales/"
    val defaultLanguageResource = run {
        val defaultName = "en_US"
        MyLanguageResource.BundledLanguageResource(
            language = defaultName,
            getData = suspend { ResourceUtil.readResourceAsByteArray("$LOCALES_DIRECTORY$defaultName.properties") }
        )
    }
    val languages: List<MyLanguageResource>
        get() = ResourceMap
            .files
            .filter { it.startsWith(LOCALES_DIRECTORY) }
            .map {
                MyLanguageResource.BundledLanguageResource(
                    language = it.split("/").last().split(".").first(),
                    getData = suspend { ResourceUtil.readResourceAsByteArray(it) }
                )
            }

}

internal object ResourceUtil {
    fun readResourceAsByteArray(path: String): ByteArray {
        return FileSystem.RESOURCES.read(path.toPath()) {
            readByteArray()
        }
    }

    fun readResourceAsString(path: String): String {
        return FileSystem.RESOURCES.read(path.toPath()) {
            readUtf8()
        }
    }
}



object FlowResources {
    fun getTranslatorsContent(): String {
        return ResourceUtil
            .readResourceAsString("com/flowspeed/link/resources/credits/translators.json")
    }
}
