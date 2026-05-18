package com.flowspeed.lib.downloader.exception

import java.io.IOException

class UnSuccessfulResponseException(val code:Int,msg:String):IOException(
    "$code | $msg"
)