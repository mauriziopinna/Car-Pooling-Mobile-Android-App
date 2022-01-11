package com.example.goingmad

import android.accounts.NetworkErrorException
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Address
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.*
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.io.ByteArrayOutputStream
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.util.*
import kotlin.collections.ArrayList

private val stopsList = mutableListOf<SingleTrip>()
private var newArrayList: MutableList<String> =mutableListOf<String>()

class TripDetailsFragment : Fragment(R.layout.fragment_trip_details) {
    private lateinit var tripOwnerName: TextView
    private lateinit var tripOwnerPicture: CircleImageView
    private lateinit var byteArray: ByteArray
    private lateinit var carImage: ImageView
    private lateinit var depLocation: TextView
    private lateinit var arrLocation: TextView
    private lateinit var tripDuration: TextView
    private lateinit var numSeats: TextView
    private lateinit var depTime: TextView
    private lateinit var price: TextView
    private lateinit var description: TextView
    private lateinit var rv: RecyclerView
    private lateinit var map : MapView
    private lateinit var mapButton : Button
    private lateinit var controller: IMapController
    private lateinit var progressBar: ProgressBar
    private lateinit var list : ListenerRegistration
    private var inflated = false
    var job: Job? = null
    var listener: ListenerRegistration?=null

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1

    val args: TripDetailsFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tripOwnerName = view.findViewById<TextView>(R.id.tripOwnerName)
        tripOwnerPicture = view.findViewById<CircleImageView>(R.id.tripOwnerPicture)
        carImage = view.findViewById(R.id.CarImage)
        depLocation = view.findViewById<TextView>(R.id.departureLocation)
        arrLocation = view.findViewById<TextView>(R.id.arrivalLocation)
        tripDuration = view.findViewById<TextView>(R.id.tripDuration)
        numSeats = view.findViewById<TextView>(R.id.seats)
        depTime = view.findViewById<TextView>(R.id.departureTime)
        price = view.findViewById<TextView>(R.id.price)
        description = view.findViewById<TextView>(R.id.description)
        rv = view.findViewById<RecyclerView>(R.id.intermediateStopsRV)
        map = view.findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        mapButton = view.findViewById<Button>(R.id.mapButton)
        progressBar = view.findViewById<ProgressBar>(R.id.progress_circular)
        val speedDialView = view.findViewById<SpeedDialView>(R.id.speedDial)
        val speedDialFinishedView = view.findViewById<SpeedDialView>(R.id.speedDial2)
        val fabButtonBookTrip = view.findViewById<ExtendedFloatingActionButton>(R.id.tripDetailsFAB)
        map.isFocusable = false
        map.isClickable = false
        mapButton.isEnabled =false
        var ownerName = ""
        var ownerId = ""
        var tripEnded = false

        Configuration.getInstance().load(this.requireContext(), activity?.getSharedPreferences("map",MODE_PRIVATE))

        controller = map.controller
        val mapPoint = GeoPoint(42.7443, 12.0809)
        controller.setZoom(6.5)
        controller.animateTo(mapPoint)

        val db = FirebaseFirestore.getInstance()
        val trip = db.collection("trips").document(args.tripId)

        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = SingleTripAdapter(stopsList, this)

