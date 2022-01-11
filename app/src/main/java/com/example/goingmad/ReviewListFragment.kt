package com.example.goingmad

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File

var reviews = mutableListOf<Review>()

class ReviewListFragment : Fragment(R.layout.fragment_review_list) {
    private lateinit var rv : RecyclerView

    val args: ReviewListFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv = view.findViewById<RecyclerView>(R.id.reviewsrecyclerView)
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = ReviewAdapter(reviews, this)
        val db = Firebase.firestore
        reviews.clear()
        if(args.reviewType == "D"){
            db.collection("reviews")
                .whereEqualTo("type", args.reviewType.toString())
                .whereEqualTo("driverId", args.userId.toString())
                .get()
                .addOnSuccessListener { documents ->
                    for(document in documents){
                        val newReview = Review(
                            document.data["passengerId"].toString(),
                            document.data["reviewerName"].toString(),
                            document.data["description"].toString(),
                            document.data["score"] as Double
                        )
                        reviews.add(newReview)
                    }
                    if (reviews.size == 0) {
                        showAlert()
                    } else {
                        hideAlert()
                    }
                    rv.adapter?.notifyDataSetChanged()
                }
        }
        else if(args.reviewType == "P"){
            db.collection("reviews")
                .whereEqualTo("type", args.reviewType.toString())
                .whereEqualTo("passengerId", args.userId.toString())
                .get()
                .addOnSuccessListener { documents ->
                    for(document in documents){
                        val newReview = Review(
                            document.data["driverId"].toString(),
                            document.data["reviewerName"].toString(),
                            document.data["description"].toString(),
                            document.data["score"] as Double
                        )
                        reviews.add(newReview)
                    }
                    if (reviews.size == 0) {
                        showAlert()
                    } else {
                        hideAlert()
                    }
                    rv.adapter?.notifyDataSetChanged()
                }
        }
    }
    fun showAlert() {
        val alertText = view?.findViewById<TextView>(R.id.reviewAlertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.reviewAlertIcon)
        if (alertText != null) {
            alertText.visibility = View.VISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.VISIBLE
        }
    }

    //hide alert textview and alert icon
    private fun hideAlert() {
        val alertText = view?.findViewById<TextView>(R.id.reviewAlertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.reviewAlertIcon)
        if (alertText != null) {
            alertText.visibility = View.INVISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.INVISIBLE
        }
    }
}

data class Review(
    val reviewerId: String?,
    val reviewerName: String,
    val reviewText: String,
    val reviewScore: Double
)

class ReviewAdapter(val reviews: MutableList<Review>, private val parentFrag: ReviewListFragment):
    RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>(){
    class ReviewViewHolder(v:View, private val parentFrag: ReviewListFragment):RecyclerView.ViewHolder(v){
        val reviewerName = v.findViewById<TextView>(R.id.reviewerName)
        val reviewerPicture = v.findViewById<CircleImageView>(R.id.reviewerPicture)
        val ratingBar = v.findViewById<RatingBar>(R.id.reviewRating)
        val reviewText = v.findViewById<TextView>(R.id.reviewText)
        var byteArray: ByteArray? = null

        fun bind(review:Review){
            val imageRef = Firebase.storage.reference.child("users/${review.reviewerId}.jpg")
            downloadAndSetImage(imageRef, reviewerPicture)

            reviewerName.text = review.reviewerName
            reviewText.text = review.reviewText
            ratingBar.rating = review.reviewScore.toFloat()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val layout = layoutInflater.inflate(R.layout.review_card, parent, false)
        return ReviewViewHolder(layout, parentFrag)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(reviews[position])
    }

    override fun getItemCount(): Int {
        return reviews.size
    }
}

fun downloadAndSetImage(imageRef:StorageReference, pictureView:CircleImageView){
    val localFile = File.createTempFile("tempImage", "jpg")
    imageRef.getFile(localFile)
        .addOnSuccessListener { // if manage to download image from database
            val byteArray = localFile.readBytes()
            val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            pictureView.setImageBitmap(imageBitmap)
        }
}