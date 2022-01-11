package com.example.goingmad

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.transition.Slide
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.text.isDigitsOnly
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.lang.ClassCastException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList


var othersTrips = mutableListOf<Trip>()
var displayTrips = mutableListOf<Trip>()
var intermediateStops = mutableListOf<String>()

class OthersTripListFragment : Fragment(R.layout.fragment_others_trip_list) {
    private lateinit var departureSearch: TextInputEditText
    private lateinit var arrivalSearch: TextInputEditText
    private lateinit var durationSearch: TextInputEditText
    private lateinit var priceSearch: TextInputEditText
    private lateinit var seatsSearch: TextInputEditText
    private lateinit var dateSearch: TextInputEditText
    var cal = Calendar.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        val userId = currentUser?.uid.toString()

        val db = Firebase.firestore
        val settings = firestoreSettings {
            isPersistenceEnabled = true
        }
        val rv = view.findViewById<RecyclerView>(R.id.otherTripsRecyclerView)
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = OthersTripAdapter(othersTrips, this)

        db.firestoreSettings = settings

        db.collection("trips").whereNotEqualTo("ownerId", userId)
            .addSnapshotListener { result, error ->
                if (error != null) throw error
                if (result != null) {
                    othersTrips.removeAll(othersTrips)
                    for (document in result) {
                        val localTripEnded = (document["tripEnded"]) as Boolean
                        val localTripDeleted = (document["tripDeleted"]) as Boolean
                        var localDateFlag = true

                        if(Build.VERSION.SDK_INT >= 26){
                            localDateFlag = LocalDateTime.parse(
                                document["departureTime"].toString(),
                                DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm")
                            ).isAfter(LocalDateTime.now())
                        }

                        if(!localTripEnded && !localTripDeleted && localDateFlag){
                            val newArrayList = document["intermediateStops"] as ArrayList<*>?
                            intermediateStops.clear()
                            if (newArrayList != null) {
                                for (stop in newArrayList) {
                                    intermediateStops.add(stop.toString())
                                }
                            }

                            var priceDouble: Double? = null
                            var priceLong: Long? = null
                            var newTrip: Trip

                            try {
                                priceDouble = document["price"] as Double
                            } catch (e: ClassCastException) {
                                priceLong = document["price"] as Long
                            }

                            if (priceDouble != null) {
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
                                    intermediateStops
                                )
                            } else {
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
                                    intermediateStops
                                )
                            }
                            othersTrips.add(newTrip)
                        }
                    }
                    rv.adapter?.notifyDataSetChanged()
                }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.search_menu, menu)
    }

    private fun search(){
        val inflater:LayoutInflater = activity?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val v= inflater.inflate(R.layout.search_form,null)
        departureSearch= v.findViewById(R.id.departureSearch)
        arrivalSearch = v.findViewById(R.id.arrivalSearch)
        durationSearch= v.findViewById(R.id.durationSearch)
        priceSearch = v.findViewById(R.id.priceSearch)
        seatsSearch= v.findViewById(R.id.seatsSearch)
        dateSearch = v.findViewById(R.id.dateSearch)
        val confirmButton= v.findViewById<Button>(R.id.searchConfirm)
        val cancelButton= v.findViewById<Button>(R.id.searchCancel)

        val popupWindow = PopupWindow(
            v, // Custom view to show in popup window
            LinearLayout.LayoutParams.MATCH_PARENT, // Width of popup window
            LinearLayout.LayoutParams.MATCH_PARENT, // Window height
            true
        )
        popupWindow.showAtLocation(v,Gravity.CENTER_VERTICAL,0,0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.elevation = 10.0F
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            // Create a new slide animation for popup window enter transition
            val slideIn = Slide()
            slideIn.slideEdge = Gravity.TOP
            popupWindow.enterTransition = slideIn

            // Slide animation for popup window exit transition
            val slideOut = Slide()
            slideOut.slideEdge = Gravity.RIGHT
            popupWindow.exitTransition = slideOut

        }

        val today = Calendar.getInstance()
        val dateSetListener =
            DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, monthOfYear)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                val myFormat = "dd/MM/yyyy" // mention the format you need
                val sdf = SimpleDateFormat(myFormat, Locale.US)
                val age = isOfAge(
                    today.get(Calendar.DAY_OF_MONTH),
                    today.get(Calendar.MONTH),
                    today.get(Calendar.YEAR),
                    dayOfMonth,
                    monthOfYear,
                    year
                )
                if (age == 1) {
                    dateSearch.setText(sdf.format(cal.time))
                } else {
                    Snackbar.make(this.requireView(), R.string.future_date, Snackbar.LENGTH_SHORT)
                        .show()
                }
            }

        dateSearch.setOnClickListener {
            DatePickerDialog(
                this.requireContext(), dateSetListener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        val rv = activity?.findViewById<RecyclerView>(R.id.otherTripsRecyclerView)
        rv?.layoutManager = LinearLayoutManager(this.context)
        displayTrips.clear()
        confirmButton.setOnClickListener {
            val depLocSearch = departureSearch.text.toString()
            val arrLocSearch = arrivalSearch.text.toString()
            val tripDurationSearch = durationSearch.text.toString()
            var numSeatsSearch = seatsSearch.text.toString()
            val depTimeSearch = dateSearch.text.toString()
            var priceSearch = priceSearch.text.toString()
            if(numSeatsSearch=="")
                numSeatsSearch="0"
            if(priceSearch=="")
                priceSearch="0"

            othersTrips.forEach {
                var localDateFlag = true

                if(Build.VERSION.SDK_INT >= 26){
                    localDateFlag = LocalDateTime.parse(
                        it.departureDateHour,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm")
                    ).isAfter(LocalDateTime.now())
                }
                if(localDateFlag) {
                    if ((it.arrivalLocation.lowercase(Locale.ROOT).contains(arrLocSearch.lowercase(Locale.ROOT)) && arrLocSearch!="") ||
                        (it.departureLocation.lowercase(Locale.ROOT).contains(depLocSearch.lowercase(Locale.ROOT)) && depLocSearch!="") ||
                        (it.departureDateHour.lowercase(Locale.ROOT).contains(depTimeSearch.lowercase(Locale.ROOT)) && depTimeSearch!="" ) ||
                        (it.price==priceSearch.toDouble() && priceSearch!="") || (it.seats==numSeatsSearch.toLong() && numSeatsSearch!="")
                        || (it.duration.contains(tripDurationSearch.lowercase(Locale.ROOT)) && tripDurationSearch!="")
                    ) {
                        displayTrips.add(it)
                    }
                }
            }
            val alertText = this@OthersTripListFragment.view?.findViewById<TextView>(R.id.alertText)
            val alertIcon = this@OthersTripListFragment.view?.findViewById<ImageView>(R.id.alertIcon)
            if(displayTrips.size==0){
                if (alertText != null) {
                    alertText.visibility = View.VISIBLE
                }
                if (alertIcon != null) {
                    alertIcon.visibility = View.VISIBLE
                }
            }else{
                if (alertText != null) {
                    alertText.visibility = View.INVISIBLE
                }
                if (alertIcon != null) {
                    alertIcon.visibility = View.INVISIBLE
                }
            }
            if(arrLocSearch=="" && depLocSearch=="" && depTimeSearch=="" && tripDurationSearch=="" && priceSearch=="0" && numSeatsSearch=="0" )
                displayTrips.addAll(othersTrips)

            popupWindow.dismiss()
            rv?.adapter = OthersTripAdapter(displayTrips, this@OthersTripListFragment)

        }

        cancelButton.setOnClickListener {
            rv?.adapter = OthersTripAdapter(othersTrips, this@OthersTripListFragment)
            popupWindow.dismiss()
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.search -> {
                search()
                true
            }
            else -> super.onContextItemSelected(item)
        }

    }

    /*override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.search_menu, menu)
        val searchItem = menu.findItem(R.id.search_menu)
        val rv = activity?.findViewById<RecyclerView>(R.id.otherTripsRecyclerView)
        rv?.layoutManager = LinearLayoutManager(this.context)

        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onQueryTextChange(newText: String?): Boolean {
                    displayTrips.clear()
                    if (newText!!.isNotEmpty()) {
                        val search = newText.lowercase(Locale.ROOT)
                        othersTrips.forEach {
                            var localDateFlag = true

                            if(Build.VERSION.SDK_INT >= 26){
                                localDateFlag = LocalDateTime.parse(
                                    it.departureDateHour,
                                    DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm")
                                ).isAfter(LocalDateTime.now())
                            }
                            if(localDateFlag) {
                                if (it.arrivalLocation.lowercase(Locale.ROOT)
                                        .contains(search) || it.departureLocation.lowercase(Locale.ROOT)
                                        .contains(search)
                                    || it.arrivalLocation.lowercase(Locale.ROOT).contains(search)
                                ) {
                                    displayTrips.add(it)
                                }
                            }else if (search.isDigitsOnly()) {
                                if (it.departureDateHour.contains(search) || it.duration.contains(
                                        search
                                    ) || it.price == search.toDouble() || it.seats == search.toLong()
                                ) {
                                    displayTrips.add(it)
                                }
                            }
                        }
                    } else {
                        displayTrips.addAll(othersTrips)
                    }

                    rv?.adapter = OthersTripAdapter(displayTrips, this@OthersTripListFragment)
                    return true
                }

            })
        }
    }*/
}