        //set view element with data contained in db
        list = trip.addSnapshotListener { value, error ->
            if (error != null) throw error
            if (value != null) {
                if (value.data != null) {
                    val newArray = (value["intermediateStops"]) as ArrayList<*>?
                    newArrayList.clear()
                    if (newArray != null) {
                        for (stop in newArray) {
                            newArrayList.add(stop.toString())
                        }
                    }
                    ownerId = value["ownerId"].toString()
                    db.collection("users").document(ownerId).addSnapshotListener { doc, err ->
                        if (err != null) throw err
                        if (doc != null) {
                            ownerName = (doc["fullName"]).toString()
                            tripOwnerName.text = ownerName
                        }
                    }



                    depLocation.text = (value["departureLocation"]).toString()
                    arrLocation.text = (value["arrivalLocation"]).toString()
                    tripDuration.text = (value["tripDuration"]).toString()
                    numSeats.text = (value["seats"]).toString()
                    depTime.text = (value["departureTime"]).toString()
                    price.text = (value["price"]).toString()
                    description.text = (value["description"]).toString()
                    tripEnded = value["tripEnded"] as Boolean
                    val imageRef = Firebase.storage.reference.child("users/${ownerId}.jpg")
                    downloadAndSetImage(imageRef, tripOwnerPicture)

                    tripOwnerPicture.setOnClickListener {
                        val action = TripDetailsFragmentDirections.actionNavTripDetailsToNavShowProfile(ownerId,1,args.tripId)
                        findNavController().navigate(action)
                    }
                   tripOwnerName.setOnClickListener {
                        val action = TripDetailsFragmentDirections.actionNavTripDetailsToNavShowProfile(ownerId,1,args.tripId)
                        findNavController().navigate(action)
                    }

                    // val intermediateStops = value.data?.get("intermediateStops")
                    stopsList.clear()

                    val stopsLabel = view.findViewById<TextView>(R.id.additionalTrips)
                    if (newArrayList.size == 0) {
                        stopsLabel.visibility = View.INVISIBLE
                    } else {
                        stopsLabel.visibility = View.VISIBLE
                    }

                    for (stop in newArrayList)
                        stopsList.add(SingleTrip(stop))
                    rv.adapter?.notifyDataSetChanged()
                    // Add departure location Marker
                    markers.clear()

                    if (job == null || job?.isActive == false) {
                        job = MainScope().launch {
                            try {
                                val mapUpdater = MapUpdater(requireContext(), map, view)
                                mapUpdater.mapUpdate(
                                    depLocation.text.toString(),
                                    arrLocation.text.toString()
                                )
                            } catch (e:IllegalStateException){

                            }
                        }
                        job?.invokeOnCompletion {
                            if (markers.isNotEmpty()) {
                                progressBar.visibility = View.GONE
                                map.alpha = 1f
                                if(markers.size >1) {
                                    val b: BoundingBox = getBoundingBox(
                                        markers[0].position,
                                        markers[markers.size - 1].position
                                    )
                                     map.zoomToBoundingBox(b, false, 100)
                                }else if (markers.size ==1){
                                    controller.setZoom(5.5)
                                    controller.animateTo(markers[0].position)
                                }
                                mapButton.isEnabled = true
                            }
                        }
                    }

                    mapButton.setOnClickListener {
                        val tripSent = Trip(
                            args.tripId,
                            ownerId,
                            ByteArray(0),
                            depLocation.text.toString(),
                            arrLocation.text.toString(),
                            depTime.text.toString(),
                            tripDuration.text.toString(),
                            numSeats.text.toString().toLong(),
                            price.text.toString().toDouble(),
                            description.text.toString(),
                            newArrayList
                        )
                        val action =
                            TripDetailsFragmentDirections.actionNavTripDetailsToMapFragment(tripSent)
                        findNavController().navigate(action)
                    }

                    // Update Map
                    if (args.tripListType == 0){
                        if (tripEnded) {
                            speedDialFinishedView.visibility = View.VISIBLE
                            speedDialView.visibility = View.INVISIBLE
                        } else {
                            speedDialFinishedView.visibility = View.INVISIBLE
                            speedDialView.visibility = View.VISIBLE
                        }
                    }
                    map.invalidate()


                }else{
                    activity?.findNavController(R.id.nav_host_fragment)?.popBackStack()
                }
            }
        }

        val imageRef = Firebase.storage.reference.child("trips/${args.tripId}.jpg")
        imageRef.getBytes(1024 * 1024).addOnSuccessListener {
            // if manage to download image from database
            byteArray = it
            val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            carImage.setImageBitmap(imageBitmap)
        }.addOnFailureListener { // if fail to download image
            val imageBitmap =
                AppCompatResources.getDrawable(requireContext(), R.drawable.default_car_image)
                    ?.toBitmap()
            carImage.setImageBitmap(imageBitmap)
        }

        // SPEED DIAL VIEW 1 (FOR WHEN TRIP IS NOT FINISHED)
        speedDialView.addActionItem( // COMPLETE TRIP
            SpeedDialActionItem.Builder(
                R.id.finishTripFAB,
                R.drawable.outline_done_24
            )
                .setLabel(getString(R.string.finishTrip))
                .setLabelColor(Color.BLACK)
                .setLabelClickable(false)
                .create()
        )

