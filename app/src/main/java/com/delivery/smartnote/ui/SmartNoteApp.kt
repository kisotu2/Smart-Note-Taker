package com.delivery.smartnote.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.delivery.smartnote.data.NoteEntity
import com.delivery.smartnote.data.NotesDatabase
import com.delivery.smartnote.domain.ExtractiveSummarizer
import com.delivery.smartnote.domain.LocalQuizGenerator
import com.delivery.smartnote.domain.Summary
import com.delivery.smartnote.domain.Validation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Context.preferences by preferencesDataStore("smartnote_settings")
private val NAME = stringPreferencesKey("name")

class SmartViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication<Application>().applicationContext
    private val dao = NotesDatabase.get(context).notes()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val notes = dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val name = context.preferences.data.map { it[NAME] ?: "Student" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Student")
    var currentUser by mutableStateOf<FirebaseUser?>(null)
        private set
    var sourceUri by mutableStateOf<Uri?>(null)
    var extracted by mutableStateOf("")
    var title by mutableStateOf("")
    var summary by mutableStateOf(Summary("", emptyList(), ""))
    var busy by mutableStateOf(value = false)
    var error by mutableStateOf("")
    var savedId by mutableLongStateOf(0L)

    init {
        try {
            currentUser = auth.currentUser
            auth.addAuthStateListener { firebaseAuth ->
                currentUser = firebaseAuth.currentUser
            }
        } catch (_: Exception) {
            // Firebase init fallback for local test context
        }
    }

    fun setName(value: String) = viewModelScope.launch { context.preferences.edit { it[NAME] = value.trim() } }

    fun loginWithFirebase(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        busy = true
        error = ""
        try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            currentUser = result.user
            val displayName = currentUser?.displayName?.ifBlank { null } ?: email.substringBefore("@")
            setName(displayName)
            onSuccess()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Authentication failed"
            error = msg
            onError(msg)
        } finally {
            busy = false
        }
    }

    fun registerWithFirebase(email: String, pass: String, displayName: String, onSuccess: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        busy = true
        error = ""
        try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user
            if ((user != null) && displayName.isNotBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            }
            currentUser = user
            if (displayName.isNotBlank()) setName(displayName)
            onSuccess()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Registration failed"
            error = msg
            onError(msg)
        } finally {
            busy = false
        }
    }

    fun signOutFirebase() {
        try {
            auth.signOut()
        } catch (_: Exception) {}
        currentUser = null
    }

    fun recognize(onDone: () -> Unit) = viewModelScope.launch {
        val uri = sourceUri ?: return@launch
        busy = true
        error = ""
        extracted = ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            extracted = recognizer.process(image).await().text
            if (extracted.isBlank()) error = "No text was detected. Try a clearer image or type your notes."
            onDone()
        } catch (e: Exception) {
            error = "Text recognition failed: ${e.localizedMessage ?: "unknown error"}"
            onDone()
        } finally {
            recognizer.close()
            busy = false
        }
    }

    fun summarize(): Boolean {
        if (extracted.trim().length < 20) {
            error = "Please enter at least 20 characters of note text."
            return false
        }
        summary = ExtractiveSummarizer().summarize(extracted)
        error = ""
        return true
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        if (busy) return@launch
        if (title.isBlank() || extracted.isBlank() || summary.text.isBlank()) {
            error = "Title, original text and summary are required."
            return@launch
        }
        busy = true
        try {
            savedId = dao.insert(NoteEntity(title = title.trim(), original = extracted.trim(), summary = summary.text, keyPoints = summary.points.joinToString("\n")))
            onSaved()
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Unable to save note"
        } finally {
            busy = false
        }
    }

    fun toggle(note: NoteEntity) = viewModelScope.launch { dao.update(note.copy(favorite = !note.favorite, updatedAt = System.currentTimeMillis())) }
    fun delete(note: NoteEntity) = viewModelScope.launch { dao.delete(note) }
    fun update(note: NoteEntity, title: String, original: String, summary: String) = viewModelScope.launch {
        dao.update(note.copy(title = title.trim(), original = original.trim(), summary = summary.trim(), updatedAt = System.currentTimeMillis()))
    }
    fun clear() = viewModelScope.launch { dao.clear() }
}

