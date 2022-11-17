package com.nejer.database.tables

import org.jetbrains.exposed.sql.Table

object Files : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100).uniqueIndex()
    val path = varchar("path", 300)

    override val primaryKey = PrimaryKey(id)
}