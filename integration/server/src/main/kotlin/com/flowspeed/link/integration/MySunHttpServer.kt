package com.flowspeed.link.integration

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MySunHttpServer(
    val port: Int,
    val handlerMap: HandlerMap,
    val isDebugMode: Boolean,
) : MyServer {
    private var server: HttpServer? = null

    private fun createServer(): HttpServer {
        val httpServer = HttpServer.create(
            InetSocketAddress("localhost", port),
            10,
        )
        httpServer.createContext(
            /* path = */ "/",
            /* handler = */ HttpHandlerImpl(
                handlerMap = handlerMap,
                isDebugMode = isDebugMode
            )
        )
        // Use a cached thread pool with limited max threads and short keep-alive
        // to minimize idle memory usage while handling bursts
        httpServer.executor = java.util.concurrent.ThreadPoolExecutor(
            0, 4,
            30L, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.SynchronousQueue(),
            Executors.defaultThreadFactory(),
        )
        return httpServer
    }

    override fun stopMyServer() {
        server?.run {
            (executor as ExecutorService).shutdownNow()
            stop(0)
        }
        server = null
    }

    override fun startMyServer() {
        stopMyServer()
        server = createServer().also {
            it.start()
        }
    }
}

private class HttpHandlerImpl(
    val handlerMap: HandlerMap,
    val isDebugMode: Boolean,
) : HttpHandler {
    fun createMyRequest(exchange: HttpExchange): MyRequest {
        return MyRequest(
            uri = exchange.requestURI.toString(),
            method = exchange.requestMethod,
            getBody = {
                exchange.requestBody.reader().readText()
            },
            headers = exchange.requestHeaders
                .mapValues { it.value.firstOrNull().orEmpty() },
        )
    }

    fun fillExchangeWithResponse(exchange: HttpExchange, response: MyResponse) {
        response.headers.forEach { (key, value) ->
            exchange.responseHeaders.add(key, value)
        }
        exchange.sendResponseHeaders(response.statusCode, response.getContent().length.toLong())
        exchange.responseBody.writer().use {
            it.write(response.getContent())
        }
    }

    override fun handle(exchange: HttpExchange) {
        val request = createMyRequest(exchange)
        val handler = handlerMap.findMatch(request)
        try {
            val response = handler?.invoke(request) ?: MyResponse.BadRequest(
                errorText = "Not Found",
                statusCode = 404,
            )
            fillExchangeWithResponse(exchange, response)
        } catch (e: Exception) {
            val internalServerErrorResponse = MyResponse.Text(
                if (isDebugMode) "Error ${e.localizedMessage}"
                else "Error",
                statusCode = 500,
            )
            fillExchangeWithResponse(exchange, internalServerErrorResponse)
            return
        } finally {
            exchange.close()
        }
    }
}
