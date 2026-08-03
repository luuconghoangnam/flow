package com.flowspeed.lib.downloader.part

import com.flowspeed.lib.downloader.connection.HttpDownloaderClient
import com.flowspeed.lib.downloader.connection.Connection
import com.flowspeed.lib.downloader.connection.response.HttpResponseInfo
import com.flowspeed.lib.downloader.connection.response.expectSuccess
import com.flowspeed.lib.downloader.destination.DestWriter
import com.flowspeed.lib.downloader.downloaditem.http.IHttpDownloadCredentials
import com.flowspeed.lib.downloader.exception.ServerPartIsNotTheSameAsWeExpectException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okio.*


/**
 * @param strictMode
 *  `false` - this is not the purpose of its app, so we don't strict here
 *
 *  download part without checking for length validation
 *  this is where we want to only copy data arrived from server
 *  for example, a web page link that maybe get us different response length
 *  we only need to download it no matter what is inside
 *
 *  `true` - main purpose of this class
 *
 *  validate download size before trying to write to the filesystem
 */
class HttpPartDownloader(
    val credentials: IHttpDownloadCredentials,
    getDestWriter: () -> DestWriter,
    part: RangedPart,
    val client: HttpDownloaderClient,
    val speedLimiters: List<Throttler>,
    val strictMode: Boolean,
    partSplitLock: Any,
) : PartDownloader<RangedPart>(
    part = part,
    getDestWriter = getDestWriter
) {


    private suspend fun establishConnection(
        from: Long,
        to: Long?,
    ): Connection<HttpResponseInfo> {
        val connect = client.connect(credentials, from, to)
        // make sure this is a 2xx response
        kotlin.runCatching {
            connect.responseInfo.expectSuccess()
        }
            .onFailure {
                // close connection before throwing exception
                kotlin.runCatching {
                    connect.close()
                }
            }
            .getOrThrow()
        val source = speedLimiters.fold<Throttler, Source>(connect.source) { acc, throttler ->
            throttler.source(acc)
        }
        return connect.copy(
            source = source
        )
    }

    override fun onFinish() {
        synchronized(partSplitSupport) {
            if (part.isBlind) {
                part.setBlindAsCompleted()
            } else if (!part.isCompleted) {
                onCanceled(
                    CancellationException(
                        "response ended before bounded part completed: $part"
                    )
                )
                return
            }
        }
        super.onFinish()
    }

    private val partSplitSupport = PartSplitSupport(part, partSplitLock)

    //this method is invoked only in one thread for every instance
    override fun howMuchCanRead(maxAllowed: Long): Long {
        return partSplitSupport.howMuchCanRead(
            expandToBufferSize = maxAllowed,
            tryToExtendSafeZone = true
        )
    }

    fun canBeSplit(): Boolean {
        return partSplitSupport.canSplit()
    }

    override suspend fun connectAndVerify(): Connection<HttpResponseInfo> {
        //        thisLogger().info("going to copy data to destination")
        //we copy part because maybe part::to property will change during part split,
        //so we make backup of current part to validate http response
        val partCopy = part.copy()
        val conn = establishConnection(partCopy.current, partCopy.to)
//        thisLogger().info("connection established")
        if (stop || !currentCoroutineContext().isActive) {
            conn.close()
            throw CancellationException()
        }
        val contentLength = conn.contentLength.let {
            if (it == -1L) {
                //in case of no end is come from headers
                null
            } else {
                it
            }
        }
        val responseRange = conn.responseInfo.contentRange?.range
        val isRangedRequest = partCopy.to != null
        val hasValidResponseStart = responseRange?.first == partCopy.current
        val hasUnexpectedFullResponse = isRangedRequest && !conn.responseInfo.isPartial
        val hasMissingResponseRange = isRangedRequest && responseRange == null
        val hasUnexpectedResponseStart = isRangedRequest && responseRange != null && !hasValidResponseStart
        val hasUnexpectedLength = contentLength != null && partCopy.remainingLength != null &&
                contentLength != partCopy.remainingLength
        val mustReject = hasUnexpectedFullResponse || hasMissingResponseRange ||
                hasUnexpectedResponseStart ||
                (hasUnexpectedLength && (!strictMode || !hasValidResponseStart))
        if (mustReject) {
            val exception = ServerPartIsNotTheSameAsWeExpectException(
                start = partCopy.current,
                end = partCopy.to,
                expectedLength = partCopy.remainingLength,
                actualLength = contentLength
            )
            conn.close()
            throw exception
        }
        return conn
    }


    //should be sync with part split lock
    fun splitPart(): RangedPart? {
        return partSplitSupport.splitPart()
    }

}
