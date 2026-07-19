package com.flowspeed.lib.downloader.db

import com.flowspeed.lib.downloader.downloaditem.IDownloadItem
import com.flowspeed.lib.downloader.utils.SuspendLockList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class DownloadListFileStorage(
    private val downloadListFolder: File,
    private val fileSaver: TransactionalFileSaver,
) : IDownloadListDb {

    private val fileLocks = SuspendLockList<Long>()

    private var isCached = false
    private val cache = mutableMapOf<Long, IDownloadItem>()
    private val cacheLock = Mutex()
    private val lastWriteTimes = mutableMapOf<Long, Long>()
    private val throttleInterval = 2000L

    fun getDownloadItemFile(id: Long): File {
        return downloadListFolder.resolve("$id.json")
    }

    override suspend fun getAll(): List<IDownloadItem> {
        return cacheLock.withLock {
            if (!isCached) {
                withContext(Dispatchers.IO) {
                    val jsonExtension = ".json"
                    downloadListFolder.listFiles()
                        ?.mapNotNull { file ->
                            file.name
                                .takeIf { it.endsWith(jsonExtension) }
                                ?.removeSuffix(jsonExtension)
                                ?.toLongOrNull()
                                ?.let { id ->
                                    fileLocks.withLock(id) { fileSaver.readObject<IDownloadItem>(file) }?.also { 
                                        cache[id] = it 
                                    }
                                }
                        }
                }
                isCached = true
            }
            cache.values.toList()
        }
    }

    private suspend fun get(file: File, id: Long): IDownloadItem? {
        return fileLocks.withLock(id) {
            fileSaver.readObject(file)
        }
    }

    override suspend fun getById(id: Long): IDownloadItem? {
        cacheLock.withLock {
            if (isCached) return cache[id]
        }
        return withContext(Dispatchers.IO) {
            val item = get(getDownloadItemFile(id), id)
            if (item != null) {
                cacheLock.withLock { cache[id] = item }
            }
            item
        }
    }

    private val addLock = Mutex()
    override suspend fun add(item: IDownloadItem) {
        withContext(Dispatchers.IO) {
            addLock.withLock {
                fileLocks.withLock(item.id) {
                    fileSaver.writeObject(getDownloadItemFile(item.id), item)
                    val lastId = getLastId()
                    if (lastId < item.id) {
                        setLastId(item.id)
                    }
                }
            }
            cacheLock.withLock {
                cache[item.id] = item
                lastWriteTimes[item.id] = System.currentTimeMillis()
            }
        }
    }

    override suspend fun update(item: IDownloadItem) {
        val now = System.currentTimeMillis()
        val forceFlush = item.status != com.flowspeed.lib.downloader.downloaditem.DownloadStatus.Downloading

        withContext(Dispatchers.IO) {
            cacheLock.withLock {
                cache[item.id] = item
            }
            val lastWrite = cacheLock.withLock { lastWriteTimes[item.id] ?: 0L }
            if (forceFlush || now - lastWrite >= throttleInterval) {
                fileLocks.withLock(item.id) {
                    fileSaver.writeObject(getDownloadItemFile(item.id), item)
                }
                cacheLock.withLock {
                    lastWriteTimes[item.id] = now
                }
            }
        }
    }

    override suspend fun removeById(itemId: Long) {
        withContext(Dispatchers.IO) {
            fileLocks.withLock(itemId) {
                val file = getDownloadItemFile(itemId)
                if (file.exists()) file.delete()
            }
            cacheLock.withLock {
                cache.remove(itemId)
                lastWriteTimes.remove(itemId)
            }
        }
    }

    override suspend fun remove(item: IDownloadItem) {
        removeById(item.id)
    }


    private val lastIdFile = downloadListFolder.resolve("last_id.txt")
    private fun setLastId(id: Long) {
        fileSaver.writeObject(lastIdFile, id)
    }

    override suspend fun getLastId(): Long {
        return withContext(Dispatchers.IO) {
            var lastId = fileSaver.readObject<Long>(lastIdFile)
            if (lastId == null) {
                lastId = getLastIdFromFiles()
                setLastId(lastId)
            }
            lastId
        }
    }

    private fun getLastIdFromFiles(): Long {
        return downloadListFolder.listFiles()!!.filter {
            it.name.endsWith(".json") && it.isFile
        }.maxOfOrNull {
            it.name
                .substring(0, it.name.length - ".json".length)
                .toLong()
        } ?: -1L
    }
}
