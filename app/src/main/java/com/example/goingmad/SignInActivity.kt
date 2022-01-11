package com.example.goingmad

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class SignInActivity : AppCompatActivity() {
    companion object {
        private const val RC_SIGN_IN =
            120 //120 is just an example, we can assign the number we want
    }

    private lateinit var mAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        val signInButton = findViewById<Button>(R.id.sign_in_btn)
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        mAuth = FirebaseAuth.getInstance()
        signInButton.setOnClickListener {
            signIn()
        }

    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val exeption = task.exception
            if (task.isSuccessful) {
                try {
                    // Google Sign In was successful, authenticate with Firebase
                    val account = task.getResult(ApiException::class.java)!!
                    Log.d("Sign in Activity", "firebaseAuthWithGoogle:" + account.id)
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    // Google Sign In failed
                    Log.w("Sign in Activity", "Google sign in failed", e)
                }
            } else {
                Log.w("Sign in Activity", exeption.toString())
            }

        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("Sign in Activity", "signInWithCredential:success")
                    //mAuth = FirebaseAuth.getInstance()
                    val currUser = mAuth.currentUser
                    val name = currUser?.displayName
                    val email = currUser?.email
                    val id = currUser?.uid
                    val newUser = mutableMapOf(
                        ("fullName" to name),
                        ("email" to email),
                        ("car" to ""),
                        ("dateOfBirth" to ""),
                        ("gender" to ""),
                        ("location" to ""),
                        ("nickname" to "")
                    ) as Map<String, *>
                    val db = FirebaseFirestore.getInstance()
                    val userCollection = db.collection("users")
                    val userDoc = userCollection.document(id!!)
                    userDoc.get().addOnSuccessListener { document ->
                        if (document.getData() == null) {
                            userDoc
                                .set(newUser)
                                .addOnSuccessListener { _ ->
                                    Log.d("User added", "DocumentSnapshot added")
                                }
                                .addOnFailureListener { e ->
                                    Log.w("Error adding user", "Error adding document", e)
                                }
                        }
                    }
                    var nickname : String?
                    //set image reference for the firebase storage
                    //get instance of the firebase db and actual user information
                    val users=db.collection("users")
                    val user=users.document(id)
                    nickname = null
                    user.get().addOnSuccessListener{ result->
                        if(result!=null){
                            nickname = (result["nickname"]).toString()
                        }
                        val intent = Intent(this, DashboardActivity::class.java)
                        intent.also {
                            it.putExtra("nickname",nickname)
                        }
                        startActivity(intent)
                        finish()
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("Sign In Activity", "signInWithCredential:failure", task.exception)
                }
            }
    }
}