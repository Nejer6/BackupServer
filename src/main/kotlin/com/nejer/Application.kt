package com.nejer

import com.nejer.database.DatabaseFactory.init
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.nejer.plugins.*

fun main() {
    embeddedServer(Netty, port = 8082, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    init()
    configureSerialization()
    configureRouting()
}
