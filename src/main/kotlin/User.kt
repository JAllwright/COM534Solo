package main.kotlin

data class User(
    val id: String,
    var userName: String,
    var email: String,
    var password: String,
    val accountType: String
)
