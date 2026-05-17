package com.flowspeed.link.integration

import kotlinx.serialization.Serializable

@Serializable
data class ApiQueueModel(
        val id: Long,
        val name: String,
)
