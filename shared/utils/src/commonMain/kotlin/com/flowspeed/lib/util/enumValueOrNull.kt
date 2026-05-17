package com.flowspeed.lib.util

inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? {
    return runCatching { enumValueOf<T>(this) }.getOrNull()
}
