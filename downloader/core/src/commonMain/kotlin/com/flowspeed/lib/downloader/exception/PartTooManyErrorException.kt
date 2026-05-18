package com.flowspeed.lib.downloader.exception

import com.flowspeed.lib.downloader.part.DownloadPart

class PartTooManyErrorException(
    part: DownloadPart,
    override val cause: Throwable
) : Exception(
        "this part $part have too many errors",
    cause,
)
