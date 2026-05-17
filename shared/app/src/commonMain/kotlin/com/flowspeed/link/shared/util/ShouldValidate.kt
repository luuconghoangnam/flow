package com.flowspeed.link.shared.util

import kotlinx.coroutines.flow.StateFlow

interface ShouldValidate {
    val valid: StateFlow<Boolean>
}
