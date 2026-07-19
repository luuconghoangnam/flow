package com.flowspeed.link.integration.http4k

import com.flowspeed.lib.util.http4k.NanoHttp
import com.flowspeed.link.integration.HandlerMap
import com.flowspeed.link.integration.MyRequest
import com.flowspeed.link.integration.MyResponse
import com.flowspeed.link.integration.MyServer
import org.http4k.core.*
import org.http4k.server.Http4kServer
import org.http4k.server.asServer


class MyHttp4KServer(
    val port: Int,
    val handlerMap: HandlerMap,
    val isDebugMode: Boolean,
) : MyServer {
    private fun toMyRequest(request: Request): MyRequest {
        return MyRequest(
            uri = request.uri.toString(),
            method = request.method.toString(),
            getBody = {
                if (request.body == Body.EMPTY) null
                else request.bodyString()
            },
            headers = request.headers
                .mapNotNull { (key, value) -> value?.let { key to it } }
                .toMap(),
        )
    }

    private fun toHttp4kResponse(response: MyResponse): Response {
        val status = Status.serverValues.find {
            it.code == response.statusCode
        }!!
        return Response(status)
            .headers(response.headers.map { it.key to it.value })
            .body(response.getContent())
    }

    private var server: Http4kServer? = null

    private fun createServer(): Http4kServer {
//        val logAll = Filter { next ->
//            {
//                println("req: $it")
//                next(it).also {
//                    println("res: $it")
//                }
//            }
//        }
        val appRoute = { req: Request ->
            val myRequest = toMyRequest(req)
            val handler = handlerMap.findMatch(myRequest)
            if (handler != null) {
                toHttp4kResponse(handler(myRequest))
            } else {
                Response(Status.NOT_FOUND)
                    .body("Not Found")
            }
        }
        return Filter.NoOp
//            .then(logAll)
            .then(appRoute)
            .asServer(NanoHttp("localhost",port))
    }

    override fun stopMyServer() {
        server?.stop()
        server = null
    }

    override fun startMyServer() {
        stopMyServer()
        server = createServer().also {
            it.start()
        }
    }
}