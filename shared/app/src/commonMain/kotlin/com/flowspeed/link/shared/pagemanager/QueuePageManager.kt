package com.flowspeed.link.shared.pagemanager

interface QueuePageManager : QueueItemPageManager, NewQueuePageManager
interface QueueItemPageManager {
    fun openQueues(openQueueId: Long? = null)
    fun closeQueues()
}

interface NewQueuePageManager {
    fun openNewQueueDialog()
    fun closeNewQueueDialog()
}
