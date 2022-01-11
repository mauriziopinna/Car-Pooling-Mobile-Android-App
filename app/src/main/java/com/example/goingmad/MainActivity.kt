package com.example.goingmad

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var mAuth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mAuth = FirebaseAuth.getInstance()
        val user = mAuth.currentUser

        Handler().postDelayed({
            if (user != null) {
                var nickname : String?
                //set image reference for the firebase storage
                //get instance of the firebase db and actual user information
                val db = FirebaseFirestore.getInstance()
                val users=db.collection("users")
                val userDoc=users.document(user.uid)
                nickname = null
                userDoc.get().addOnSuccessListener{ result->
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
                val signInIntent = Intent(this, SignInActivity::class.java)
                startActivity(signInIntent)
                finish()
            }
        }, 2000)
    }
}