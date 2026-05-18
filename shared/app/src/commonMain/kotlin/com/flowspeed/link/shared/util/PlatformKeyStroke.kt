package com.flowspeed.link.shared.util

interface PlatformKeyStroke {
    val keyCode: Int

    fun getModifiers(): List<String>
    fun getKeyText(): String
}
