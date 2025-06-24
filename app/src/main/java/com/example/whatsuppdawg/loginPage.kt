package com.example.whatsuppdawg

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity

import com.google.firebase.auth.ktx.auth

import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore


data class appUser(
    val uid: String? = null,
    val email: String? = null,
){
    constructor():this("","");
}


class loginPage : ComponentActivity() {
    fun signinUsers(email: String, password: String){
        Firebase.auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val intent= Intent(this, Dashboard::class.java);
                    startActivity(this, intent, null);
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success")
                    val user = Firebase.auth.currentUser
                    //Firebase.auth.updateUI(user)
                    user?.let { firebaseUser ->
                        val db = Firebase.firestore
                        val appUser = appUser(
                            firebaseUser.uid,
                            email = firebaseUser.email ?: "",
                        )

                        db.collection("users").document(firebaseUser.uid)
                            .set(appUser)
                            .addOnSuccessListener {
                                Log.d("Firestore", "User data saved successfully!")
                            }
                            .addOnFailureListener { e ->
                                Log.w("Firestore", "Error writing document", e)
                            }
                    }


                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG,"signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        this,
                        "You're not on the list, ring aryanthapliyal.bt22ece@pec.edu.in to get credentials",
                        Toast.LENGTH_LONG,
                    ).show()
                    //ContextupdateUI(null)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent{
            LoginScreen(onSignInAttempt = { email, password -> signinUsers(email, password) })
        }
    }
}




@Composable
fun LoginScreen(onSignInAttempt: (String,String)-> Unit){
    val context= LocalContext.current;
    Surface(modifier= Modifier.fillMaxSize()) {
        Box(modifier = Modifier, contentAlignment= Alignment.TopCenter) {
            val bg = painterResource (R.drawable.loginbg);
            Image(painter= bg, contentDescription = null, modifier= Modifier, Alignment.BottomCenter);
                Column(modifier= Modifier.padding(top= 96.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    var id by remember {mutableStateOf("")};
                    TextField(modifier= Modifier.border(2.dp, color = Color.Gray, RoundedCornerShape(12.dp)), value= id, onValueChange ={id= it}, label = { Text("What you go by, homie?") });
                    var pass by remember {mutableStateOf("")};
                    TextField(modifier = Modifier.border(2.dp, color = Color.Gray, RoundedCornerShape(12.dp)), value= pass, onValueChange= {pass= it}, label = { Text("Yo, you got the code to get in?") });
                    Button(onClick = {
                        onSignInAttempt(id, pass);
                    },
                        modifier = Modifier,
                        colors = ButtonColors(Color(0xFF333333), Color.White, Color.Red, Color.White)) {
                        Text(text= "Let's Go");
                    }
                }

            }

        }
    }

