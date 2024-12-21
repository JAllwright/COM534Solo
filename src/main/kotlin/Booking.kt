package main.kotlin

data class Booking(
    val id: Int,
    val userName: String,
    val room: Room,
    val computer: Computer,
    val day: String,
    val timeslot: String
)
