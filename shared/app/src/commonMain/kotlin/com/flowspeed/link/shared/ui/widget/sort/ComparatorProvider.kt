package com.flowspeed.link.shared.ui.widget.sort

interface ComparatorProvider<T> {
    fun comparator(): Comparator<T>
}
