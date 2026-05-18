package com.flowspeed.link.updateapplier

import com.flowspeed.link.updatechecker.UpdateInfo

interface UpdateApplier {
    fun updateSupported(): Boolean
    suspend fun applyUpdate(updateInfo: UpdateInfo)
    suspend fun cleanup()
}