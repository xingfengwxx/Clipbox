package com.wangxingxing.clipbox.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

private const val SERVER_VERSION = "1.0.0"

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val time: Long,
)

fun Route.healthRoutes() {
    get("/healthz") {
        call.respond(
            HealthResponse(
                status = "ok",
                version = SERVER_VERSION,
                time = System.currentTimeMillis(),
            ),
        )
    }
}