        speedDialView.addActionItem( // DELETE TRIP
            SpeedDialActionItem.Builder(R.id.hideTripFAB,
                R.drawable.outline_delete_outline_24)
                .setLabel(getString(R.string.hide_trip))
                .setLabelColor(Color.BLACK)
                .setLabelClickable(false)
                .create()
        )

        speedDialView.addActionItem( // Show Interested Users
            SpeedDialActionItem.Builder(
                R.id.seeInterestedFAB,
                R.drawable.baseline_person_add_24
            )
                .setLabel(getString(R.string.show_interested_users))
                .setLabelColor(Color.BLACK)
                .setLabelClickable(false)
                .create()
        )

        speedDialView.addActionItem( // SHOW BOOKED USERS
            SpeedDialActionItem.Builder(R.id.seeBookedFAB,
                R.drawable.baseline_turned_in_24)
                .setLabel(getString(R.string.show_booked_users))
                .setLabelColor(Color.BLACK)
                .setLabelClickable(false)
                .create()
        )

        // SPEED DIAL 2 (BECOMES VISIBLE WHEN TRIP IS FINISHED)
        speedDialFinishedView.addActionItem( // SHOW BOOKED USERS
            SpeedDialActionItem.Builder(R.id.seeBookedFAB,
                R.drawable.baseline_turned_in_24)
                .setLabel(getString(R.string.show_booked_users))
                .setLabelColor(Color.BLACK)
                .setLabelClickable(false)
                .create()
        )

