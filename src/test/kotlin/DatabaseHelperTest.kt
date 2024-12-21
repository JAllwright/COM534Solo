import main.kotlin.DatabaseHelper
import main.kotlin.RoomsTable
import main.kotlin.BookingsTable
import main.kotlin.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseHelperTest {

    @BeforeAll
    fun setup() {
        // Connect to an in-memory SQLite database for testing
        Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
    }

    @AfterEach
    fun cleanup() {
        // Drop all tables after each test to ensure isolation
        transaction {
            SchemaUtils.drop(BookingsTable, RoomsTable, UsersTable)
        }
    }

    @Test
    fun `init creates tables successfully`() {
        transaction {
            // Before calling init, tables should not exist
            assertFalse(SchemaUtils.checkTablesExist(RoomsTable, BookingsTable, UsersTable))
        }

        DatabaseHelper.init()

        transaction {
            // After calling init, tables should exist
            assertTrue(SchemaUtils.checkTablesExist(RoomsTable, BookingsTable, UsersTable))
        }
    }

    @Test
    fun `init creates admin account successfully`() {
        DatabaseHelper.init()

        transaction {
            // Verify admin account exists in the UsersTable
            val adminUser = UsersTable.select { UsersTable.userName eq "admin" }.singleOrNull()
            assertNotNull(adminUser, "Admin user was not created.")
            assertEquals("admin", adminUser!![UsersTable.userName])
            assertEquals("admin@university.com", adminUser[UsersTable.email])
            assertEquals("admin123", adminUser[UsersTable.password])
            assertEquals("admin", adminUser[UsersTable.accountType])
        }
    }

    @Test
    fun `init does not recreate admin account if it already exists`() {
        DatabaseHelper.init()

        transaction {
            // Insert a custom admin user
            UsersTable.insert {
                it[userName] = "admin2"
                it[email] = "custom_admin@university.com"
                it[password] = "custom_pass"
                it[accountType] = "superadmin"
            }
        }

        DatabaseHelper.init()

        transaction {
            // Verify the existing admin account was not overwritten
            val adminUser = UsersTable.select { UsersTable.userName eq "admin" }.singleOrNull()
            assertNotNull(adminUser)
            assertEquals("admin@university.com", adminUser!![UsersTable.email])
            assertEquals("admin123", adminUser[UsersTable.password])
            assertEquals("admin", adminUser[UsersTable.accountType])
        }
    }

    @Test
    fun `init creates tables and admin in a fresh database`() {
        DatabaseHelper.init()

        transaction {
            // Verify tables exist
            assertTrue(SchemaUtils.checkTablesExist(RoomsTable, BookingsTable, UsersTable))

            // Verify admin account exists
            val adminUser = UsersTable.select { UsersTable.userName eq "admin" }.singleOrNull()
            assertNotNull(adminUser)
        }
    }
}

private fun SchemaUtils.checkTablesExist(vararg tables: Table): Boolean {
    return try {
        for (table in tables) {
            table.columns.firstOrNull()?.let { table.selectAll().limit(1).firstOrNull() }
        }
        true
    } catch (e: Exception) {
        false
    }
}
