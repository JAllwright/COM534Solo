package main.kotlin

data class Computer(
    val id: String,
    val isBooked: Boolean = false,
    val isBookedByCurrentUser: Boolean = false,
    val bookingId: Int? = null,
    val timeslot: String? = null,
    val bookedBy: String? = null
)