        if (args.tripListType == 0) { // tripListType 0: from ShowTripList (YOUR TRIPS)

            //speedDialView.visibility = View.VISIBLE
            // fabButtonBookTrip.visibility = View.INVISIBLE // useless since default is INVISIBLE

            // if you're seeing your trips, there's no reason for seeing your name and picture
            tripOwnerName.visibility = View.GONE
            tripOwnerPicture.visibility = View.GONE

            speedDialFinishedView.setOnActionSelectedListener { actionItem ->
                when (actionItem.id) {
                    R.id.seeBookedFAB -> {
                        val action =
                            TripDetailsFragmentDirections.actionNavTripDetailsToBookedUsersFragment(
                                trip.id,
                                "bookedUsers",
                                getString(R.string.show_booked_users)
                            )
                        findNavController().navigate(action)
                    }
                }
                false
            }

            speedDialView.setOnActionSelectedListener { actionItem ->
                when (actionItem.id) {
                    R.id.seeInterestedFAB -> {
                        val action =
                            TripDetailsFragmentDirections.actionNavTripDetailsToBookedUsersFragment(
                                trip.id,
                                "interestedUsers",
                                getString(R.string.show_interested_users)
                            )
                        findNavController().navigate(action)
                    }
                    R.id.seeBookedFAB -> {
                        val action =
                            TripDetailsFragmentDirections.actionNavTripDetailsToBookedUsersFragment(
                                trip.id,
                                "bookedUsers",
                                getString(R.string.show_booked_users)
                            )
                        findNavController().navigate(action)
                    }
                    R.id.hideTripFAB -> {
                        if(tripEnded){
                            Snackbar.make(
                                this.requireView(),
                                R.string.trip_ended_no_delete,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        } else {
                            val action =
                                TripDetailsFragmentDirections.actionNavTripDetailsToNavTripList()
                            findNavController().navigate(action)
                            trip.update(
                                mutableMapOf(
                                    ("tripDeleted" to true)
                                ) as Map<String, Any>
                            )
                        }
                    }
                    R.id.finishTripFAB -> {
                        if(tripEnded){
                            Snackbar.make(
                                this.requireView(),
                                R.string.trip_already_ended,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        } else{
                            trip.update(
                                mutableMapOf(
                                    ("tripEnded" to true)
                                ) as Map<String, Any>
                            )
                            tripEnded = true
                            Snackbar.make(
                                this.requireView(),
                                R.string.trip_completed,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                false
            }

        } else if(args.tripListType == 1){ // tripListType 1: from othersShowTripList
            // obtain userId of currentUser
            val mAuth = FirebaseAuth.getInstance()
            val currentUser = mAuth.currentUser
            val userId = currentUser?.uid.toString()

           list = trip.addSnapshotListener { value, error->
                if (error != null) throw error
                if (value != null) {
                    if (value.data != null) {
                        val bookedUsers = value["bookedUsers"] as ArrayList<*>
                        val hasReviewedDriver = (value["hasReviewedDriver"]) as ArrayList<*>
                            if (bookedUsers.contains(userId)) { // if the user has participated in trip
                                if (value["tripEnded"] as Boolean) { // if trip has ended
                                    if (!hasReviewedDriver.contains(userId)) { // if the user has not reviewed the driver yet
                                        fabButtonBookTrip.text = getString(R.string.review)
                                        fabButtonBookTrip.visibility = View.VISIBLE
                                        fabButtonBookTrip.setOnClickListener {
                                            db.collection("users").document(userId).addSnapshotListener { value, error ->
                                                if(error!=null) throw error
                                                if(value!=null){
                                                    val userName=value["fullName"] as String
                                                    val action =
                                                        TripDetailsFragmentDirections.actionNavTripDetailsToReviewFragment(
                                                            true,
                                                            false,
                                                            args.tripId,
                                                            ownerId,
                                                            userId,
                                                            userName,
                                                            ownerName,
                                                            getString(R.string.title_DriverReview)
                                                        )
                                                    findNavController().navigate(action)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else { //the user is not booked to the trip
                                if (!(value["tripEnded"] as Boolean)) {
                                    val interestedUsers = (value["interestedUsers"]) as ArrayList<*>
                                    fabButtonBookTrip.text = getString(R.string.book_trip)
                                    if (!interestedUsers.contains(userId)) { // if user is not yet interested, show "BOOK TRIP" button
                                        fabButtonBookTrip.visibility = View.VISIBLE
                                    }
                                    fabButtonBookTrip.setOnClickListener {
                                        trip.update(
                                            "interestedUsers",
                                            FieldValue.arrayUnion(userId)
                                        )
                                            .addOnSuccessListener {
                                                Snackbar.make(
                                                    view,
                                                    R.string.correctly_interested,
                                                    Snackbar.LENGTH_SHORT
                                                ).show()
                                                fabButtonBookTrip.visibility=View.INVISIBLE
                                            }.addOnFailureListener {
                                                Snackbar.make(
                                                    view,
                                                    R.string.something_wrong,
                                                    Snackbar.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    private fun getBoundingBox(start: GeoPoint, end: GeoPoint): BoundingBox {
        val north: Double
        val south: Double
        val east: Double
        val west: Double
        if (start.latitude > end.latitude) {
            north = start.latitude
            south = end.latitude
        } else {
            north = end.latitude
            south = start.latitude
        }
        if (start.longitude > end.longitude) {
            east = start.longitude
            west = end.longitude
        } else {
            east = end.longitude
            west = start.longitude
        }
        return BoundingBox(north, east, south, west)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (args.tripListType == 0) { // tripListType = 0 -> coming from tripListFragments (your trips, you can edit)
            val db = Firebase.firestore
            db.collection("trips").document(args.tripId).addSnapshotListener { value, error ->
                if(error!=null) throw error
                if (value != null) {
                    val tripEnded = value["tripEnded"] as Boolean
                    if (!tripEnded && !inflated) {
                        inflater.inflate(R.menu.edit_menu, menu)
                        inflated = true
                    }
                    if(tripEnded){
                        menu.removeItem(R.id.edit)
                    }
                }
            }
        }
    }

    override fun onResume() {
        inflated = false
        map.onResume()
        super.onResume()
    }

    override fun onPause() {
        job?.cancel()
        map.onPause()
        list.remove()
        val db = FirebaseFirestore.getInstance()
        val trip = db.collection("trips").document(args.tripId)
        trip.addSnapshotListener{_,_->

        }
        /*
        db.collection("trips").document(args.tripId).addSnapshotListener { _, _ ->
        }*/
        super.onPause()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun editTrip() {
        val imageBitmap = carImage.drawable.toBitmap()
        val stream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()
        val depLocDisplay = depLocation.text.toString()
        val arrLocDisplay = arrLocation.text.toString()
        val tripDurationDisplay = tripDuration.text.toString()
        val numSeatsDisplay = numSeats.text.toString().toLong()
        val depTimeDisplay = depTime.text.toString()
        val priceDisplay = price.text.toString().toDouble()
        val descriptionDisplay = description.text.toString()
        val mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        val ownerId = currentUser?.uid.toString()

        val trip = Trip(
            args.tripId,
            ownerId,
            byteArray,
            depLocDisplay,
            arrLocDisplay,
            depTimeDisplay,
            tripDurationDisplay,
            numSeatsDisplay,
            priceDisplay,
            descriptionDisplay,
            newArrayList
        )
        val action = TripDetailsFragmentDirections.actionNavTripDetailsToTripEditFragment(1, trip)
        findNavController().navigate(action)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.edit -> {
                editTrip()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this.requireActivity(),
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }
}


class MapUpdater (val context : Context, val map: MapView, val view: View) {
    fun getMarker(locality: String) : Marker? {
        lateinit var mapPoint: GeoPoint
        var exist = false
        val coderNominatim = GeocoderNominatim("madcarpooling")
        try {
            val geoResults: List<Address> = coderNominatim.getFromLocationName(locality, 1)
            if (geoResults.isNotEmpty()) {
                exist=true
                val address = geoResults[0]
                val longitude = address.longitude
                val latitude = address.latitude
                mapPoint = GeoPoint(latitude, longitude)
            }
        } catch (e: NetworkErrorException) {

        }

        try {
            var marker :Marker? =null
            if(exist) {
                marker = Marker(map)
                marker.icon = ContextCompat.getDrawable(context, R.drawable.goingmadmarker)
                marker.position = mapPoint
                marker.title = locality
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            return marker
        } catch(e: NullPointerException){
            return null
        }
    }

    suspend fun mapUpdate(depLocation: String, arrLocation: String) {
        // Move the execution of the coroutine to the I/O dispatcher
        return withContext(Dispatchers.IO) {
            try {
                var marker = getMarker(depLocation)
                delay(1000)
                if (marker != null)
                    markers.add(marker)
                else  Snackbar.make(
                   view,
                    R.string.no_departure_exist,
                    Snackbar.LENGTH_SHORT
                ).show()



                // Add intermediate location Marker
                for (stop in newArrayList) {
                    stopsList.add(SingleTrip(stop))
                    marker = getMarker(stop)
                    delay(1000)
                    if(marker != null)
                    markers.add(marker)
                    else //TOD
                        Snackbar.make(
                            view,
                            R.string.no_intermediate_exist,
                            Snackbar.LENGTH_SHORT
                        ).show()
                }

                // Add arrival location Marker
                marker = getMarker(arrLocation)
                delay(1000)
                if(marker != null)
                markers.add(marker)
                else  Snackbar.make(
                    view,
                    R.string.no_arrival_exist,
                    Snackbar.LENGTH_SHORT
                ).show()

                //need the library that connect coordinates to names
                val stops = arrayListOf<GeoPoint>()

                // Add markers to the map
                for (m in markers) {
                    map.overlays?.add(m)
                    stops.add(m.position)
                }

                //Create road line
                if(stops.size >1){
                    val roadManager: RoadManager = OSRMRoadManager(context, "madCarPooling")
                    try {
                        val road: Road = roadManager.getRoad(stops)
                        delay(1000)

                        val roadLine: Polyline = RoadManager.buildRoadOverlay(road)
                        // Add road line to the map
                        map.overlays?.add(roadLine)

                        // Update Map
                        map.invalidate()
                    } catch(e: IllegalArgumentException) {
                        // NOTHING
                    }
                }
                map.invalidate()
            } catch(e: NullPointerException){

            }
        }
    }

    suspend fun createRoad(stops:ArrayList<GeoPoint>):Polyline{
        return withContext(Dispatchers.IO) {
            val roadManager: RoadManager = OSRMRoadManager(context, "madCarPooling")
            val road: Road = roadManager.getRoad(stops)
            delay(1000)

            val roadLine: Polyline = RoadManager.buildRoadOverlay(road)
            return@withContext roadLine
        }
    }
}



