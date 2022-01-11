package com.example.goingmad

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class ShowProfileFragment : Fragment(R.layout.fragment_show_profile) {
    private val args: ShowProfileFragmentArgs by navArgs()
    private lateinit var profileImage: ImageView
    private lateinit var name: TextView
    private lateinit var genderIcon: ImageView
    private lateinit var gender: TextView
    private lateinit var nickname: TextView
    private lateinit var email: TextView
    private lateinit var location: TextView
    private lateinit var car: TextView
    private lateinit var dateOfBirth: TextView
    private lateinit var fabButtonReview: ExtendedFloatingActionButton
    private lateinit var driverRatingB: RatingBar
    private lateinit var passengerRatingB: RatingBar
    private lateinit var driverListButton: ImageView
    private lateinit var passengerListButton: ImageView
    private lateinit var userId: String

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //get view element

        profileImage = view.findViewById<ImageView>(R.id.ProfileImage)
        name = view.findViewById<TextView>(R.id.fullname)
        genderIcon = view.findViewById<ImageView>(R.id.genderIcon)
        gender = view.findViewById<TextView>(R.id.gender)
        nickname = view.findViewById<TextView>(R.id.nickname)
        email = view.findViewById<TextView>(R.id.email)
        location = view.findViewById<TextView>(R.id.location)
        car = view.findViewById<TextView>(R.id.car)
        dateOfBirth = view.findViewById<TextView>(R.id.birthdate)
        fabButtonReview = view.findViewById<ExtendedFloatingActionButton>(R.id.showprofileFAB)
        driverRatingB = view.findViewById<RatingBar>(R.id.driverRatingBar)
        passengerRatingB = view.findViewById<RatingBar>(R.id.passengerRatingBar)
        passengerListButton = view.findViewById<ImageView>(R.id.passenger_list_button)
        driverListButton = view.findViewById<ImageView>(R.id.driver_list_button)
        val mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        //userId = currentUser?.uid.toString()
        val myUserId = currentUser?.uid.toString()
        val db = FirebaseFirestore.getInstance()

        if (args.fromBookedUsers != 0) {//from booked users, a different profile from the one logged has to be shown
            (activity as DashboardActivity).topLevelFragment(setOf(
                R.id.nav_trip_list,
                R.id.others_nav_trip_list,
                R.id.nav_trip_of_interest_list,
                R.id.nav_bought_trip_list)
            )

            val locationLabel = view.findViewById<TextView>(R.id.locationLabel)
            val carLabel = view.findViewById<TextView>(R.id.carLabel)
            val dateOfBirthLabel = view.findViewById<TextView>(R.id.birthdateLabel)
            locationLabel.visibility = View.INVISIBLE
            carLabel.visibility = View.INVISIBLE
            dateOfBirthLabel.visibility = View.INVISIBLE
            location.visibility = View.INVISIBLE
            car.visibility = View.INVISIBLE
            dateOfBirth.visibility = View.INVISIBLE
            //display the ratings on the passenger profile
            db.collection("reviews")
                .whereEqualTo("type", "D")
                .whereEqualTo("driverId", args.userId)
                .get()
                .addOnSuccessListener { documents->
                    val ratings: MutableList<Double> = ArrayList()
                    for(document in documents){
                        ratings.add(document.data["score"] as Double)
                    }
                    val avgRating = ratings.average()
                    driverRatingB.rating = avgRating.toFloat()
                    driverListButton.setOnClickListener {
                        val action = ShowProfileFragmentDirections
                            .actionNavShowProfileToReviewListFragment(args.userId,"D",getString(R.string.title_DriverReviewList))
                        findNavController().navigate(action)
                    }
                }
            db.collection("reviews")
                .whereEqualTo("type", "P")
                .whereEqualTo("passengerId", args.userId)
                .get()
                .addOnSuccessListener { documents->
                    val ratings: MutableList<Double> = ArrayList()
                    for(document in documents){
                        ratings.add(document.data["score"] as Double)
                    }
                    val avgRating = ratings.average()
                    passengerRatingB.rating = avgRating.toFloat()
                    passengerListButton.setOnClickListener {
                        val action = ShowProfileFragmentDirections
                            .actionNavShowProfileToReviewListFragment(args.userId,"P",getString(R.string.title_PassengerReviewList))
                        findNavController().navigate(action)
                    }
                }

            val trip = db.collection("trips").document(args.tripId.toString())
            trip.addSnapshotListener { value, error ->
                if (error != null) throw error
                if (value != null) {
                    if (value.data != null) {
                        if((value["tripEnded"]) as Boolean) {
                            val passengersReviewed = (value["passengersReviewed"]) as ArrayList<*>
                            if (passengersReviewed.contains(args.userId)) {//this passenger has already been reviewed by driver
                                fabButtonReview.visibility = View.INVISIBLE
                            } else {
                                fabButtonReview.visibility = View.VISIBLE
                                fabButtonReview.setOnClickListener {
                                    val action =
                                        ShowProfileFragmentDirections.actionNavShowProfileToReviewFragment(
                                            fromTripDetails = false,
                                            fromShowProfile = true,
                                            tripId = args.tripId, //passed by the bookedUserFragment
                                            driverId = myUserId,
                                            passengerId = args.userId,
                                            reviewerName = null,//passenger id, passed as argument by bookedUserFragment
                                            reviewedName = name.text.toString(),
                                            fragmentName = getString(R.string.title_PassengerReview)
                                        )
                                    findNavController().navigate(action)
                                }
                            }
                        }
                    }
                }
            }
        } else{ // if we're seeing the profile of the user that opened the app
            fabButtonReview.visibility = View.INVISIBLE
            db.collection("reviews")
                .whereEqualTo("type", "D")
                .whereEqualTo("driverId",myUserId)
                .get()
                .addOnSuccessListener { documents->
                    val ratings: MutableList<Double> = ArrayList()
                    for(document in documents){
                        ratings.add(document.data["score"] as Double)
                    }
                    val avgRating = ratings.average()
                    driverRatingB.rating = avgRating.toFloat()
                    driverListButton.setOnClickListener {
                        val action = ShowProfileFragmentDirections.actionNavShowProfileToReviewListFragment(myUserId,"D",getString(R.string.title_DriverReviewList) )
                        findNavController().navigate(action)
                    }
                }
            db.collection("reviews")
                .whereEqualTo("type", "P")
                .whereEqualTo("passengerId",myUserId)
                .get()
                .addOnSuccessListener { documents->
                    val ratings: MutableList<Double> = ArrayList()
                    for(document in documents){
                        ratings.add(document.data["score"] as Double)
                    }
                    val avgRating = ratings.average()
                    passengerRatingB.rating = avgRating.toFloat()
                    passengerListButton.setOnClickListener {
                        val action = ShowProfileFragmentDirections.actionNavShowProfileToReviewListFragment(myUserId,"P",getString(R.string.title_PassengerReviewList))
                        findNavController().navigate(action)
                    }
                }
        }

        userId = currentUser?.uid.toString()
        if (args.userId != "\"\"") {
            userId = args.userId
        }

        //set image reference for the firebase storage
        val imageRef = Firebase.storage.reference.child("users/$userId.jpg")
        //get instance of the firebase db and actual user information

        val users = db.collection("users")
        val user = users.document(userId)

        //set view element with data contained in db
        user.addSnapshotListener { value, error ->
            if (error != null) throw error
            if (value != null) {
                name.text = (value["fullName"]).toString()
                when {
                    (value["gender"]).toString() == "Male" -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
                            genderIcon.setImageDrawable(requireContext().getDrawable(R.drawable.outline_male_24))
                        } else{
                            genderIcon.setImageDrawable(resources.getDrawable(R.drawable.outline_male_24))
                        }
                    }
                    (value["gender"]).toString() == "Female" -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
                            genderIcon.setImageDrawable(requireContext().getDrawable(R.drawable.outline_female_24))
                        } else{
                            genderIcon.setImageDrawable(resources.getDrawable(R.drawable.outline_female_24))
                        }
                    }
                }
                gender.text = (value["gender"]).toString()
                nickname.text = (value["nickname"]).toString()
                email.text = (value["email"]).toString()
                location.text = (value["location"]).toString()
                car.text = (value["car"]).toString()
                dateOfBirth.text = (value["dateOfBirth"]).toString()
            }
        }

        // set image, if no image is set it use the default, otherwise get from the db the image and save it on a file
        imageRef.getBytes(1024 * 1024).addOnSuccessListener {
            val byteArray = it
            val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            profileImage.setImageBitmap(imageBitmap)
            // ONCE IMAGE HAS LOADED
            if (args.fromBookedUsers == 0) {
                setHasOptionsMenu(true)  // IF IT'S YOUR PROFILE, YOU CAN EDIT
            } else {
                setHasOptionsMenu(false) // IF IT'S NOT YOUR PROFILE, YOU CAN'T EDIT
            }
        }.addOnFailureListener {
            profileImage.setImageResource(R.drawable.default_user_image)
            profileImage.imageAlpha = 255
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (args.fromBookedUsers != 0) {
            (activity as DashboardActivity).topLevelFragment(setOf(
                R.id.nav_trip_list,
                R.id.others_nav_trip_list,
                R.id.nav_trip_of_interest_list,
                R.id.nav_bought_trip_list)
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.edit_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.edit -> {
                editProfile()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun editProfile() {
        val nameSafe = name.text.toString()
        val genderSafe = gender.text.toString()
        val nicknameSafe = nickname.text.toString()
        val emailSafe = email.text.toString()
        val locationSafe = location.text.toString()
        val carSafe = car.text.toString()
        val dateOfBirthSafe = dateOfBirth.text.toString()
        val bitmap =
            profileImage.drawable.toBitmap()
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()

        //navigate to edit profile with the user information
        val userSafe = User(
            nameSafe,
            genderSafe,
            nicknameSafe,
            emailSafe,
            locationSafe,
            carSafe,
            dateOfBirthSafe,
            byteArray
        )
        val action = ShowProfileFragmentDirections.actionShowProfileFragmentToEditProfileFragment(
            userSafe
        )
        findNavController().navigate(action)
    }
}

data class User(
    val name: String,
    val gender: String,
    val nickname: String,
    val email: String,
    val location: String,
    val car: String,
    val dateOfBirth: String,
    val profileImage: ByteArray?,
    val userId: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.createByteArray()!!,
        parcel.readString().toString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(gender)
        parcel.writeString(nickname)
        parcel.writeString(email)
        parcel.writeString(location)
        parcel.writeString(car)
        parcel.writeString(dateOfBirth)
        parcel.writeByteArray(profileImage)
        parcel.writeString(userId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<User> {
        override fun createFromParcel(parcel: Parcel): User {
            return User(parcel)
        }

        override fun newArray(size: Int): Array<User?> {
            return arrayOfNulls(size)
        }
    }
}