package com.flowspeed.link.desktop.utils
//
//import com.flowspeed.lib.debugboard.core.plugin.watcher.RemoveWatch
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.job
//
//fun RemoveWatch.inScope(scope: CoroutineScope) {
//    val removeWatch = this
//    scope.coroutineContext.job.invokeOnCompletion {
//        removeWatch()
//    }
//}