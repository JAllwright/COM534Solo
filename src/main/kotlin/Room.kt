package main.kotlin

data class Room(
    val id: Int,
    var buildingCode: String,
    var roomNumber: Int,
    var operatingSystem: String,
    var computers: List<Computer>
)
