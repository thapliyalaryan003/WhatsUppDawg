package com.example.whatsuppdawg

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase

class Dashboard : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = Firebase.auth.currentUser
        enableEdgeToEdge()
        setContent{dashboard()}
    }
}

class UserListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private var usersListener: ListenerRegistration? = null

    // This list will hold your AppUser objects and will be observed by Compose
    val users = mutableStateListOf<appUser>()

    init {
        // Start listening for users when the ViewModel is created
        fetchUsers()
    }

    private fun fetchUsers() {
        usersListener = db.collection("users")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("UserListViewModel", "Listen for users failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    // Clear the existing list to avoid duplicates on updates
                    users.clear()
                    // Iterate through each document in the query snapshot
                    for (document in snapshots.documents) {
                        // Convert each document into an AppUser object
                        val appUser = document.toObject(appUser::class.java)
                        appUser?.let {
                            // Add the non-null AppUser to our observable list
                            users.add(it)
                        }
                    }
                    Log.d("UserListViewModel", "Fetched ${users.size} users.")
                } else {
                    Log.d("UserListViewModel", "Current users data: null")
                }
            }
    }

    // Important: Remove the listener when the ViewModel is no longer needed
    // to prevent memory leaks and unnecessary updates.
    override fun onCleared() {
        super.onCleared()
        usersListener?.remove()
    }
}

@Composable
fun dashboard(userListViewModel: UserListViewModel = viewModel()){
    val context= LocalContext.current;
    Column(verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(all= 48.dp).background(color= Color(0xFF333333))){
    Column(){
        LazyColumn(
            modifier= Modifier,
            state = rememberLazyListState(),
            contentPadding = PaddingValues(0.dp),
            reverseLayout = false,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
            flingBehavior = ScrollableDefaults.flingBehavior(), // requires import androidx.compose.foundation.gestures.FlingBehavior
            userScrollEnabled =  true

        ){  items(userListViewModel.users){appUser->
                Card(modifier= Modifier.fillMaxWidth()
                    .clickable{
                        val intent = Intent(context, ChatScreen::class.java).apply {
                            // Optionally pass user data to the next screen
                            putExtra("userId", appUser.uid)
                            putExtra("userEmail", appUser.email)
                        }
                        startActivity(context, intent, null)
                    },
                )
                { Text(text = appUser.uid ?: "No UID",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer, // Uses a color from your app's theme
                            shape = RoundedCornerShape(10.dp) // Defines the rounded corners
                        )

                        // Ensures any content or subsequent drawing is confined to the defined shape
                        .clip(RoundedCornerShape(10.dp))

                        // Adds a subtle shadow for a raised effect
                        .shadow(
                            elevation = 4.dp, // The depth of the shadow
                            shape = RoundedCornerShape(10.dp) // The shadow follows the rounded shape
                        )

                        // Optional: A thin border around the styled text element
                        .border(
                            width = 1.dp, // Thickness of the border
                            color = MaterialTheme.colorScheme.primary, // Color of the border from your theme
                            shape = RoundedCornerShape(10.dp) // Border also follows the rounded shape
                        ))}

            }
        }
}
Row(horizontalArrangement = Arrangement.End){
    Button(onClick = {Firebase.auth.signOut();
    val intent= Intent(context, loginPage::class.java);
    startActivity(context, intent, null);
    }) {
        Text("Roll Out")
    }
}
    }}