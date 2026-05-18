package com.flowspeed.link.shared.util

fun String.takeOrAppendDots(takeCount: Int): String {
    val take = take(takeCount)
    if (length<=takeCount){
        return take
    }else{
        return "$take…"
    }
}
