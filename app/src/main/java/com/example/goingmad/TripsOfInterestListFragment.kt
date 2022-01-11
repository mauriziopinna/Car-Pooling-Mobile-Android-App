package com.example.goingmad

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.lang.ClassCastException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class TripsOfInterestListFragment : Fragment(R.layout.fragment_trip_of_interest_list) {

    private lateinit var rv: RecyclerView

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv = view.findViewById<RecyclerView>(R.id.InterestTripsRecyclerView)
        trips.clear()
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = InterestedTripAdapter(trips, this)
        val mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        val userId = currentUser?.uid.toString()
        val db = Firebase.firestore
        val settings = firestoreSettings {
            isPersistenceEnabled = true
        }
        db.firestoreSettings = settings
        db.collection("trips")
            .whereArrayContains("interestedUsers", userId)
            .addSnapshotListener { result, error ->
                if (error != null) throw error
                if (result != null) {
                    trips.clear()
                    for (document in result) {
                        val localDateFlag = LocalDateTime.parse(
                            document["departureTime"].toString(),
                            DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm")
                        ).isAfter(LocalDateTime.now())
                        val localTripEnded = (document["tripEnded"]) as Boolean
                        if(!localTripEnded && localDateFlag) {
                            getTrip(document)
                        }
                    }
                    if (trips.size == 0) {
                        showAlert()
                    } else {
                        hideAlert()
                    }
                    rv.adapter?.notifyDataSetChanged()
                }
            }
    }

    fun getTrip(document: QueryDocumentSnapshot) {
        val arrayList = document["intermediateStops"] as ArrayList<*>
        tripArrayList.clear()
        for (stop in arrayList) {
            tripArrayList.add(stop.toString())
        }

        var priceDouble:Double? = null
        var priceLong:Long? = null
        val newTrip:Trip

        try {
            priceDouble = document["price"] as Double
        } catch (e: ClassCastException){
            priceLong = document["price"] as Long
        }

        if(priceDouble != null) {
            newTrip = Trip(
                document.id,
                document["ownerId"] as String,
                null,
                document["departureLocation"] as String,
                document["arrivalLocation"] as String,
                document["departureTime"] as String,
                document["tripDuration"] as String,
                document["seats"] as Long,
                priceDouble,
                document["description"] as String,
                tripArrayList
            )
        } else{
            newTrip = Trip(
                document.id,
                document["ownerId"] as String,
                null,
                document["departureLocation"] as String,
                document["arrivalLocation"] as String,
                document["departureTime"] as String,
                document["tripDuration"] as String,
                document["seats"] as Long,
                priceLong!!.toDouble(),
                document["description"] as String,
                tripArrayList
            )
        }

        trips.add(newTrip)
    }

    //show alert textview and alert icon
    fun showAlert() {
        val alertText = view?.findViewById<TextView>(R.id.InterestAlertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.InterestAlertIcon)
        if (alertText != null) {
            alertText.visibility = View.VISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.VISIBLE
        }
    }

    //hide alert textview and alert icon
    private fun hideAlert() {
        val alertText = view?.findViewById<TextView>(R.id.InterestAlertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.InterestAlertIcon)
        if (alertText != null) {
            alertText.visibility = View.INVISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.INVISIBLE
        }
    }
}

class InterestedTripAdapter(val trips: MutableList<Trip>, private val parentFrag: TripsOfInterestListFragment) :
    RecyclerView.Adapter<InterestedTripAdapter.TripViewHolder>() {
    var type: Int = 0

    class TripViewHolder(v: View, private val parentFrag: TripsOfInterestListFragment) :
        RecyclerView.ViewHolder(v) {
        val carImage =
            v.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.cardCarImage)
        val departureLocation = v.findViewById<TextView>(R.id.cardDepartureLocation)
        val arrivalLocation = v.findViewById<TextView>(R.id.cardArrivalLocation)
        val departureDateHour = v.findViewById<TextView>(R.id.cardDateHour)
        val price = v.findViewById<TextView>(R.id.cardPrice)
        val cardEditButton = v.findViewById<Button>(R.id.cardEditButton)
        val card = v.findViewById<MaterialCardView>(R.id.card)
        var byteArray: ByteArray? = null


        fun bind(trip: Trip) {
            val imageRef = Firebase.storage.reference.child("trips/${trip.tripId}.jpg")
            val localFile = File.createTempFile("carImage", "jpg")
            imageRef.getFile(localFile)
                .addOnSuccessListener { // if manage to download image from database
                    byteArray = localFile.readBytes()
                    val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
                    carImage.setImageBitmap(imageBitmap)
                    trip.carImageByteArray = byteArray
                }


            departureLocation.text = trip.departureLocation
            arrivalLocation.text = trip.arrivalLocation
            departureDateHour.text = trip.departureDateHour
            price.text = trip.price.toString()
            card.setOnClickListener {
                val action = TripsOfInterestListFragmentDirections.actionTripsOfInterestListFragmentToNavTripDetails(
                    2, // FROM TRIPS OF INTEREST
                    trip.tripId
                ) //tripListType 0: from OUR trips
                parentFrag.findNavController().navigate(action)
            }

            cardEditButton.visibility = View.INVISIBLE

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = inflater.inflate(R.layout.card, parent, false)
        return TripViewHolder(layout, parentFrag)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount(): Int {
        return trips.size
    }
}