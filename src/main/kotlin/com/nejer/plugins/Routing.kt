package com.nejer.plugins

import com.nejer.database.tables.Files
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

fun Application.configureRouting() {

    routing {
        route("api") {
            route("v1") {
                get {
                    val uniqueName = call.request.queryParameters["uniqueName"]!!

                    val path = transaction {
                        Files.select { Files.name eq uniqueName }.firstOrNull()
                    }

                    if (path == null) {
                        call.respondText("File not found", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    call.respondText(path[Files.path])
                }

                post {
                    val multipartData = call.receiveMultipart()

                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val uniqueName = part.headers["uniqueName"]!! /// TODO: 15.11.2022 избавиться от !!
                                val path = part.headers["path"]!! // TODO: 15.11.2022 избавиться от !!

                                transaction {
                                    val query = Files.select { Files.name eq uniqueName }

                                    if (query.empty()) {
                                        Files.insert {
                                            it[name] = uniqueName
                                            it[this.path] = path
                                        }
                                    }
                                }

                                part.streamProvider().use { its ->
                                    File("backups/$uniqueName").outputStream().buffered().use {
                                        its.copyTo(it)
                                    }
                                }
                            }
                            else -> {}
                        }
                        part.dispose()
                    }

                    call.respond(HttpStatusCode.OK)
                }

                delete {
                    val uniqueName = call.request.queryParameters["uniqueName"]!!

                    File("backups/$uniqueName").delete()

                    transaction {
                        Files.deleteWhere { name eq uniqueName }
                    }

                    call.respond(HttpStatusCode.OK)
                }

                patch {
                    val oldName = call.request.queryParameters["oldName"]!!
                    val newName = call.request.queryParameters["newName"]!!

                    val fileWithNewName = transaction {
                        Files.select { Files.name eq newName }.firstOrNull()
                    }

                    if (fileWithNewName != null) {
                        call.respond(HttpStatusCode.Conflict)
                        return@patch
                    }

                    val fileWithOldName = transaction {
                        Files.select { Files.name eq oldName }.firstOrNull()
                    }

                    if (fileWithOldName == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@patch
                    }

                    transaction {
                        Files.update({ Files.name eq oldName }) {
                            it[name] = newName
                        }
                    }

                    File("backups/${oldName}").renameTo(File("backups/$newName"))

                    call.respond(HttpStatusCode.OK)
                }

                get("list") {
                    val uniqueNames = call.receive<List<String>>()

                    val paths = transaction {
                        Files.select { Files.name inList uniqueNames }.map {
                            it[Files.name] to it[Files.path]
                        }
                    }

                    call.respond(paths)
                }

                get("all") {
                    val files = transaction {
                        Files.selectAll().map {
                            it[Files.path] to it[Files.name]
                        }
                    }

                    call.respond(files)
                }

                get("restore") {
                    val uniqueName = call.request.queryParameters["uniqueName"]!!
                    val path = transaction {
                        Files.select { Files.name eq uniqueName }
                            .first()[Files.path] // TODO: 16.11.2022 Нет проверки на отсутствие элементов
                    }
                    call.response.header("path", path)
                    call.respondFile(File("backups/$uniqueName"))
                }

                get("paths") {
                    val paths = transaction {
                        Files.selectAll().map {
                            it[Files.path]
                        }
                    }

                    call.respond(paths)
                }
            }
        }
    }
}
