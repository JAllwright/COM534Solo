import main.kotlin.BookingsTable
import main.kotlin.ReservationSystem
import main.kotlin.RoomsTable
import main.kotlin.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationSystemTest {

    private val reservationSystem = ReservationSystem()

    @BeforeAll
    fun setup() {
        // use an in memory database for testing
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, RoomsTable, BookingsTable)
        }
    }

    @AfterEach
    fun cleanup() {
        transaction {
            BookingsTable.deleteAll()
            RoomsTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    @Test
    fun `addRoom with blank building code returns error`() = transaction {
        val result = reservationSystem.addRoom("", 101, "Windows", 10)
        assertEquals("Please fill out all fields.", result)
    }

    @Test
    fun `addRoom with invalid number of computers returns error`() = transaction {
        val result = reservationSystem.addRoom("JM", 101, "Windows", 7)
        assertEquals("Number of computers must be a multiple of 5.", result)
    }


    @Test
    fun `addRoom successfully adds a room with valid inputs`() = transaction {
        val result = reservationSystem.addRoom("JM", 101, "Windows", 10)
        assertEquals("Room JM-101 with 10 computers successfully added.", result)

        val rooms = RoomsTable.selectAll().toList()
        assertEquals(1, rooms.size)
    }

    @Test
    fun `addRoom fails if room already exists`() = transaction {
        reservationSystem.addRoom("AB", 101, "Windows 10", 10)

        val exception = assertThrows<IllegalArgumentException> {
            reservationSystem.addRoom("AB", 101, "Windows 10", 10)
        }
        assertEquals("Room AB-101 already exists.", exception.message)
    }
@Test
fun `addRoom with invalid input throws exception`() = transaction {
    val result = reservationSystem.addRoom("", -1, "", 0)
    assertEquals("Please fill out all fields.", result)
}

@Test
    fun `addRoom with invalid number of computers returns error message`() = transaction {
        val result = reservationSystem.addRoom("AB", 102, "Linux", 7)
        assertEquals("Number of computers must be a multiple of 5.", result)
    }


@Test
    fun `bookComputer successfully books an available computer`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        val result = reservationSystem.bookComputer("john_doe", "JM", 101, "Monday", "9-11am")
        assertTrue(result.startsWith("Booking confirmed for computer"))

        val bookings = BookingsTable.select { BookingsTable.userName eq "john_doe" }.toList()
        assertEquals(1, bookings.size)
        assertEquals("Monday", bookings[0][BookingsTable.day])
    }


    @Test
    fun `bookComputer fails when no computers are available`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)

        repeat(5) { index ->
            reservationSystem.bookComputer("user_$index", "JM", 101, "Monday", "9-11am")
        }

        val result = reservationSystem.bookComputer("new_user", "JM", 101, "Monday", "9-11am")
        assertEquals("No available computers for the selected timeslot in room JM-101.", result)
    }

    @Test
    fun `bookComputer with invalid day returns error`() = transaction {
        val result = reservationSystem.bookComputer("user1", "JM", 101, "InvalidDay", "9-11am")
        assertEquals("Invalid day. Please enter a valid day of the week (e.g., Monday).", result)
    }


    @Test
    fun `bookComputer for non-existent room returns error`() = transaction {
        val result = reservationSystem.bookComputer("user1", "NonExistent", 999, "Monday", "9-11am")
        assertEquals("Room not found: NonExistent-999", result)
    }

    @Test
    fun `getBookingsByRoomAndDay retrieves bookings correctly`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        reservationSystem.bookComputer("user1", "JM", 101, "Monday", "9-11am")
        val bookings = reservationSystem.getBookingsByRoomAndDay("JM", 101, "Monday")
        assertEquals(1, bookings.size)
        assertEquals("user1", bookings[0].userName)
    }

    @Test
    fun `getBookingsByRoomAndDay for non-existent room throws exception`() = transaction {
        val exception = assertThrows<NoSuchElementException> {
            reservationSystem.getBookingsByRoomAndDay("Invalid", 404, "Monday")
        }
        assertEquals("Room Invalid-404 not found.", exception.message)
    }

    @Test
    fun `getBookingsByRoomAndDay for invalid day throws exception`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        val exception = assertThrows<IllegalArgumentException> {
            reservationSystem.getBookingsByRoomAndDay("JM", 101, "Freeday")
        }
        assertEquals("Invalid day. Please enter a valid day of the week (e.g., Monday).", exception.message)
    }


    @Test
fun `getUserBookings returns empty list if user has no bookings`() = transaction {
    reservationSystem.addRoom("JM", 101, "Windows", 5)
    val bookings = reservationSystem.getUserBookings("unknown_user")
    assertEquals(0, bookings.size)
}

    @Test
    fun `getUserBookings retrieves all bookings for user`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 10)
        reservationSystem.bookComputer("user1", "JM", 101, "Monday", "9-11am")
        reservationSystem.bookComputer("user1", "JM", 101, "Tuesday", "11am-1pm")
        val bookings = reservationSystem.getUserBookings("user1")
        assertEquals(2, bookings.size)
    }


    @Test
    fun `cancelBooking successfully cancels a booking`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        reservationSystem.bookComputer("john_doe", "JM", 101, "Monday", "9-11am")

        val bookingId = BookingsTable.select { BookingsTable.userName eq "john_doe" }
            .single()[BookingsTable.id]

        val cancelResult = reservationSystem.cancelBooking(bookingId, "john_doe")
        assertEquals("Booking canceled successfully.", cancelResult)

        val remainingBookings = BookingsTable.select { BookingsTable.id eq bookingId and (BookingsTable.userName.isNull()) }
        assertTrue(remainingBookings.count() > 0, "Booking was not properly canceled.")
    }


    @Test
    fun `cancelBooking fails if booking does not belong to user`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        reservationSystem.bookComputer("john_doe", "JM", 101, "Monday", "9-11am")

        val bookingId = BookingsTable.select { BookingsTable.userName eq "john_doe" }
            .single()[BookingsTable.id]

        val cancelResult = reservationSystem.cancelBooking(bookingId, "jane_doe")
        assertEquals("No matching booking found for the current user.", cancelResult)
    }

