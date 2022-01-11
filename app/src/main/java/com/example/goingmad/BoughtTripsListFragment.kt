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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File
import java.lang.ClassCastException


class BoughtTripsListFragment : Fragment(R.layout.fragment_bought_trips_list) {

    private lateinit var rv: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv = view.findViewById(R.id.BoughtTripsRecyclerView)
        trips.clear()
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = BoughtTripAdapter(trips, this)
        val bottomNavigationView = view.findViewById<BottomNavigationView>(R.id.bottomYourTrips)
        val mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        val userId = currentUser?.uid.toString()
        val db = Firebase.firestore
        val settings = firestoreSettings {
            isPersistenceEnabled = true
        }
        val firstIt = bottomNavigationView.menu.findItem(R.id.toDoTrips)
        firstIt.isChecked = true
        db.collection("trips")
            .whereArrayContains("bookedUsers", userId).whereEqualTo("tripEnded",true)
            .addSnapshotListener { result, err ->
                if (err != null) throw  err
                if (result != null) {
                    val reviewed = mutableListOf<String>()
                    for (res in result) {
                        val arrayList = res["hasReviewedDriver"] as ArrayList<*>?
                        val hasReviewed = arrayList?.contains(userId)?:false
                        if (!hasReviewed)
                            reviewed.add(res.id)
                    }
                    val badge = bottomNavigationView.getOrCreateBadge(R.id.toReviewTrips)
                    if (reviewed.size > 0){
                        badge.isVisible = true
                        badge.number = reviewed.size
                    }else{
                        badge.isVisible = false
                    }
                }
            }

        db.firestoreSettings = settings
        db.collection("trips")
            .whereArrayContains("bookedUsers", userId).whereEqualTo("tripEnded", false)
            .addSnapshotListener { result, error ->
                if (error != null) throw error
                if (result != null) {
                    trips.clear()
                    for (document in result) {
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

        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            trips.clear()
            when (item.itemId) {
                R.id.toDoTrips -> {
                    db.collection("trips")
                        .whereArrayContains("bookedUsers", userId).whereEqualTo("tripEnded", false)
                        .addSnapshotListener { result, error ->
                            if (error != null) throw error
                            if (result != null) {
                                trips.clear()
                                for (document in result) {
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
                    true
                }
                R.id.toReviewTrips -> {
                    db.collection("trips")
                        .whereArrayContains("bookedUsers", userId).whereEqualTo("tripEnded",true)
                        .addSnapshotListener { result, err ->
                            if (err != null) throw  err
                            if (result != null) {
                                val reviewed = mutableListOf<String>()
                                for (res in result) {
                                    val arrayList = res["hasReviewedDriver"] as ArrayList<*>?
                                    val hasReviewed = arrayList?.contains(userId)?:false
                                    if (!hasReviewed)
                                        reviewed.add(res.id)
                                }
                                val badge = bottomNavigationView.getOrCreateBadge(R.id.toReviewTrips)
                                if (reviewed.size == 0){
                                    reviewed.add("emptyArray")
                                    badge.isVisible = false
                                }else{badge.isVisible = true
                                    badge.number = reviewed.size
                                }



                                db.collection("trips").whereIn(FieldPath.documentId(), reviewed)
                                    .addSnapshotListener { arr, error ->
                                        if (error != null) throw error
                                        if (arr != null) {
                                            trips.clear()
                                            for (document in arr) {
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
                    true
                }
                R.id.oldTrips -> {
                    db.collection("trips").whereArrayContains("bookedUsers", userId)
                        .whereEqualTo("tripEnded",true)
                        .addSnapshotListener { result, err ->
                            if (err != null) throw  err
                            if (result != null) {
                                val reviewed = mutableListOf<String>()
                                for (res in result) {
                                    val arrayList = res["hasReviewedDriver"] as ArrayList<*>?
                                    val hasReviewed = arrayList?.contains(userId)?:false
                                    if (hasReviewed)
                                        reviewed.add(res.id)
                                }
                                if (reviewed.size == 0)
                                    reviewed.add("emptyArray")
                                db.collection("trips").whereIn(FieldPath.documentId(), reviewed)
                                    .addSnapshotListener { arr, error ->
                                        if (error != null) throw error
                                        if (arr != null) {
                                            trips.clear()
                                            for (document in arr) {
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
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        val bottomNavigationView = view?.findViewById<BottomNavigationView>(R.id.bottomYourTrips)
        val firstIt = bottomNavigationView?.menu?.findItem(R.id.toDoTrips)
        firstIt?.isChecked = true
        super.onResume()
    }
    fun getTrip(document: QueryDocumentSnapshot) {
        val arrayList = document["intermediateStops"] as ArrayList<*>?
        tripArrayList.clear()
        if (arrayList != null) {
            for (stop in arrayList) {
                tripArrayList.add(stop.toString())
            }
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

        val alertText = view?.findViewById<TextView>(R.id.BoughtAlertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.BoughtAlertIcon)
        if (alertText != null) {
            alertText.visibility = View.VISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.VISIBLE
        }

    }

    //hide alert textview and alert icon
    fun hideAlert() {
        val alertText = view?.findViewById<TextView>(R.id.BoughtAlertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.BoughtAlertIcon)
        if (alertText != null) {
            alertText.visibility = View.INVISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.INVISIBLE
        }

    }

}

class BoughtTripAdapter(val trips: MutableList<Trip>, private val parentFrag: BoughtTripsListFragment) :
    RecyclerView.Adapter<BoughtTripAdapter.TripViewHolder>() {
    var type: Int = 0

    class TripViewHolder(v: View, private val parentFrag: BoughtTripsListFragment) :
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
        private val tripStatus = v.findViewById<TextView>(R.id.tripStatus)



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

            val db = Firebase.firestore
            val trips = db.collection("trips")
            val tripRef = trips.document(trip.tripId)
            tripRef.addSnapshotListener { value, error ->
                if (error != null) throw error
                if (value != null) {
                    val bookedUsers = value["bookedUsers"] as ArrayList<*>
                    val hasReviewedDriver = (value["hasReviewedDriver"]) as ArrayList<*>
                    val mAuth = FirebaseAuth.getInstance()
                    val currentUser = mAuth.currentUser
                    if (bookedUsers.contains(currentUser?.uid.toString())) { // if the user has participated in trip
                        if (value["tripEnded"] as Boolean) { // if trip has ended
                            if (!hasReviewedDriver.contains(currentUser?.uid.toString())) { // if the user has not reviewed the driver yet
                                //tripStatus.text = "You need to review the driver"
                                tripStatus.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
            departureLocation.text = trip.departureLocation
            arrivalLocation.text = trip.arrivalLocation
            departureDateHour.text = trip.departureDateHour
            price.text = trip.price.toString()
            val type = 1
            card.setOnClickListener {
                val action = BoughtTripsListFragmentDirections.actionNavBoughtTripListToNavTripDetails(
                    type,
                    trip.tripId
                ) //tripListType 0: from OUR trips
                parentFrag.findNavController().navigate(action)
            }

            cardEditButton.visibility = View.GONE

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