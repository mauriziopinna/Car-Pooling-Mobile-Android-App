package com.example.goingmad

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.util.*

var users = mutableListOf<User>()

private lateinit var registration:ListenerRegistration

class BookedUsersFragment : Fragment(R.layout.fragment_booked_users) {
    val args: BookedUsersFragmentArgs by navArgs()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tripId = args.tripId

        val rv = view.findViewById<RecyclerView>(R.id.bookedRecyclerView)
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = UserAdapter(users, this, tripId, args.field)

        val db = FirebaseFirestore.getInstance()
        val trips = db.collection("trips")
        val tripDoc = trips.document(tripId)

        tripDoc.addSnapshotListener { value, error ->
            if (error != null) throw error

            if (value != null) {
                if (value.data != null) {
                    val usersId = value.data?.get(args.field) as ArrayList<*>
                    val alertTextBooked = view.findViewById<TextView>(R.id.alertTextNoBooked)
                    val alertTextInterested =
                        view.findViewById<TextView>(R.id.alertTextNoInterested)
                    val alertIcon = view.findViewById<ImageView>(R.id.alertIconBooked)

                    // necessary to not make things explode when there are no booked users
                    if (usersId.isNotEmpty()) {
                        alertTextBooked.visibility = View.INVISIBLE
                        alertTextInterested.visibility = View.INVISIBLE
                        alertIcon.visibility = View.INVISIBLE
                        val dbRef = db.collection("users").whereIn(FieldPath.documentId(), usersId)
                        registration = dbRef.addSnapshotListener { result, error2 ->
                                if (error2 != null) throw error2
                                if (result != null) {
                                    users.clear()
                                    for (document in result) {
                                        val name = (document["fullName"]).toString()
                                        val gender = (document["gender"]).toString()
                                        val nickname = (document["nickname"]).toString()
                                        val email = (document["email"]).toString()
                                        val location = (document["location"]).toString()
                                        val car = (document["car"]).toString()
                                        val dateOfBirth = (document["dateOfBirth"]).toString()
                                        val bookedUser = User(
                                            name,
                                            gender,
                                            nickname,
                                            email,
                                            location,
                                            car,
                                            dateOfBirth,
                                            null,
                                            document.id
                                        )
                                        users.add(bookedUser)
                                    }
                                    rv.adapter?.notifyDataSetChanged()
                                }
                            }
                    } else {
                        users.clear()
                        if (args.field == "bookedUsers") {
                            if (alertTextBooked != null) {
                                alertTextBooked.visibility = View.VISIBLE
                            }
                        } else {
                            if (alertTextInterested != null) {
                                alertTextInterested.visibility = View.VISIBLE
                            }
                        }
                        if (alertIcon != null) {
                            alertIcon.visibility = View.VISIBLE
                        }
                        rv.adapter?.notifyDataSetChanged()
                    }
                }
            }
        }

    }
}

class UserAdapter(
    val users: MutableList<User>,
    val parentFrag: BookedUsersFragment,
    val tripId: String,
    val booked: String
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {
    class UserViewHolder(
        v: View,
        val parentFrag: BookedUsersFragment,
        val tripId: String,
        val booked: String
    ) : RecyclerView.ViewHolder(v) {
        val userCard = v.findViewById<MaterialCardView>(R.id.userCard)
        val userImage =
            v.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.cardUserCarImage)
        val fullName = v.findViewById<TextView>(R.id.cardFullName)
        val nickname = v.findViewById<TextView>(R.id.cardNickname)
        val acceptButton = v.findViewById<Button>(R.id.cardAcceptButton)
        val view = v


        fun bind(user: User) {
            val imageRef = Firebase.storage.reference.child("users/${user.userId}.jpg")
            imageRef.getBytes(1024 * 1024)
                .addOnSuccessListener { // if manage to download image from database
                    val byteArray = it
                    val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
                    userImage.setImageBitmap(imageBitmap)
                }.addOnFailureListener { // if fail to download image
                    val imageBitmap = AppCompatResources.getDrawable(
                        parentFrag.requireContext(),
                        R.drawable.default_user_image
                    )?.toBitmap()
                    userImage.setImageBitmap(imageBitmap)
                }

            if (booked == "bookedUsers") {
                acceptButton.visibility = View.INVISIBLE
            } else {
                acceptButton.setOnClickListener {
                    // create reference to trip opened in details
                    val ourDb = FirebaseFirestore.getInstance()
                    val tripRef = ourDb.collection("trips").document(tripId)

                    // obtain userId of currentUser

                    tripRef.get().addOnSuccessListener {
                        val bookedUsers = it.data?.get("bookedUsers") as ArrayList<*>
                        var insert = true
                        if (bookedUsers.size >= (it.data?.get("seats") as Long)) { // IF MAX SIZE IS REACHED, DO NOT ADD USER
                            insert =
                                false    //SINCE ARE ONLY INTERESTED USERS SHOULD NO MORE NEED THAT
                            Snackbar.make(
                                view,
                                (R.string.no_seats_available),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        if (bookedUsers.contains(user.userId)) { // IF USER ALREADY BOOKED, DO NOT ADD USER
                            insert = false
                            Snackbar.make(
                                view,
                                (R.string.user_already_booked),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        if (insert) {
                            tripRef.update("bookedUsers", FieldValue.arrayUnion(user.userId))
                                .addOnSuccessListener {
                                    tripRef.update(
                                        "interestedUsers",
                                        FieldValue.arrayRemove(user.userId)
                                    )
                                    if(users.size == 1) {
                                        registration.remove()
                                    }
                                    users.remove(user)
                                }.addOnFailureListener {
                                    Snackbar.make(
                                        view,
                                        R.string.something_wrong,
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            Snackbar.make(view, R.string.correctly_booked, Snackbar.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }

            fullName.text = user.name
            nickname.text = user.nickname
            userCard.setOnClickListener {
                val action =
                    BookedUsersFragmentDirections.actionBookedUsersFragmentToNavShowProfile(1, user.userId, tripId)
                parentFrag.findNavController().navigate(action)

            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = inflater.inflate(R.layout.user_card, parent, false)
        return UserViewHolder(layout, parentFrag, tripId, booked)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int {
        return users.size
    }
}