@Composable
fun SmartNoteApp() {
    val application = LocalContext.current.applicationContext as Application
    val vm: SmartViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SmartViewModel(application) as T
        },
    )
    val nav = rememberNavController()
    val notes by vm.notes.collectAsState()
    val name by vm.name.collectAsState()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route.orEmpty()
    val tabs = listOf("home", "notes", "favorites", "quiz", "settings")

    Scaffold(
        bottomBar = {
            if (route in tabs) {
                NavigationBar {
                    listOf(
                        Triple("home", "Home", Icons.Default.Home),
                        Triple("notes", "Notes", Icons.Default.Description),
                        Triple("favorites", "Favorites", Icons.Default.Favorite),
                        Triple("quiz", "Quiz", Icons.Default.School),
                        Triple("settings", "Settings", Icons.Default.Settings),
                    ).forEach { (path, label, icon) ->
                        NavigationBarItem(
                            selected = route == path,
                            onClick = { nav.navigate(path) { popUpTo("home"); launchSingleTop = true } },
                            icon = { Icon(icon, label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "welcome", modifier = Modifier.padding(padding)) {
            composable("welcome") {
                Page {
                    Spacer(Modifier.height(90.dp))
                    Icon(Icons.Default.AutoStories, null, Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
                    Heading("SmartNote")
                    Text("Turn your notes into concise study material.")
                    Spacer(Modifier.height(30.dp))
                    if (vm.currentUser != null) {
                        Text("Signed in as: ${vm.currentUser?.email}")
                        Primary("Go to App") { nav.navigate("home") }
                    } else {
                        Primary("Get Started") { nav.navigate("login") }
                        TextButton(onClick = { nav.navigate("home") }) { Text("Continue in guest mode") }
                    }
                }
            }
            composable("login") {
                var email by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                Page {
                    Heading("Welcome Back")
                    Text("Sign in with your Firebase account or use guest mode.")
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
                    Primary(if (vm.busy) "Signing in..." else "Sign In with Firebase") {
                        when {
                            email.isBlank() -> message = "Enter your email"
                            !Validation.validEmail(email) -> message = "Enter a valid email address"
                            pass.isBlank() -> message = "Enter your password"
                            else -> {
                                message = ""
                                vm.loginWithFirebase(email, pass, onSuccess = { nav.navigate("home") }) { message = it }
                            }
                        }
                    }
                    TextButton(onClick = { nav.navigate("register") }) { Text("Create Firebase Account") }
                    TextButton(onClick = { nav.navigate("home") }) { Text("Use guest demo") }
                }
            }
            composable("register") {
                var display by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }
                var confirm by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                Page {
                    Heading("Create Account")
                    Text("Register a new Firebase account.")
                    OutlinedTextField(display, { display = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                    Primary(if (vm.busy) "Creating Account..." else "Sign Up with Firebase") {
                        message = when {
                            display.isBlank() -> "Enter your name"
                            !Validation.validEmail(email) -> "Enter a valid email"
                            !Validation.validPassword(pass) -> "Use at least 8 characters"
                            pass != confirm -> "Passwords do not match"
                            else -> ""
                        }
                        if (message.isEmpty()) {
                            vm.registerWithFirebase(email, pass, display, onSuccess = { nav.navigate("home") }) { message = it }
                        }
                    }
                    TextButton(onClick = { nav.popBackStack() }) { Text("Back to login") }
                }
            }
            composable("home") {
                Page {
                    Heading("Hello, $name!")
                    Text("Let's turn your notes into knowledge.")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Scan Notes", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                            Text("Capture a page and turn it into study material", color = Color.White)
                            Button(onClick = { nav.navigate("scan") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary)) {
                                Text("Scan Now")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("My Notes", notes.size.toString(), Modifier.weight(1f)) { nav.navigate("notes") }
                        Tile("Favorites", notes.count { (_, _, _, _, _, _, _, favorite) -> favorite }.toString(), Modifier.weight(1f)) { nav.navigate("favorites") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("Study Quiz", "Practice", Modifier.weight(1f)) { nav.navigate("quiz") }
                        Tile("Settings", "Profile", Modifier.weight(1f)) { nav.navigate("settings") }
                    }
                    Heading("Recent Notes")
                    if (notes.isEmpty()) {
                        Text("No notes yet. Scan a page or create a note to begin.")
                    } else {
                        notes.take(5).forEach { note -> NoteCard(note, favorite = { vm.toggle(note) }) { nav.navigate("detail/${note.id}") } }
                    }
                    OutlinedButton(onClick = { vm.extracted = ""; vm.title = ""; nav.navigate("text") }) { Text("Write a note manually") }
                }
            }
            composable("scan") { ScanScreen(vm, onPreview = { nav.navigate("preview") }) { vm.extracted = ""; nav.navigate("text") } }
            composable("preview") {
                Page {
                    Heading("Image Preview")
                    vm.sourceUri?.let { AsyncImage(model = it, contentDescription = "Selected note image", modifier = Modifier.fillMaxWidth().height(350.dp)) }
                    Primary("Extract text") { vm.recognize { nav.navigate("text") } }
                    if (vm.busy) CircularProgressIndicator()
                    TextButton(onClick = { nav.popBackStack() }) { Text("Choose another image") }
                }
            }
            composable("text") {
                Page {
                    Heading("Extracted Text")
                    if (vm.error.isNotEmpty()) Text(vm.error, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(vm.extracted, { vm.extracted = it }, label = { Text("Edit or type your note") }, minLines = 10, modifier = Modifier.fillMaxWidth())
                    Primary("Summarize") { if (vm.summarize()) nav.navigate("summary") }
                }
            }
            composable("summary") {
                Page {
                    Heading("Study Summary")
                    Text("Method: ${vm.summary.method}")
                    OutlinedTextField(vm.title, { vm.title = it }, label = { Text("Note title") })
                    Text("Summary", fontWeight = FontWeight.Bold)
                    OutlinedTextField(vm.summary.text, { vm.summary = vm.summary.copy(text = it) }, minLines = 5, modifier = Modifier.fillMaxWidth())
                    Heading("Key points")
                    vm.summary.points.forEach { Text("• $it") }
                    if (vm.error.isNotBlank()) Text(vm.error, color = MaterialTheme.colorScheme.error)
                    Primary(if (vm.busy) "Saving..." else "Save Note") { vm.save { nav.navigate("saved") } }
                    OutlinedButton(onClick = { share(application, vm.title, vm.summary.text) }) { Text("Share summary") }
                    TextButton(onClick = { nav.popBackStack() }) { Text("Edit original text") }
                }
            }
            composable("saved") {
                Page {
                    Heading("Note saved!")
                    Text("Your note is stored on this device.")
                    Primary("View Note") { nav.navigate("detail/${vm.savedId}") }
                    OutlinedButton(onClick = { nav.navigate("home") }) { Text("Back to Home") }
                }
            }
            composable("notes") { NoteList("My Notes", notes, vm, open = { nav.navigate("detail/$it") }) { nav.navigate("scan") } }
            composable("favorites") { NoteList("Favorites", notes.filter { (_, _, _, _, _, _, _, favorite) -> favorite }, vm, open = { nav.navigate("detail/$it") }) { nav.navigate("notes") } }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val noteId = entry.arguments?.getLong("id")
                val note = notes.firstOrNull { (id) -> id == noteId }
                if (note != null) {
                    Detail(note, vm) { nav.popBackStack() }
                } else {
                    Page { Text("Loading note or note not found") }
                }
            }
            composable("quiz") { QuizScreen(notes) }
            composable("settings") {
                var display by remember(name) { mutableStateOf(name) }
                var confirm by remember { mutableStateOf(value = false) }
                Page {
                    Heading("Settings")
                    OutlinedTextField(display, { display = it }, label = { Text("Display name") })
                    Primary("Save display name") { if (display.isNotBlank()) vm.setName(display) }
                    if (vm.currentUser != null) {
                        Heading("Firebase Account")
                        Text("Signed in as: ${vm.currentUser?.email}")
                        OutlinedButton(onClick = { vm.signOutFirebase(); nav.navigate("welcome") { popUpTo("home") { inclusive = true } } }) {
                            Text("Sign Out of Firebase")
                        }
                    }
                    Heading("About SmartNote")
                    Text("Offline-first study companion. OCR uses on-device ML Kit. Summaries are extractive, not generated by an AI model.")
                    Text("Privacy: notes and preferences remain on your device.")
                    OutlinedButton(onClick = { confirm = true }) { Text("Clear all saved notes") }
                    OutlinedButton(onClick = { nav.navigate("welcome") { popUpTo("home") { inclusive = true } } }) { Text("Exit session") }
                    if (confirm) {
                        AlertDialog(
                            onDismissRequest = { confirm = false },
                            title = { Text("Delete all notes?") },
                            text = { Text("This cannot be undone.") },
                            confirmButton = { TextButton(onClick = { vm.clear(); confirm = false }) { Text("Delete") } },
                            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable private fun Heading(text: String) { Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
@Composable private fun Primary(text: String, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(text) } }
@Composable private fun Tile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) { Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp)) { Text(label, fontWeight = FontWeight.Bold); Text(value) } } }

@Composable
private fun NoteCard(note: NoteEntity, favorite: () -> Unit, open: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = open)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(note.title, fontWeight = FontWeight.Bold)
                Text(note.summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(DateFormat.getDateTimeInstance().format(Date(note.updatedAt)), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = favorite) {
                Icon(if (note.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Toggle favorite")
            }
        }
    }
}

@Composable
private fun NoteList(title: String, notes: List<NoteEntity>, vm: SmartViewModel, open: (Long) -> Unit, action: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Page {
        Heading(title)
        OutlinedTextField(query, { query = it }, label = { Text("Search notes") }, modifier = Modifier.fillMaxWidth())
        val visible = notes.filter { (_, title, original, summary) -> title.contains(query, ignoreCase = true) || original.contains(query, ignoreCase = true) || summary.contains(query, ignoreCase = true) }
        if (visible.isEmpty()) {
            Text("No matching notes. Add or scan your first note.")
            OutlinedButton(onClick = action) { Text("Explore notes") }
        }
        visible.forEach { note ->
            NoteCard(note, favorite = { vm.toggle(note) }) { open(note.id) }
        }
    }
}

@Composable
private fun Detail(note: NoteEntity, vm: SmartViewModel, back: () -> Unit) {
    var edit by remember(note.id) { mutableStateOf(value = false) }
    var title by remember(note.id) { mutableStateOf(note.title) }
    var original by remember(note.id) { mutableStateOf(note.original) }
    var summary by remember(note.id) { mutableStateOf(note.summary) }
    var confirm by remember { mutableStateOf(value = false) }
    val context = LocalContext.current

    Page {
        Heading(if (edit) "Edit Note" else note.title)
        if (edit) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") })
            OutlinedTextField(summary, { summary = it }, label = { Text("Summary") }, minLines = 4)
            OutlinedTextField(original, { original = it }, label = { Text("Original text") }, minLines = 5)
            Primary("Save changes") {
                if (title.isNotBlank() && original.isNotBlank() && summary.isNotBlank()) {
                    vm.update(note, title, original, summary)
                    edit = false
                }
            }
        } else {
            Text(note.summary)
            Heading("Key Points")
            note.keyPoints.lines().filter { it.isNotBlank() }.forEach { Text("• $it") }
            Heading("Original Text")
            Text(note.original)
            Text("Created: ${DateFormat.getDateTimeInstance().format(Date(note.createdAt))}")
            Text("Updated: ${DateFormat.getDateTimeInstance().format(Date(note.updatedAt))}")
            OutlinedButton(onClick = { vm.toggle(note) }) { Text(if (note.favorite) "Remove from Favorites" else "Add to Favorites") }
            OutlinedButton(onClick = { edit = true }) { Text("Edit") }
            OutlinedButton(onClick = { share(context, note.title, note.summary) }) { Text("Share") }
            OutlinedButton(onClick = { confirm = true }) { Text("Delete Note") }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Delete this note?") },
                confirmButton = { TextButton(onClick = { vm.delete(note); confirm = false; back() }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            )
        }
    }
}

private fun share(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\n\n$text")
    }
    context.startActivity(Intent.createChooser(intent, "Share SmartNote").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun ScanScreen(vm: SmartViewModel, onPreview: () -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            vm.sourceUri = uri
            onPreview()
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            vm.sourceUri = cameraUri
            onPreview()
        }
    }
    Page {
        Heading("Scan Notes")
        Text("Take a photo or select an image of your study notes.")
        Icon(Icons.Default.DocumentScanner, null, Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
        Primary("Choose image") { picker.launch("image/*") }
        OutlinedButton(
            onClick = {
                try {
                    val file = File.createTempFile("scan_", ".jpg", context.cacheDir)
                    val authority = "${context.packageName}.fileprovider"
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    cameraUri = uri
                    camera.launch(uri)
                } catch (e: Exception) {
                    vm.error = "Camera unavailable: ${e.localizedMessage}"
                }
            },
        ) { Text("Take photo") }
        OutlinedButton(onClick = onManual) { Text("Type notes manually") }
        if (vm.error.isNotEmpty()) Text(vm.error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun QuizScreen(notes: List<NoteEntity>) {
    var selected by remember { mutableStateOf<Long?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var choice by remember { mutableIntStateOf(-1) }
    var score by remember { mutableIntStateOf(0) }
    var submitted by remember { mutableStateOf(value = false) }
    val note = notes.firstOrNull { (id) -> id == selected }
    val questions = remember(note?.id, note?.original) { note?.let { (_, _, original) -> LocalQuizGenerator().generate(original) } ?: emptyList() }
    Page {
        Heading("Study Quiz")
        if (note == null) {
            Text("Choose a saved note to practise.")
            if (notes.isEmpty()) Text("Save a note before starting a quiz.")
            notes.forEach { (id, title) -> OutlinedButton(onClick = { selected = id; index = 0; choice = -1; score = 0; submitted = false }, modifier = Modifier.fillMaxWidth()) { Text(title) } }
        } else if (questions.isEmpty()) {
            Text("Not enough structured material to generate reliable questions. Add at least four lines in the form 'Term: explanation' to the note's original text.")
            OutlinedButton(onClick = { selected = null }) { Text("Choose another note") }
        } else if (index >= questions.size) {
            Heading("Results: $score / ${questions.size}")
            Primary("Retry") { index = 0; score = 0; choice = -1; submitted = false }
            OutlinedButton(onClick = { selected = null }) { Text("Choose another note") }
        } else {
            val (prompt, options, correctIndex) = questions[index]
            Text("Question ${index + 1} of ${questions.size}")
            Text(prompt, fontWeight = FontWeight.Bold)
            options.forEachIndexed { i, option ->
                OutlinedButton(
                    onClick = { if (!submitted) choice = i },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (choice == i) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                ) { Text(option) }
            }
            if (submitted) Text(if (choice == correctIndex) "Correct!" else "Correct answer: ${options[correctIndex]}")
            Primary(if (submitted) "Next" else "Submit") {
                if (!submitted && (choice >= 0)) {
                    if (choice == correctIndex) score++
                    submitted = true
                } else if (submitted) {
                    index++
                    choice = -1
                    submitted = false
                }
            }
        }
    }
}