@Test
fun `cancelBooking fails for nonexistent booking ID`() = transaction {
    val result = reservationSystem.cancelBooking(99999, "admin")
    assertEquals("No matching booking found for the current user.", result)
}

    @Test
    fun `bookSpecificComputer for already booked computer returns error`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        reservationSystem.bookSpecificComputer("user1", "JM-101-1", "JM", 101, "Monday", "9-11am")
        val result = reservationSystem.bookSpecificComputer("user2", "JM-101-1", "JM", 101, "Monday", "9-11am")
        assertEquals("This computer is already booked.", result)
    }


    @Test
    fun `bookSpecificComputer books the specified computer`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        val result = reservationSystem.bookSpecificComputer("user1", "JM-101-1", "JM", 101, "Monday", "9-11am")
        assertEquals("Computer JM-101-1 successfully booked.", result)
    }


    @Test
fun `bookSpecificComputer fails when computer is already booked`() = transaction {
    reservationSystem.addRoom("JM", 101, "Windows", 5)
    reservationSystem.bookSpecificComputer(
        userName = "john_doe",
        computerId = "JM-101-3",
        buildingCode = "JM",
        roomNumber = 101,
        day = "Monday",
        timeslot = "9-11am"
    )
    val result = reservationSystem.bookSpecificComputer(
        userName = "jane_doe",
        computerId = "JM-101-3",
        buildingCode = "JM",
        roomNumber = 101,
        day = "Monday",
        timeslot = "9-11am"
    )
    assertEquals("This computer is already booked.", result)
}

@Test
    fun `bookSpecificComputer books a specific computer when available`() = transaction {
        SchemaUtils.drop(BookingsTable, RoomsTable)
        SchemaUtils.create(BookingsTable, RoomsTable)

        reservationSystem.addRoom("JM", 101, "Windows", 5)

        val result = reservationSystem.bookSpecificComputer(
            userName = "john_doe",
            computerId = "JM-101-3",
            buildingCode = "JM",
            roomNumber = 101,
            day = "Monday",
            timeslot = "9-11am"
        )

        assertEquals("Computer JM-101-3 successfully booked.", result)

        val bookingsCount = BookingsTable.select {
            (BookingsTable.computerId eq "JM-101-3") and
                    (BookingsTable.userName eq "john_doe")
        }.count()

        assertEquals(1, bookingsCount, "Expected only one booking for the specific computer.")
    }

    @Test
    fun `getComputersInRoom retrieves computers correctly`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        val computers = reservationSystem.getComputersInRoom("JM", 101, "Monday", "user1")
        assertEquals(20, computers.size) //5*20 = 4; 4 rows, one for each timeslot
    }


    @Test
    fun `getUserBookings retrieves all bookings for a user`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 5)
        reservationSystem.bookComputer("john_doe", "JM", 101, "Monday", "9-11am")
        reservationSystem.bookComputer("john_doe", "JM", 101, "Monday", "11am-1pm")

        val bookings = reservationSystem.getUserBookings("john_doe")
        assertEquals(2, bookings.size)
        assertTrue(bookings.all { it.userName == "john_doe" })
    }

    @Test
    fun `searchRooms with filters retrieves correct rooms`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 10)
        reservationSystem.addRoom("JM", 102, "Linux", 5)
        val windowsRooms = reservationSystem.searchRooms(null, "Windows")
        assertEquals(1, windowsRooms.size)
        assertEquals("Windows", windowsRooms[0].operatingSystem)
    }

    @Test
    fun `searchRooms without filters retrieves all rooms`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 10)
        reservationSystem.addRoom("JM", 102, "Linux", 5)
        val allRooms = reservationSystem.searchRooms(null, null)
        assertEquals(2, allRooms.size)
    }

    @Test
    fun `updateRoomOperatingSystem updates OS correctly`() = transaction {
        reservationSystem.addRoom("JM", 101, "Windows", 10)
        val result = reservationSystem.updateRoomOperatingSystem("JM", 101, "Linux")
        assertEquals("Room JM-101 updated to Linux.", result)
    }

    @Test
    fun `updateRoomOperatingSystem with invalid OS returns error`() = transaction {
        val result = reservationSystem.updateRoomOperatingSystem("JM", 101, "InvalidOS")
        assertEquals("Invalid operating system. Valid options are: Windows, Mac, Linux", result)
    }

    @Test
    fun `updateRoomOperatingSystem for non-existent room returns error`() = transaction {
        val result = reservationSystem.updateRoomOperatingSystem("Invalid", 404, "Windows")
        assertEquals("Room Invalid-404 not found.", result)
    }


}
