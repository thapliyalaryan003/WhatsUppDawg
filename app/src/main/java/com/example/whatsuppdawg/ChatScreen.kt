package com.example.whatsuppdawg

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.* // For Card, Text, TextField, Button, Scaffold, TopAppBar, etc.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel // REQUIRED for viewModel() composable
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp // For @ServerTimestamp annotation
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date // For Date type for timestamp

// Your existing Message Data Class - adapted to include receiverId and use Date for timestamp
data class Message(
    val senderId: String = "",
    val receiverId: String = "", // For 1-on-1, useful for filtering/display
    val text: String = "", // Changed from 'content' to 'text' as per your definition
    @ServerTimestamp val timestamp: Date? = null // Using Date and @ServerTimestamp
) {
    // No-argument constructor is REQUIRED by Firestore to deserialize documents
    // into Kotlin objects. It provides default values for the properties.
    constructor() : this("", "", "", null)
}

// ChatViewModel: Manages chat-related data and logic
class ChatViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth
    private var chatListener: ListenerRegistration? = null

    // Observable list to hold chat messages
    val messages = mutableStateListOf<Message>()

    // State for the message input field
    var messageInput by mutableStateOf("")
        private set

    // Private StateFlow to hold the current chat room ID
    private val _chatId = MutableStateFlow<String?>(null)
    val chatId = _chatId.asStateFlow()

    // Store the other user's UID and Email for easy access within the ViewModel
    private var _otherUserId: String? = null
    private var _otherUserEmail: String? = null

    init {
        // No direct fetching here, as currentUserId and otherUserId are needed,
        // which are set via setChatParticipants.
    }

    /**
     * Sets the participants for the current chat and initializes message listening.
     * This method should be called from the Composable (e.g., using LaunchedEffect)
     * once both user IDs are available.
     *
     * @param currentUserId The UID of the current authenticated user.
     * @param otherUserId The UID of the other user in the chat.
     * @param otherUserEmail Optional: The email of the other user (for display purposes).
     */
    fun setChatParticipants(currentUserId: String, otherUserId: String, otherUserEmail: String?) {
        _otherUserId = otherUserId
        _otherUserEmail = otherUserEmail

        // Generate a consistent chat ID by sorting UIDs to ensure a unique ID for each pair
        val sortedUids = listOf(currentUserId, otherUserId).sorted()
        val newChatId = "${sortedUids[0]}_${sortedUids[1]}"

        // If the chat ID has changed, update and re-fetch messages
        if (_chatId.value != newChatId) {
            _chatId.value = newChatId
            fetchMessages(newChatId)
            Log.d("ChatViewModel", "Chat ID set to: $newChatId, other user: $otherUserId")
        }
    }

    /**
     * Fetches messages for a given chat room ID from Firestore in real-time.
     * Attaches a snapshot listener that updates the `messages` list whenever
     * there are changes in the Firestore collection.
     *
     * @param chatRoomId The ID of the chat room to listen to.
     */
    private fun fetchMessages(chatRoomId: String) {
        // Remove any existing listener to prevent duplicates and memory leaks
        chatListener?.remove()
        messages.clear() // Clear existing messages when switching chats or re-fetching

        chatListener = db.collection("chat_rooms") // Top-level collection for chat rooms
            .document(chatRoomId) // Document for the specific chat room
            .collection("messages") // Subcollection for messages within that chat room
            .orderBy("timestamp", Query.Direction.ASCENDING) // Order messages chronologically
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ChatViewModel", "Listen for messages failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    messages.clear() // Clear and re-populate for current state
                    for (document in snapshots.documents) {
                        val message = document.toObject(Message::class.java)
                        message?.let {
                            messages.add(it)
                        }
                    }
                    Log.d("ChatViewModel", "Fetched ${messages.size} messages for chat $chatRoomId")
                } else {
                    Log.d("ChatViewModel", "Current messages data: null for chat $chatRoomId")
                }
            }
    }

    /**
     * Updates the content of the message input field.
     * @param newText The new text entered by the user.
     */
    fun onMessageInputChange(newText: String) {
        messageInput = newText
    }

    /**
     * Sends the current message to Firestore.
     * Creates a new Message document in the appropriate chat room subcollection.
     */
    fun sendMessage() {
        val currentUserId = auth.currentUser?.uid
        val currentChatId = _chatId.value

        // Ensure all necessary data is present before sending
        if (messageInput.isBlank() || currentUserId == null || currentChatId == null || _otherUserId == null) {
            Log.w("ChatViewModel", "Cannot send message: input blank, no current user, no chat ID, or no other user ID.")
            return
        }

        // Create a new Message object using your Message data class fields
        val message = Message(
            senderId = currentUserId,
            receiverId = _otherUserId!!, // Use non-null asserted as we checked above
            text = messageInput.trim(), // Use 'text' field
            timestamp = null // @ServerTimestamp will fill this on the server
        )

        // Add the message to Firestore
        db.collection("chat_rooms")
            .document(currentChatId)
            .collection("messages")
            .add(message) // .add() automatically generates a new document ID
            .addOnSuccessListener {
                Log.d("ChatViewModel", "Message sent successfully! Doc ID: ${it.id}")
                messageInput = "" // Clear input on success
            }
            .addOnFailureListener { e ->
                Log.e("ChatViewModel", "Error sending message", e)
                // Optionally, show a Toast to the user
            }
    }

    // Called when the ViewModel is no longer used, crucial for cleanup
    override fun onCleared() {
        super.onCleared()
        chatListener?.remove() // Remove the Firestore listener
        Log.d("ChatViewModel", "Chat listener removed from ViewModel.")
    }
}

