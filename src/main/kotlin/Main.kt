package main.kotlin

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


fun main() = application {
    DatabaseHelper.init()

    Window(onCloseRequest = ::exitApplication, title = "University Room Booking System") {
        MaterialTheme {
            App()
        }
    }
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf("Home") }
    var loggedInUser by remember { mutableStateOf<User?>(null) }

    var selectedBuildingCode by remember { mutableStateOf("") }
    var selectedRoomNumber by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("University Room Booking System") })
        }
    ) {
        when (currentScreen) {
            "Home" -> HomeScreen { currentScreen = it }
            "Login" -> LoginScreen(
                onLoginSuccess = { user ->
                    loggedInUser = user
                    currentScreen = "Dashboard"
                },
                onBackToHome = { currentScreen = "Home" }
            )
            "SignUp" -> SignUpScreen(
                onSignUpSuccess = { currentScreen = "Login" },
                onBackToHome = { currentScreen = "Home" }
            )
            "Dashboard" -> DashboardScreen(
                user = loggedInUser!!,
                onLogout = { currentScreen = "Home" },
                onNavigate = { screen, buildingCode, roomNumber, day ->
                    selectedBuildingCode = buildingCode
                    selectedRoomNumber = roomNumber
                    selectedDay = day

                    currentScreen = screen
                }
            )
            "BookComputer" -> BookComputerScreen(
                userName = loggedInUser!!.userName,
                onBackToDashboard = { currentScreen = "Dashboard" }
            )
            "ViewAndCancelBookings" -> ViewAndCancelBookingsScreen(
                userName = loggedInUser!!.userName,
                onBackToDashboard = { currentScreen = "Dashboard" }
            )
            "RoomGraphicalView" -> RoomGraphicalViewScreen(
                userName = loggedInUser!!.userName,
                userType = loggedInUser!!.accountType,
                buildingCode = selectedBuildingCode,
                roomNumber = selectedRoomNumber,
                day = selectedDay,
                onBackToDashboard = { currentScreen = "Dashboard" }
            )
            "AddRoom" -> AddRoomScreen { currentScreen = "Dashboard" }
            "ViewBookingsByRoom" -> ViewBookingsByRoomScreen { currentScreen = "Dashboard" }
            "ManageRooms" -> ManageRoomsScreen { currentScreen = "Dashboard" }
            "SearchRooms" -> SearchRoomsScreen { currentScreen = "Dashboard" }
            "updateRoomOperatingSystem" -> ChangeRoomOperatingSystemScreen { currentScreen = "Dashboard" }
        }
    }
}

@Composable
fun InputField(value: String, onValueChange: (String) -> Unit, label: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun NavigationButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Welcome to the Room Booking System", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))
        NavigationButton("Login") { onNavigate("Login") }
        NavigationButton("Sign Up") { onNavigate("SignUp") }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (User) -> Unit, onBackToHome: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Login", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        InputField(value = username, onValueChange = { username = it }, label = "Username")
        InputField(value = password, onValueChange = { password = it }, label = "Password")

        NavigationButton("Login") {
            val user = transaction {
                UsersTable.select { UsersTable.userName eq username }
                    .singleOrNull()
            }

            if (user != null && user[UsersTable.password] == password) {
                onLoginSuccess(
                    User(
                        id = user[UsersTable.id].toString(),
                        userName = user[UsersTable.userName],
                        email = user[UsersTable.email],
                        password = user[UsersTable.password],
                        accountType = user[UsersTable.accountType]
                    )
                )
            } else {
                error = "Invalid credentials. Try again."
            }
        }
        NavigationButton("Back to Home") { onBackToHome() }

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colors.error)
        }
    }
}

@Composable
fun SignUpScreen(onSignUpSuccess: () -> Unit, onBackToHome: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Sign Up", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        InputField(value = username, onValueChange = { username = it }, label = "Username")
        InputField(value = email, onValueChange = { email = it }, label = "Email")
        InputField(value = password, onValueChange = { password = it }, label = "Password")
        InputField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = "Confirm Password")

        NavigationButton("Sign Up") {
            if (password != confirmPassword) {
                error = "Passwords do not match."
                return@NavigationButton
            }

            val existingUser = transaction {
                UsersTable.select { UsersTable.userName eq username }.singleOrNull()
            }

            if (existingUser != null) {
                error = "Username already exists."
            } else {
                transaction {
                    UsersTable.insert {
                        it[userName] = username
                        it[UsersTable.email] = email
                        it[UsersTable.password] = password
                        it[accountType] = "user"
                    }
                }
                onSignUpSuccess()
            }
        }

        NavigationButton("Back to Home") { onBackToHome() }

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colors.error)
        }
    }
}

