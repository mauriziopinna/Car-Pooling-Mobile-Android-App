package com.example.goingmad

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ClassCastException

var trips = mutableListOf<Trip>()
var tripArrayList = mutableListOf<String>()

class TripListFragment : Fragment(R.layout.fragment_trip_list) {
    private lateinit var rv: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fabButtonAddTrip = view.findViewById<FloatingActionButton>(R.id.floatingActionButton)
        rv = view.findViewById<RecyclerView>(R.id.TripsRecyclerView)
        trips.clear()
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = TripAdapter(trips, this)
        val mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        val userId = currentUser?.uid.toString()
        val bottomNavigationView = view.findViewById<BottomNavigationView>(R.id.bottomYourTrips)
        val db = Firebase.firestore
        val settings = firestoreSettings {
            isPersistenceEnabled = true
        }
        val firstIt = bottomNavigationView.menu.findItem(R.id.toDoTrips)
        firstIt.isChecked = true
        db.firestoreSettings = settings
            db.collection("trips")
                .whereEqualTo("ownerId", userId).whereEqualTo("tripEnded",true)
                .get().addOnSuccessListener{ result ->
                    if (result != null) {
                        val reviewed = mutableListOf<String>()
                        for (res in result) {
                            val arrayList = res["passengersReviewed"] as ArrayList<*>?
                            val nReviewed = (arrayList?.size ?: 0).toLong()
                            val booked = res["bookedUsers"] as ArrayList<*>?
                            val nBooked = (booked?.size ?: 0).toLong()
                            if (nBooked > nReviewed)
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




            db.collection("trips")
                .whereEqualTo("ownerId", userId).whereEqualTo("tripEnded", false)
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
        fabButtonAddTrip.setOnClickListener {
            val imageBitmap =
                AppCompatResources.getDrawable(requireContext(), R.drawable.default_car_image)
                    ?.toBitmap()
            val stream = ByteArrayOutputStream()
            imageBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val byteArray = stream.toByteArray()
            val trip = Trip("", "", byteArray, "", "", "", "", 0, 0.0, "", mutableListOf<String>())
            val action = TripListFragmentDirections.actionTripListFragmentToNavTripEdit(0, trip)

            findNavController().navigate(action)
        }

        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            trips.clear()
            when (item.itemId) {
                R.id.toDoTrips -> {
                    fabButtonAddTrip.visibility=View.VISIBLE
                    db.collection("trips")
                        .whereEqualTo("ownerId", userId).whereEqualTo("tripEnded", false)
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
                    //rv.layoutManager = LinearLayoutManager(this.context)
                   // rv.adapter = TripAdapter(trips, this)
                    true
                }
                R.id.toReviewTrips -> {
                    fabButtonAddTrip.visibility=View.INVISIBLE
                    db.collection("trips")
                        .whereEqualTo("ownerId", userId).whereEqualTo("tripEnded",true)
                        .addSnapshotListener { result, err ->
                            if (err != null) throw  err
                            if (result != null) {
                                val reviewed = mutableListOf<String>()
                                for (res in result) {
                                    val arrayList = res["passengersReviewed"] as ArrayList<*>?
                                    val nReviewed = (arrayList?.size ?: 0).toLong()
                                    val booked = res["bookedUsers"] as ArrayList<*>?
                                    val nBooked = (booked?.size ?: 0).toLong()
                                    if (nBooked > nReviewed)
                                        reviewed.add(res.id)
                                }
                                val badge = bottomNavigationView.getOrCreateBadge(R.id.toReviewTrips)
                                if (reviewed.size == 0) {
                                    reviewed.add("emptyArray")
                                    badge.isVisible = false
                                }else{
                                    badge.isVisible = true
                                    badge.number = reviewed.size
                                }

                                db.collection("trips").whereIn(FieldPath.documentId(), reviewed)
                                    .whereEqualTo("ownerId", userId).whereEqualTo("tripEnded", true)
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
                   // rv.layoutManager = LinearLayoutManager(this.context)
                  //  rv.adapter = TripAdapter(trips, this)
                    true
                }
                R.id.oldTrips -> {
                    fabButtonAddTrip.visibility=View.INVISIBLE
                    db.collection("trips")
                        .whereEqualTo("ownerId", userId).whereEqualTo("tripEnded",true)
                        .addSnapshotListener { result, err ->
                            if (err != null) throw  err
                            if (result != null) {
                                val reviewed = mutableListOf<String>()
                                for (res in result) {
                                    val arrayList = res["passengersReviewed"] as ArrayList<*>?
                                    val nReviewed = (arrayList?.size ?: 0).toLong()
                                    val booked = res["bookedUsers"] as ArrayList<*>?
                                    val nBooked = (booked?.size ?: 0).toLong()
                                    if (nBooked == nReviewed)
                                        reviewed.add(res.id)
                                }
                                if (reviewed.size == 0)
                                    reviewed.add("emptyArray")
                                db.collection("trips").whereIn(FieldPath.documentId(), reviewed)
                                    .whereEqualTo("ownerId", userId).whereEqualTo("tripEnded", true)
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
                   // rv.layoutManager = LinearLayoutManager(this.context)
                   // rv.adapter = TripAdapter(trips, this)
                    // Respond to navigation item 2 click
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
        val alertText = view?.findViewById<TextView>(R.id.alertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.alertIcon)
        if (alertText != null) {
            alertText.visibility = View.VISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.VISIBLE
        }
    }

    //hide alert textview and alert icon
    fun hideAlert() {
        val alertText = view?.findViewById<TextView>(R.id.alertText)
        val alertIcon = view?.findViewById<ImageView>(R.id.alertIcon)
        if (alertText != null) {
            alertText.visibility = View.INVISIBLE
        }
        if (alertIcon != null) {
            alertIcon.visibility = View.INVISIBLE
        }
    }
}


data class Trip(
    val tripId: String,
    val ownerId: String,
    var carImageByteArray: ByteArray?,
    val departureLocation: String,
    val arrivalLocation: String,
    val departureDateHour: String,
    val duration: String,
    val seats: Long,
    val price: Double,
    val description: String,
    val intermediateStops: MutableList<String>,
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.createByteArray()!!,
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readLong(),
        parcel.readDouble(),
        parcel.readString().toString(),
        parcel.createStringArrayList()!!,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(tripId)
        parcel.writeString(ownerId)
        parcel.writeByteArray(carImageByteArray)
        parcel.writeString(departureLocation)
        parcel.writeString(arrivalLocation)
        parcel.writeString(departureDateHour)
        parcel.writeString(duration)
        parcel.writeLong(seats)
        parcel.writeDouble(price)
        parcel.writeString(description)
        parcel.writeStringList(intermediateStops)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Trip> {
        override fun createFromParcel(parcel: Parcel): Trip {
            return Trip(parcel)
        }

        override fun newArray(size: Int): Array<Trip?> {
            return arrayOfNulls(size)
        }
    }
}

class TripAdapter(val trips: MutableList<Trip>, private val parentFrag: TripListFragment) :
    RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    class TripViewHolder(v: View, private val parentFrag: TripListFragment) :
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
                val action = TripListFragmentDirections.actionTripListFragmentToNavTripDetails(
                    tripListType = 0, // 0: from OUR trips
                    tripId = trip.tripId
                )
                parentFrag.findNavController().navigate(action)
            }

            val db = Firebase.firestore
            db.collection("trips").document(trip.tripId).addSnapshotListener { value, error ->
                if (error != null) throw error
                if (value != null) {
                    val tripEnded = value["tripEnded"] as Boolean
                    if (!tripEnded) { // IF TRIP HASN'T ENDED, YOU CAN EDIT TRIP
                        cardEditButton.setOnClickListener {
                            val action =
                                TripListFragmentDirections.actionTripListFragmentToNavTripEdit(
                                    0,
                                    trip
                                )
                            parentFrag.findNavController().navigate(action)
                        }
                        cardEditButton.visibility = View.VISIBLE
                    } else { // IF TRIP HAS ENDED, YOU CANNOT EDIT TRIP
                        cardEditButton.visibility = View.GONE
                    }
                }
            }
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