package main.kotlin

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ReservationSystem {
    fun bookComputer(userName: String, buildingCode: String, roomNumber: Int, day: String, timeslot: String): String {
        val validDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        val normalizedDay = validDays.find { it.equals(day, ignoreCase = true) }
            ?: return "Invalid day. Please enter a valid day of the week (e.g., Monday)."

        return transaction {
            val roomId = RoomsTable
                .select { (RoomsTable.buildingCode eq buildingCode) and (RoomsTable.roomNumber eq roomNumber) }
                .singleOrNull()?.get(RoomsTable.id)?.value

            if (roomId == null) {
                return@transaction "Room not found: $buildingCode-$roomNumber"
            }

            println("Room ID found: $roomId") // debug

            val availableComputer = BookingsTable
                .select {
                    (BookingsTable.roomId eq roomId) and
                            (BookingsTable.day eq normalizedDay) and
                            (BookingsTable.timeslot eq timeslot) and
                            (BookingsTable.userName.isNull())
                }
                .firstOrNull()

            if (availableComputer != null) {

                BookingsTable.update({ BookingsTable.id eq availableComputer[BookingsTable.id] }) {
                    it[this.userName] = userName
                }

                "Booking confirmed for computer: ${availableComputer[BookingsTable.computerId]} on $normalizedDay at $timeslot."
            } else {
                "No available computers for the selected timeslot in room $buildingCode-$roomNumber."
            }
        }
    }

    fun getBookingsByRoomAndDay(buildingCode: String, roomNumber: Int, day: String): List<Booking> {
        val validDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")

        val normalizedDay = validDays.find { it.equals(day, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid day. Please enter a valid day of the week (e.g., Monday).")

        return transaction {
            val roomId = RoomsTable
                .select { (RoomsTable.buildingCode eq buildingCode) and (RoomsTable.roomNumber eq roomNumber) }
                .singleOrNull()?.get(RoomsTable.id)?.value ?: throw NoSuchElementException("Room $buildingCode-$roomNumber not found.")

            BookingsTable
                .select { (BookingsTable.roomId eq roomId) and (BookingsTable.day eq normalizedDay) and (BookingsTable.userName.isNotNull()) }
                .map {
                    Booking(
                        id = it[BookingsTable.id],
                        userName = it[BookingsTable.userName] ?: "Unassigned",
                        room = Room(
                            id = roomId,
                            buildingCode = buildingCode,
                            roomNumber = roomNumber,
                            operatingSystem = "", // os not relevant
                            computers = emptyList() // computers not fetched here
                        ),
                        computer = Computer(it[BookingsTable.computerId]),
                        day = it[BookingsTable.day],
                        timeslot = it[BookingsTable.timeslot]
                    )
                }
        }
    }


    fun addRoom(buildingCode: String, roomNumber: Int, os: String, numberOfComputers: Int): String {
        // Validate inputs
        if (buildingCode.isBlank() || roomNumber <= 0 || os.isBlank() || numberOfComputers <= 0) {
            return "Please fill out all fields."
        }

        if (numberOfComputers % 5 != 0) {
            return "Number of computers must be a multiple of 5."
        }

        return transaction {
            val existingRoom = RoomsTable
                .select { (RoomsTable.buildingCode eq buildingCode) and (RoomsTable.roomNumber eq roomNumber) }
                .singleOrNull()

            if (existingRoom != null) {
                throw IllegalArgumentException("Room $buildingCode-$roomNumber already exists.")
            }

            try {
                val roomId = RoomsTable.insertAndGetId {
                    it[this.buildingCode] = buildingCode
                    it[this.roomNumber] = roomNumber
                    it[this.operatingSystem] = os
                }

                val timeslots = listOf("9-11am", "11am-1pm", "1pm-3pm", "3pm-5pm")
                val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")

                for (computerIndex in 1..numberOfComputers) {
                    val computerId = "${buildingCode}-${roomNumber}-$computerIndex"
                    for (day in daysOfWeek) {
                        for (timeslot in timeslots) {
                            BookingsTable.insert {
                                it[this.roomId] = roomId
                                it[this.computerId] = computerId
                                it[this.userName] = null // available
                                it[this.day] = day
                                it[this.timeslot] = timeslot
                            }
                        }
                    }
                }

                "Room $buildingCode-$roomNumber with $numberOfComputers computers successfully added."
            } catch (e: Exception) {
                "Failed to add room: ${e.message}"
            }
        }
    }

    fun searchRooms(buildingCode: String?, operatingSystem: String?): List<Room> {
        return transaction {
            RoomsTable
                .select {
                    (if (buildingCode.isNullOrEmpty()) Op.TRUE else RoomsTable.buildingCode eq buildingCode) and
                            (if (operatingSystem.isNullOrEmpty()) Op.TRUE else RoomsTable.operatingSystem eq operatingSystem)
                }
                .map {
                    Room(
                        id = it[RoomsTable.id].value,
                        buildingCode = it[RoomsTable.buildingCode],
                        roomNumber = it[RoomsTable.roomNumber],
                        operatingSystem = it[RoomsTable.operatingSystem],
                        computers = emptyList()
                    )
                }
        }
    }


    fun updateRoomOperatingSystem(buildingCode: String, roomNumber: Int, newOperatingSystem: String): String {
        val validOperatingSystems = listOf("Windows", "Mac", "Linux")

        if (newOperatingSystem !in validOperatingSystems) {
            return "Invalid operating system. Valid options are: ${validOperatingSystems.joinToString(", ")}"
        }

        return transaction {
            val room = RoomsTable
                .select { (RoomsTable.buildingCode eq buildingCode) and (RoomsTable.roomNumber eq roomNumber) }
                .singleOrNull()

            if (room != null) {
                RoomsTable.update({ (RoomsTable.buildingCode eq buildingCode) and (RoomsTable.roomNumber eq roomNumber) }) {
                    it[operatingSystem] = newOperatingSystem
                }
                "Room $buildingCode-$roomNumber updated to $newOperatingSystem."
            } else {
                "Room $buildingCode-$roomNumber not found."
            }
        }
    }

    fun getUserBookings(userName: String): List<Booking> {
        return transaction {
            (BookingsTable innerJoin RoomsTable)
                .select { BookingsTable.userName eq userName }
                .map {
                    Booking(
                        id = it[BookingsTable.id],
                        userName = it[BookingsTable.userName] ?: "Unknown",
                        room = Room(
                            id = it[RoomsTable.id].value,
                            buildingCode = it[RoomsTable.buildingCode],
                            roomNumber = it[RoomsTable.roomNumber],
                            operatingSystem = it[RoomsTable.operatingSystem],
                            computers = emptyList()
                        ),
                        computer = Computer(it[BookingsTable.computerId]),
                        day = it[BookingsTable.day],
                        timeslot = it[BookingsTable.timeslot]
                    )
                }
        }
    }

    fun cancelBooking(bookingId: Int, userName: String): String {
        return transaction {
            val affectedRows = BookingsTable.update({ (BookingsTable.id eq bookingId) and (BookingsTable.userName eq userName) }) {
                it[this.userName] = null
            }
            if (affectedRows > 0) {
                "Booking canceled successfully."
            } else {
                "No matching booking found for the current user."
            }
        }
    }


    fun getComputersInRoom(buildingCode: String, roomNumber: Int, day: String, userName: String): List<Computer> {
        return transaction {
            val roomId = RoomsTable
                .select { (RoomsTable.buildingCode eq buildingCode) and (RoomsTable.roomNumber eq roomNumber) }
                .singleOrNull()?.get(RoomsTable.id)?.value ?: throw Exception("Room not found.")

            BookingsTable.select { (BookingsTable.roomId eq roomId) and (BookingsTable.day eq day) }
                .map {
                    Computer(
                        id = it[BookingsTable.computerId],
                        isBooked = it[BookingsTable.userName] != null,
                        isBookedByCurrentUser = it[BookingsTable.userName] == userName,
                        bookingId = it[BookingsTable.id],
                        timeslot = it[BookingsTable.timeslot],
                        bookedBy = it[BookingsTable.userName]
                    )
                }
        }
    }
    fun bookSpecificComputer(userName: String, computerId: String, buildingCode: String, roomNumber: Int, day: String, timeslot: String): String {
        return transaction {
            val roomId = RoomsTable
                .select { (RoomsTable.buildingCode eq buildingCode) and (RoomsTable.roomNumber eq roomNumber) }
                .singleOrNull()?.get(RoomsTable.id)?.value ?: throw Exception("Room not found.")

            val targetRecord = BookingsTable
                .select {
                    (BookingsTable.roomId eq roomId) and
                            (BookingsTable.computerId eq computerId) and
                            (BookingsTable.day eq day) and
                            (BookingsTable.timeslot eq timeslot) and
                            (BookingsTable.userName.isNull())
                }
                .singleOrNull()

            if (targetRecord != null) {
                BookingsTable.update({ BookingsTable.id eq targetRecord[BookingsTable.id] }) {
                    it[this.userName] = userName
                }
                "Computer $computerId successfully booked."
            } else {
                "This computer is already booked."
            }
        }
    }
}



