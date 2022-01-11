package com.example.goingmad

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import de.hdodenhof.circleimageview.CircleImageView

class ReviewFragment : Fragment(R.layout.fragment_review) {
    val args : ReviewFragmentArgs by navArgs()
    private lateinit var revTextView : TextView
    private lateinit var reviewedImage : CircleImageView
    private lateinit var reviewedName : TextView
    private lateinit var ratingBarReview : RatingBar
    private lateinit var reviewDescTextView : TextInputLayout
    private lateinit var reviewButton : Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        revTextView = view.findViewById<TextView>(R.id.reviewTextView)
        reviewedImage = view.findViewById<CircleImageView>(R.id.reviewedPicture)
        reviewedName = view.findViewById<TextView>(R.id.reviewedName)
        ratingBarReview = view.findViewById<RatingBar>(R.id.ratingBar)
        reviewDescTextView = view.findViewById<TextInputLayout>(R.id.reviewText)
        reviewButton = view.findViewById<Button>(R.id.reviewButton)
        val db = FirebaseFirestore.getInstance()

        //arriving from tripDetails, so a passenger wants to review a driver(so a trip)
        if(args.fromTripDetails && !args.fromShowProfile){
            val imageRef = Firebase.storage.reference.child("users/${args.driverId}.jpg")
            downloadAndSetImage(imageRef, reviewedImage)
            reviewedName.text = args.reviewedName
            revTextView.text = getString(R.string.driverReviewPrompt)
            reviewButton.setOnClickListener {
                val review = hashMapOf(
                    "type" to "D",
                    "driverId" to args.driverId.toString(),
                    "tripId" to args.tripId,
                    "passengerId" to args.passengerId.toString(),
                    "score" to ratingBarReview.rating,
                    "description" to reviewDescTextView.editText?.text.toString(),
                    "reviewerName" to args.reviewerName.toString(),
                    "reviewedName" to args.reviewedName.toString()
                )
                db.collection("reviews")
                    .add(review)
                    .addOnSuccessListener { documentReference->
                        Log.d("Firebase", "DocumentSnapshot written with ID: ${documentReference.id}")
                    }
                    .addOnFailureListener { e->
                        Log.w("Firebase", "Error adding document", e)
                    }
                db.collection("trips").document(args.tripId.toString())
                    .update("hasReviewedDriver", FieldValue.arrayUnion(args.passengerId.toString()))
                    .addOnSuccessListener {
                        Log.d("Firebase", "driverRev flag setted correctly")
                    }
                    .addOnFailureListener { e->
                        Log.w("Firebase", "Error setting driverrev flag", e)
                    }
                findNavController().popBackStack()
            }

        }
        //arriving from showProfile, so a driver wants to review a passenger
        if(!args.fromTripDetails && args.fromShowProfile){
            val imageRef = Firebase.storage.reference.child("users/${args.passengerId}.jpg")
            downloadAndSetImage(imageRef, reviewedImage)
            reviewedName.text = args.reviewedName
            revTextView.text = getString(R.string.passengerReviewPrompt)
            db.collection("users")
            .document(args.driverId.toString())
            .get().addOnSuccessListener{
                val reviewerName = it["fullName"] as String
                    reviewButton.setOnClickListener {
                        val review = hashMapOf(
                            "type" to "P",
                            "tripId" to args.tripId,
                            "driverId" to args.driverId.toString(),
                            "passengerId" to args.passengerId.toString(),
                            "score" to ratingBarReview.rating,
                            "description" to reviewDescTextView.editText?.text.toString(),
                            "reviewerName" to reviewerName,
                            "reviewedName" to args.reviewedName,
                        )
                        db.collection("reviews")
                            .add(review)
                            .addOnSuccessListener { documentReference->
                                Log.d("Firebaseaddreview", "DocumentSnapshot written with ID: ${documentReference.id}")
                            }
                            .addOnFailureListener { e->
                                Log.w("Firebaseaddreview", "Error adding document", e)
                            }

                        db.collection("trips").document(args.tripId.toString())
                            .update("passengersReviewed", FieldValue.arrayUnion(args.passengerId.toString()))
                            .addOnSuccessListener {
                                Log.d("Firebase", "passengerRev set correctly")
                            }
                            .addOnFailureListener { e->
                                Log.w("Firebase", "Error setting passengerRev flag", e)
                            }
                        findNavController().popBackStack(R.id.bookedUsersFragment, false)
                    }
            }

        }
    }
}