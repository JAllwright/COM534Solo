package main.kotlin

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.dao.id.IntIdTable

// Table definitions
object RoomsTable : IntIdTable("Rooms") {
    val buildingCode = varchar("buildingCode", 5)
    val roomNumber = integer("roomNumber")
    val operatingSystem = varchar("operatingSystem", 20)
}

object BookingsTable : Table("Bookings") {
    val id = integer("id").autoIncrement()
    val userName = varchar("userName", 50).nullable()
    val roomId = reference("roomId", RoomsTable) // make sure that "roomId" exists in RoomsTable
    val computerId = varchar("computerId", 50)
    val day = varchar("day", 20)
    val timeslot = varchar("timeslot", 20)

    override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table("Users") {
    val id = integer("id").autoIncrement()
    val userName = varchar("userName", 50).uniqueIndex()
    val email = varchar("email", 50)
    val password = varchar("password", 50)
    val accountType = varchar("accountType", 20)

    override val primaryKey = PrimaryKey(id)
}

object DatabaseHelper {
    fun init() {
        Database.connect("jdbc:sqlite:university.db", driver = "org.sqlite.JDBC")
        transaction {
            // create database if it doesn't exist
            SchemaUtils.createMissingTablesAndColumns(RoomsTable, BookingsTable, UsersTable)

            // create admin account
            if (UsersTable.select { UsersTable.userName eq "admin" }.empty()) {
                UsersTable.insert {
                    it[userName] = "admin" // Simplified
                    it[email] = "admin@university.com"
                    it[password] = "admin123" // Use hashed password in production
                    it[accountType] = "admin"
                }
            }
        }
    }
}
