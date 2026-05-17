package com.flowspeed.link.shared.util


expect object ClipboardUtil {
    fun read(): String?
    fun copy(text: String)
}
