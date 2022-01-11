
package com.example.goingmad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class DashboardActivity : AppCompatActivity() {
    private lateinit var mAuth: FirebaseAuth
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var imageHead : ImageView
    private lateinit var emailHead : TextView
    private lateinit var nameHead : TextView
    private lateinit var drawerLayout: DrawerLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        var nickname:String?=null
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
         drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navHeader = navView.getHeaderView(0)
        imageHead = navHeader.findViewById<ImageView>(R.id.ImageHeader)
        emailHead = navHeader.findViewById<TextView>(R.id.emailHeader)
        nameHead = navHeader.findViewById<TextView>(R.id.nameHeader)

        //set image reference for the firebase storage
        val userid=currentUser?.uid
        val imageRef = Firebase.storage.reference.child("users/$userid.jpg")
        //get instance of the firebase db and actual user information
        val db = FirebaseFirestore.getInstance()
        val users=db.collection("users")
        val user=users.document(userid!!)

        user.addSnapshotListener { value, error ->
            if (error != null) throw error
            if (value != null) {
                nameHead.text = (value["fullName"]).toString()
                emailHead.text = (value["email"]).toString()
            }
        }

        if(savedInstanceState==null){
            nickname = intent.getStringExtra("nickname")
            val navController = findNavController(R.id.nav_host_fragment)
            if (nickname==null || nickname=="") {
                navController.graph.startDestination = R.id.nav_edit_profile
                navController.graph=navController.graph
            }
        }

        imageRef.getBytes(1024*1024).addOnSuccessListener {
            // if manage to download image from database
            val byteArray = it
            val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            imageHead.setImageBitmap(imageBitmap)
        }.addOnFailureListener{
            imageHead.setImageResource(R.drawable.default_user_image)
            imageHead.imageAlpha = 255
        }
        
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        topLevelFragment(setOf(R.id.nav_trip_list, R.id.others_nav_trip_list,  R.id.nav_trip_of_interest_list, R.id.nav_bought_trip_list, R.id.nav_show_profile))

    }
    fun topLevelFragment(set : Set<Int>){
        val navView: NavigationView = findViewById(R.id.nav_view)
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(set, drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        drawerLayout.closeDrawers()
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("group28.lab2.NICKNAME", nameHead.text.toString())
        outState.putString("group28.lab2.EMAIL", emailHead.text.toString())
        val imageBitmap = imageHead.drawable.toBitmap()
        val stream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray=stream.toByteArray()
        outState.putByteArray("group28.lab2.IMAGE_SAVED",byteArray)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        nameHead.text = savedInstanceState.getString("group28.lab2.NICKNAME")
        emailHead.text = savedInstanceState.getString("group28.lab2.EMAIL")
        val byteArray = savedInstanceState.getByteArray("group28.lab2.IMAGE_SAVED")!!
        val imageBitmap = BitmapFactory.decodeByteArray(byteArray,0, byteArray.size)
        imageHead.setImageBitmap(imageBitmap)
    }
}