@Composable
fun DashboardScreen(user: User, onLogout: () -> Unit, onNavigate: (String, String, Int, String) -> Unit) {
    var buildingCode by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val validDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Welcome, ${user.userName}", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        if (user.accountType == "admin") {
            Text("Admin Options:", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))

            NavigationButton("Add Room") { onNavigate("AddRoom", "", 0, "") }
            NavigationButton("Manage Rooms") { onNavigate("ManageRooms", "", 0, "") }
            NavigationButton("View Bookings by Room and Day") { onNavigate("ViewBookingsByRoom", "", 0, "") }
            NavigationButton("Change Room Operating System") { onNavigate("updateRoomOperatingSystem", "", 0, "") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("User Options:", style = MaterialTheme.typography.h6)
        NavigationButton("Search for Rooms") { onNavigate("SearchRooms", "", 0, "") }
        NavigationButton("Book a Computer") { onNavigate("BookComputer", "", 0, "") }
        NavigationButton("View and Cancel Bookings") { onNavigate("ViewAndCancelBookings", "", 0, "") }

        Spacer(modifier = Modifier.height(16.dp))

        Text("View Room Graphically:", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = buildingCode,
            onValueChange = { buildingCode = it },
            label = { Text("Building Code (e.g., JM)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = roomNumber,
            onValueChange = { roomNumber = it },
            label = { Text("Room Number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = day,
            onValueChange = { day = it },
            label = { Text("Day (e.g., Monday)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val roomNum = roomNumber.toIntOrNull()
            val normalizedDay = validDays.find { it.equals(day, ignoreCase = true) }

            if (buildingCode.isNotBlank() && roomNum != null && normalizedDay != null) {
                onNavigate("RoomGraphicalView", buildingCode, roomNum, normalizedDay)
                errorMessage = ""
            } else {
                errorMessage = "Please provide valid Building Code, Room Number, and Day."
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("View Room Graphically")
        }

        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = MaterialTheme.colors.error)
        }

        Spacer(modifier = Modifier.height(16.dp))
        NavigationButton("Logout") { onLogout() }
    }
}



@Composable
fun ManageRoomsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Manage Rooms Screen", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun SearchRoomsScreen(onBackToDashboard: () -> Unit) {
    val reservationSystem = ReservationSystem()

    var buildingCode by remember { mutableStateOf("") }
    var operatingSystem by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Room>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Search for Rooms", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = buildingCode,
            onValueChange = { buildingCode = it },
            label = { Text("Building Code (e.g., JM)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = operatingSystem,
            onValueChange = { operatingSystem = it },
            label = { Text("Operating System (e.g., Windows)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            try {
                searchResults = reservationSystem.searchRooms(buildingCode, operatingSystem)
                errorMessage = if (searchResults.isEmpty()) {
                    "No rooms found for Building: $buildingCode, OS: $operatingSystem"
                } else {
                    ""
                }
            } catch (e: Exception) {
                searchResults = emptyList()
                errorMessage = "Error: ${e.message}"
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Search")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colors.error)
        } else {
            searchResults.forEach { room ->
                Text(
                    "Building: ${room.buildingCode}, Room: ${room.roomNumber}, OS: ${room.operatingSystem}",
                    style = MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun BookComputerScreen(userName: String, onBackToDashboard: () -> Unit) {
    val reservationSystem = ReservationSystem()

    var buildingCode by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var selectedTimeslot by remember { mutableStateOf("") }
    var confirmationMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val timeslots = listOf("9-11am", "11am-1pm", "1pm-3pm", "3pm-5pm")

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Book a Computer", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = buildingCode,
            onValueChange = { buildingCode = it },
            label = { Text("Building Code (e.g., JM)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = roomNumber,
            onValueChange = { roomNumber = it },
            label = { Text("Room Number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = day,
            onValueChange = { day = it },
            label = { Text("Day (e.g., Monday)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Select Timeslot", style = MaterialTheme.typography.body1)
        Spacer(modifier = Modifier.height(8.dp))

        Box {
            Button(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedTimeslot.ifEmpty { "Choose Timeslot" })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                timeslots.forEach { timeslot ->
                    DropdownMenuItem(onClick = {
                        selectedTimeslot = timeslot
                        expanded = false
                    }) {
                        Text(timeslot)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (selectedTimeslot.isEmpty()) {
                errorMessage = "Please select a valid timeslot. Eligible timeslots: ${timeslots.joinToString(", ")}"
                confirmationMessage = ""
            } else {
                confirmationMessage = reservationSystem.bookComputer(
                    userName = userName,
                    buildingCode = buildingCode,
                    roomNumber = roomNumber.toIntOrNull() ?: -1,
                    day = day,
                    timeslot = selectedTimeslot
                )
                errorMessage = ""
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Book Computer")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (confirmationMessage.isNotEmpty()) {
            Text(confirmationMessage, color = MaterialTheme.colors.primary)
        }
        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colors.error)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun ViewBookingsByRoomScreen(onBackToDashboard: () -> Unit) {
    val reservationSystem = ReservationSystem()

    var buildingCode by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("View Bookings by Room and Day", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = buildingCode,
            onValueChange = { buildingCode = it },
            label = { Text("Building Code (e.g., JM)") }
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = roomNumber,
            onValueChange = { roomNumber = it },
            label = { Text("Room Number") }
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = day,
            onValueChange = { day = it },
            label = { Text("Day (e.g., Monday)") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            try {
                bookings = reservationSystem.getBookingsByRoomAndDay(
                    buildingCode = buildingCode,
                    roomNumber = roomNumber.toIntOrNull() ?: -1,
                    day = day
                )
                errorMessage = ""
            } catch (e: Exception) {
                bookings = emptyList()
                errorMessage = e.message ?: "An unknown error occurred."
            }
        }) {
            Text("View Bookings")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colors.error)
        } else {
            bookings.forEach { booking ->
                Text(
                    "Computer ID: ${booking.computer.id}, Timeslot: ${booking.timeslot}, Student: ${booking.userName}",
                    style = MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToDashboard) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun AddRoomScreen(onBack: () -> Unit) {
    var buildingCode by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var operatingSystem by remember { mutableStateOf("") }
    var numComputers by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add a New Room", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = buildingCode,
            onValueChange = { buildingCode = it },
            label = { Text("Building Code") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = roomNumber,
            onValueChange = { roomNumber = it },
            label = { Text("Room Number") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = operatingSystem,
            onValueChange = { operatingSystem = it },
            label = { Text("Operating System") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = numComputers,
            onValueChange = { numComputers = it },
            label = { Text("Number of Computers") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val result = try {
                ReservationSystem().addRoom(
                    buildingCode,
                    roomNumber.toIntOrNull() ?: -1, // Pass -1 if invalid
                    operatingSystem,
                    numComputers.toIntOrNull() ?: -1 // Pass -1 if invalid
                )
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            feedback = result
        }) {
            Text("Add Room")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack) {
            Text("Back to Dashboard")
        }

        if (feedback.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(feedback, color = if (feedback.startsWith("Error")) MaterialTheme.colors.error else MaterialTheme.colors.primary)
        }
    }
}


@Composable
fun ChangeRoomOperatingSystemScreen(onBackToDashboard: () -> Unit) {
    val reservationSystem = ReservationSystem()

    var buildingCode by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var newOperatingSystem by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Change Room Operating System", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = buildingCode,
            onValueChange = { buildingCode = it },
            label = { Text("Building Code (e.g., JM)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = roomNumber,
            onValueChange = { roomNumber = it },
            label = { Text("Room Number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = newOperatingSystem,
            onValueChange = { newOperatingSystem = it },
            label = { Text("New Operating System (Windows, Mac, Linux)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            feedbackMessage = reservationSystem.updateRoomOperatingSystem(
                buildingCode = buildingCode,
                roomNumber = roomNumber.toIntOrNull() ?: -1,
                newOperatingSystem = newOperatingSystem
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Update Operating System")
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (feedbackMessage.isNotEmpty()) {
            Text(feedbackMessage, color = MaterialTheme.colors.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun ViewAndCancelBookingsScreen(userName: String, onBackToDashboard: () -> Unit) {
    val reservationSystem = ReservationSystem()

    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var selectedBookingId by remember { mutableStateOf<Int?>(null) }
    var confirmationMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        bookings = reservationSystem.getUserBookings(userName)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Your Bookings", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        if (bookings.isEmpty()) {
            Text("No bookings found.", style = MaterialTheme.typography.body1)
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f) // take up remaining vertical space
                    .fillMaxWidth()
            ) {
                items(bookings) { booking ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Room: ${booking.room.buildingCode}-${booking.room.roomNumber}, " +
                                    "Computer: ${booking.computer.id}, " +
                                    "Day: ${booking.day}, Timeslot: ${booking.timeslot}",
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { selectedBookingId = booking.id }) {
                            Text("Cancel")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedBookingId != null) {
            confirmationMessage = reservationSystem.cancelBooking(selectedBookingId!!, userName)
            bookings = reservationSystem.getUserBookings(userName) // refresh bookings
            selectedBookingId = null // reset selection
        }

        if (confirmationMessage.isNotEmpty()) {
            Text(confirmationMessage, color = MaterialTheme.colors.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun RoomGraphicalViewScreen(
    userName: String,
    userType: String,
    buildingCode: String,
    roomNumber: Int,
    day: String,
    onBackToDashboard: () -> Unit
) {
    val reservationSystem = ReservationSystem()

    var computers by remember { mutableStateOf<List<Computer>>(emptyList()) }
    var feedbackMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            computers = reservationSystem.getComputersInRoom(buildingCode, roomNumber, day, userName)
            errorMessage = ""
        } catch (e: Exception) {
            errorMessage = e.message ?: "An unknown error occurred."
            computers = emptyList()
        }
    }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Room: $buildingCode-$roomNumber", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colors.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(20.dp).background(MaterialTheme.colors.secondary)) // green
                Spacer(modifier = Modifier.width(8.dp))
                Text("Available")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(20.dp).background(MaterialTheme.colors.primary)) // purple
                Spacer(modifier = Modifier.width(8.dp))
                Text("Booked by You")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(20.dp).background(MaterialTheme.colors.error)) // red
                Spacer(modifier = Modifier.width(8.dp))
                Text("Booked by Others")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("9-11am", "11am-1pm", "1-3pm", "3-5pm").forEach { timeslot ->
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = timeslot,
                        style = MaterialTheme.typography.subtitle1,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        for (row in computers.chunked(4)) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { computer ->
                    val color = when {
                        computer.isBookedByCurrentUser -> MaterialTheme.colors.primary
                        computer.isBooked -> MaterialTheme.colors.error
                        else -> MaterialTheme.colors.secondary
                    }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(color)
                            .clickable {
                                if (computer.isBookedByCurrentUser) {
                                    feedbackMessage = reservationSystem.cancelBooking(
                                        bookingId = computer.bookingId!!,
                                        userName = userName
                                    )
                                } else if (!computer.isBooked) {
                                    feedbackMessage = reservationSystem.bookSpecificComputer(
                                        userName = userName,
                                        buildingCode = buildingCode,
                                        roomNumber = roomNumber,
                                        day = day,
                                        timeslot = computer.timeslot ?: "No Timeslot",
                                        computerId = computer.id
                                    )
                                } else if (computer.isBooked && userType == "admin") {
                                    feedbackMessage = "Booked by: ${computer.bookedBy ?: "Unknown"}"
                                }

                                computers = reservationSystem.getComputersInRoom(
                                    buildingCode, roomNumber, day, userName
                                )
                            }
                    ) {
                        Text(
                            text = computer.id,
                            color = MaterialTheme.colors.onBackground,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (feedbackMessage.isNotEmpty()) {
            Text(feedbackMessage, color = MaterialTheme.colors.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Dashboard")
        }
    }
}