// ChatScreen Activity: Hosts the Jetpack Compose UI for the chat screen
class ChatScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enables edge-to-edge display

        // Retrieve data passed from the Dashboard Activity via Intent
        val otherUserId = intent.getStringExtra("userId")
        val otherUserEmail = intent.getStringExtra("userEmail") // Optional: for display in TopAppBar

        // Basic validation: if no other user ID, we can't start a chat
        if (otherUserId == null) {
            Log.e("ChatScreen", "Error: No other user ID provided to ChatScreen.")
            finish() // Close the activity
            return
        }

        setContent {
            // Pass the retrieved data to your chatScreen Composable
            chatScreen(otherUserId = otherUserId, otherUserEmail = otherUserEmail)
        }
    }
}

// chatScreen Composable: Defines the UI layout and interacts with ChatViewModel
@OptIn(ExperimentalMaterial3Api::class) // Opt-in for experimental Material 3 APIs like TopAppBar
@Composable
fun chatScreen(
    otherUserId: String, // The UID of the user you are chatting with
    otherUserEmail: String?, // The email of the other user (for display)
    chatViewModel: ChatViewModel = viewModel() // ViewModel automatically managed by Compose
) {
    val currentUserId = Firebase.auth.currentUser?.uid // Current authenticated user's UID
    val context = LocalContext.current // Android Context for Intent operations

    // LaunchedEffect: A Compose side effect to initialize the ViewModel with participant IDs
    LaunchedEffect(currentUserId, otherUserId) {
        if (currentUserId != null) {
            chatViewModel.setChatParticipants(currentUserId, otherUserId, otherUserEmail)
        } else {
            // Handle case where current user is unexpectedly not logged in
            Log.e("chatScreen", "Current user not logged in. Redirecting to login.")
            val intent = Intent(context, loginPage::class.java).apply { // **IMPORTANT: Replace YourLoginActivity**
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    // Observe messages and message input from the ViewModel
    val messages = chatViewModel.messages // Direct access to mutableStateListOf
    val messageInput = chatViewModel.messageInput // Delegated property for easy access

    // Scaffold provides basic Material Design structure (TopAppBar, content area)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // Display other user's email if available, otherwise a truncated UID
                        text = "Chat with ${otherUserEmail ?: "User ${otherUserId.take(6)}..."}",
                        color = Color.White // White text for title
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black) // Black TopAppBar
            )
        },
        containerColor = Color.Black // Set background of the entire screen to black
    ) { paddingValues -> // Padding provided by Scaffold for content
        Column(
            modifier = Modifier
                .fillMaxSize() // Fills entire available space
                .padding(paddingValues) // Apply Scaffold's padding
                .background(Color.Black) // Explicitly set Column background to black
        ) {
            // LazyColumn to display the list of messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f) // Takes up available vertical space
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = rememberLazyListState(),
                reverseLayout = false, // Messages flow from top to bottom
                verticalArrangement = Arrangement.spacedBy(8.dp), // Space between bubbles
                flingBehavior = ScrollableDefaults.flingBehavior(),
                userScrollEnabled = true
            ) {
                items(messages) { message ->
                    // Determine if the message was sent by the current user
                    val isCurrentUser = message.senderId == currentUserId
                    MessageBubble(message = message, isCurrentUser = isCurrentUser)
                }
            }

            // Message Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    // Dark background with rounded corners for the input bar
                    .background(Color.DarkGray, RoundedCornerShape(16.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TextField for message input
                TextField(
                    value = messageInput,
                    onValueChange = { chatViewModel.onMessageInputChange(it) },
                    modifier = Modifier.weight(1f), // Takes up most of the row's width
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(modifier = Modifier.width(8.dp)) // Space between TextField and Button

                // Send Button
                Button(
                    onClick = { chatViewModel.sendMessage() },
                    enabled = messageInput.isNotBlank(), // Enable button only if text is present
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp) // Match TextField height
                ) {
                    Text("Send", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

// Composable for displaying an individual message bubble
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start // Align right for sender, left for receiver
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp) // Max width for message bubble
                .padding(vertical = 2.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                // Different background colors for sender/receiver bubbles
                containerColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else Color(0xFF323232)
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = message.text, // Using 'text' field from your Message data class
                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.align(if (isCurrentUser) Alignment.End else Alignment.Start),
                    textAlign = if (isCurrentUser) TextAlign.End else TextAlign.Start
                )
                // Optional: Display timestamp
                message.timestamp?.let {
                    Text(
                        text = it.toLocaleString(), // Convert Date to readable string
                        color = (if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else Color.LightGray).copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(if (isCurrentUser) Alignment.End else Alignment.Start)
                    )
                }
            }
        }
    }
}

// Preview Composable for Android Studio preview panel
/*@Preview(showBackground = true)
@Composable
fun PreviewChatScreen() {
    // Provide dummy data and a mock ViewModel for preview purposes
    val dummyMessages = listOf(
        Message("user123", "otherUser456", "Hey there!", Date()),
        Message("otherUser456", "user123", "Hello! How are you?", Date(System.currentTimeMillis() + 1000)),
        Message("user123", "otherUser456", "I'm good, thanks! Just testing this chat.", Date(System.currentTimeMillis() + 2000)),
        Message("otherUser456", "user123", "Looks great!", Date(System.currentTimeMillis() + 3000))
    )

    // A simple mock ViewModel for preview, overriding necessary functions
    class MockChatViewModel : ChatViewModel() {
        init {
            messages.addAll(dummyMessages)
            _chatId.value = "mock_chat_id"
        }
        override fun sendMessage() {
            Log.d("PreviewChat", "Attempted to send message (Preview): $messageInput")
            messageInput = ""
        }
    }

    // Call the main Composable with mock data and the mock ViewModel
    chatScreen(otherUserId = "otherUser456", otherUserEmail = "other@example.com", chatViewModel = MockChatViewModel())
}*/