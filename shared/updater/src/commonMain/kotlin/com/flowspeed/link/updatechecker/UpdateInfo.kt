package com.flowspeed.link.updatechecker

import com.flowspeed.link.InstallableArch
import io.github.z4kn4fein.semver.Version
import com.flowspeed.lib.util.platform.Arch
import com.flowspeed.lib.util.platform.Platform

data class UpdateInfo(
    val version: Version,
    val platform: Platform,
    val arch: Arch,
    val updateSource: List<UpdateSource>,
    val changeLog: String,
)

sealed interface UpdateSource {
    data class DirectDownloadLink(
        val link: String,
        val name: String,
        val hash: String?,
        val installableArch: InstallableArch?,
    ) : UpdateSource
}