class OthersTripAdapter(val trips: MutableList<Trip>, private val parentFrag: OthersTripListFragment) :
    RecyclerView.Adapter<OthersTripAdapter.OthersTripViewHolder>() {
    class OthersTripViewHolder(v: View, private val parentFrag: OthersTripListFragment) :
        RecyclerView.ViewHolder(v) {
        private val carImage =
            v.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.cardCarImage)
        private val departureLocation = v.findViewById<TextView>(R.id.cardDepartureLocation)
        private val arrivalLocation = v.findViewById<TextView>(R.id.cardArrivalLocation)
        private val departureDateHour = v.findViewById<TextView>(R.id.cardDateHour)
        private val price = v.findViewById<TextView>(R.id.cardPrice)
        private val cardEditButton = v.findViewById<Button>(R.id.cardEditButton)
        private val card = v.findViewById<MaterialCardView>(R.id.card)

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



            val alertText = parentFrag.view?.findViewById<TextView>(R.id.alertText)
            val alertIcon = parentFrag.view?.findViewById<ImageView>(R.id.alertIcon)
            if (othersTrips.size != 0) {
                if (alertText != null) {
                    alertText.visibility = View.INVISIBLE
                }
                if (alertIcon != null) {
                    alertIcon.visibility = View.INVISIBLE
                }
            }

            departureLocation.text = trip.departureLocation
            arrivalLocation.text = trip.arrivalLocation
            departureDateHour.text = trip.departureDateHour
            price.text = trip.price.toString()
            cardEditButton.visibility = View.GONE
            card.setOnClickListener {
                val action =
                    OthersTripListFragmentDirections.actionOthersNavTripListToNavTripDetails(
                        tripListType = 1, // tripListType 1: we come from OTHERS trip list
                        tripId = trip.tripId
                    )
                parentFrag.findNavController().navigate(action)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OthersTripViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = inflater.inflate(R.layout.card, parent, false)
        return OthersTripViewHolder(layout, parentFrag)
    }

    override fun getItemCount(): Int {
        return trips.size
    }

    override fun onBindViewHolder(holder: OthersTripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

}

