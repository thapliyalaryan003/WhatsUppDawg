package com.example.whatsuppdawg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat.startActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = Firebase.auth
        var currentUser = auth.currentUser
        setContent {
                Greeting(this, currentUser);
            }

    }
}

fun Greeting(context: Context, currentUser: FirebaseUser? ) {
        if(currentUser== null){
            val intent = Intent(context, loginPage::class.java);
            startActivity(context,intent,null);
        }
        else{
            val intent = Intent(context, Dashboard::class.java);
        // Toast(Text("You're not in homies list my man, ring aryanthapliyal.bt22ece@pec.edu.in to get in"))
        }
    }


/*@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WhatsUppDawgTheme {
        Greeting()
    }
}
*/