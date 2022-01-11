package com.example.goingmad

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.ConditionVariable
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.json.JSONObject
import org.osmdroid.views.overlay.Marker
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

var trips_step = mutableListOf<SingleTrip>()
private lateinit var rv: RecyclerView
val markers = ArrayList<Marker>()

class TripEditFragment : Fragment(R.layout.fragment_trip_edit) {
    private var REQUEST_IMAGE_CAPTURE = 2
    private var REQUEST_IMAGE_GALLERY = 3
    private lateinit var byteArray: ByteArray
    private lateinit var editCarImage: ImageView
    private lateinit var editDepLocation: AutoCompleteTextView
    private lateinit var editArrLocation: AutoCompleteTextView
    private lateinit var editIntermediateStop: AutoCompleteTextView
    private lateinit var editTripDuration: TextInputEditText
    private lateinit var editNumSeats: TextInputEditText
    private lateinit var editDepTime: TextInputEditText
    private lateinit var editPrice: TextInputEditText
    private lateinit var editDescription: TextInputEditText
    private lateinit var tripId: String
    private var job: Job? = null
    private var jsonAsString: String = ""
    var cal = Calendar.getInstance()
    val args: TripEditFragmentArgs by navArgs()
   // private lateinit var map : MapView
    private lateinit var ownerName : String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        editCarImage = view.findViewById<ImageView>(R.id.editCarImage)
        editDepLocation = view.findViewById(R.id.editDepartureLocation)
        editArrLocation = view.findViewById(R.id.editArrivalLocation)
        editIntermediateStop= view.findViewById<AutoCompleteTextView>(R.id.addIntermediateStop)
        editTripDuration = view.findViewById<TextInputEditText>(R.id.editTripDuration)
        editNumSeats = view.findViewById<TextInputEditText>(R.id.editSeats)
        editDepTime = view.findViewById<TextInputEditText>(R.id.editDepartureTime)
        editPrice = view.findViewById<TextInputEditText>(R.id.editPrice)
        editDescription = view.findViewById<TextInputEditText>(R.id.editDescription)

        val mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        val userId = currentUser?.uid.toString()
        val db = FirebaseFirestore.getInstance()
        val users = db.collection("users")
        val user = users.document(userId)

        user.addSnapshotListener { value, error ->
            if (error != null) throw error
            if (value != null) {
                ownerName = (value["fullName"]).toString()
            }
        }

        tripId = args.tripSafe.tripId
        editDepLocation.setText(args.tripSafe.departureLocation)
        editArrLocation.setText(args.tripSafe.arrivalLocation)
        editTripDuration.setText(args.tripSafe.duration)
        editDescription.setText(args.tripSafe.description)
        editDepTime.setText(args.tripSafe.departureDateHour)

        if (tripId == "") { // NEW TRIP
            editNumSeats.setText("")
            editPrice.setText("")
        } else { // EDIT TRIP
            editNumSeats.setText(args.tripSafe.seats.toString())
            editPrice.setText(args.tripSafe.price.toString())
        }

        editDepLocation.setOnItemClickListener { _, _, _, _ ->
            val v = activity?.currentFocus
            if (v != null) {
                val imm: InputMethodManager =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            val action = TripEditFragmentDirections.actionNavTripEditToMapFragment(args.tripSafe,0)
            findNavController().navigate(action)
        }
        editArrLocation.setOnItemClickListener { _, _, _, _ ->
            val v = activity?.currentFocus
            if (v != null) {
                val imm: InputMethodManager =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            val action = TripEditFragmentDirections.actionNavTripEditToMapFragment(args.tripSafe,1)
            findNavController().navigate(action)
        }

        editIntermediateStop.setOnItemClickListener { _, _, _, _ ->
            val v = activity?.currentFocus
            if (v != null) {
                val imm: InputMethodManager =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            val action = TripEditFragmentDirections.actionNavTripEditToMapFragment(args.tripSafe,2)
            findNavController().navigate(action)
        }

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<String>("mapResult")?.observe(
            viewLifecycleOwner) { result ->
            val type=result.split("---")[0]
            when(type){
                "0"->{
                    editDepLocation.setText(result.split("---")[1])
                }
                "1"->{
                    editArrLocation.setText(result.split("---")[1])
                }
                "2"->{
                    editIntermediateStop.setText(result.split("---")[1])
                }
            }
        }


        if (savedInstanceState != null) {
            editDepLocation.setText(savedInstanceState.getString("group28.lab4.DEP_LOCATION"))
            editArrLocation.setText(savedInstanceState.getString("group28.lab4.ARR_LOCATION"))
            editDepTime.setText(savedInstanceState.getString("group28.lab4.DEP_TIME"))
            editTripDuration.setText(savedInstanceState.getString("group28.lab4.TRIP_DURATION"))
            editPrice.setText(savedInstanceState.getString("group28.lab4.PRICE"))
            editNumSeats.setText(savedInstanceState.getString("group28.lab4.SEATS"))
            byteArray = savedInstanceState.getByteArray("group28.lab2.CAR_IMAGE_SAVED")!!
            val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            editCarImage.setImageBitmap(imageBitmap)

            val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
            val dataSave = sharedPref.getString("trips_steps", "")
            if (dataSave != "") {
                val jdatasave = JSONObject(dataSave!!)
                jsonAsString = jdatasave.getString("group28.lab3.JSONSTRING")
                val turnsType = object : TypeToken<MutableList<SingleTrip>>() {}.type
                trips_step = Gson().fromJson<MutableList<SingleTrip>>(jsonAsString, turnsType)
            }
        } else {
            if (args.tripSafe.carImageByteArray != null) {
                byteArray = args.tripSafe.carImageByteArray!!
                val bitmap: Bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                editCarImage.setImageBitmap(bitmap)
            }else{
                val imageBitmap = AppCompatResources.getDrawable(requireContext(), R.drawable.default_car_image)?.toBitmap()
                val stream = ByteArrayOutputStream()
                imageBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                byteArray = stream.toByteArray()
            }
            // necessary, otherwise added intermediate stops persist among different edits of trips
            trips_step.clear()

            // take intermediateStops and adds them to local list
            for (stop in (args.tripSafe.intermediateStops )) {
                trips_step.add(SingleTrip(stop))
            }
            when (args.locationReturned){
                0->  editDepLocation.setText(args.locality)
                1->    editArrLocation.setText(args.locality)
                2->    trips_step.add(SingleTrip(args.locality))

            }
        }
        editCarImage.alpha = 0.5f

        val today = Calendar.getInstance()
        val dateSetListener =
            DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
                val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    cal.set(Calendar.MINUTE, minute)

                    if(cal.time>today.time) {
                        val myFormat = "dd/MM/yyyy  HH:mm" // mention the format you need
                        val sdf = SimpleDateFormat(myFormat, Locale.ITALY)
                        editDepTime.setText(sdf.format(cal.time))
                    }else{
                        Snackbar.make(this.requireView(), R.string.future_date, Snackbar.LENGTH_SHORT)
                            .show()
                    }
                }

                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, monthOfYear)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                if (isOfAge(
                        today.get(Calendar.DAY_OF_MONTH),
                        today.get(Calendar.MONTH),
                        today.get(Calendar.YEAR),
                        dayOfMonth,
                        monthOfYear,
                        year
                    ) == 1
                ) {
                    TimePickerDialog(
                        this.requireContext(), timeSetListener,
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                } else {
                    Snackbar.make(this.requireView(), R.string.future_date, Snackbar.LENGTH_SHORT)
                        .show()
                }
            }

        editDepTime.setOnClickListener {
            DatePickerDialog(
                this.requireContext(), dateSetListener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val imageButton = view.findViewById<ImageButton>(R.id.setImageButton)
        registerForContextMenu(imageButton)
        imageButton.setOnClickListener {
            imageButton.showContextMenu()
        }

        // adapt local list of steps into RecyclerView
        rv = view.findViewById<RecyclerView>(R.id.additionalTripsRecyclerView)
        rv.layoutManager = LinearLayoutManager(this.context)
        rv.adapter = SingleTripAdapter(trips_step, this)

        val addStopsTextView = view.findViewById<TextInputLayout>(R.id.addIntermediateStopLabel)
        val addStopsButton = view.findViewById<Button>(R.id.addIntermediateStopButton)

        // when Add button is pressed, the location is added and the adapter is triggered
        addStopsButton.setOnClickListener {
            val text = addStopsTextView.editText?.text.toString()
            if (text != "") {
                val mapUtilities = MapUtilities()
                var exist = false
                runBlocking{
                    exist = mapUtilities.locationExist(text)
                    delay(1000)
                    if(!exist)
                        addStopsTextView.error = getString(R.string.no_city)
                    else{
                        addStopsTextView.error = null
                        trips_step.add(SingleTrip(text))
                        rv.adapter?.notifyDataSetChanged()
                    }
                }
            }
            // when "add" is pressed, remove text from edittext and remove keyboard
            addStopsTextView.editText?.text?.clear()
            val focus = activity?.currentFocus
            if (focus != null) {
                val imm: InputMethodManager =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(focus.windowToken, 0)
            }
        }
    }

    override fun onPause() {
        job?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.manu_save_edit, menu)
    }

    private fun checkNecessaryContent(): Boolean {
        val progress = requireView().findViewById<ProgressBar>(R.id.progressSave)
        val pass  = AtomicBoolean(true)
        val layDepLocation =
            requireView().findViewById<TextInputLayout>(R.id.editDepartureLocationLabel)
        val layArrLocation =
            requireView().findViewById<TextInputLayout>(R.id.editArrivalLocationLabel)
        val layDepTime = requireView().findViewById<TextInputLayout>(R.id.editDepartureTimeLabel)
        val layTripDuration =
            requireView().findViewById<TextInputLayout>(R.id.editTripDurationLabel)
        val layNumSeats = requireView().findViewById<TextInputLayout>(R.id.editSeatsLabel)
        val layPrice = requireView().findViewById<TextInputLayout>(R.id.editPriceLabel)

        val depLocNew = layDepLocation.editText?.text.toString()
        val arrLocNew = layArrLocation.editText?.text.toString()
        val depTimeNew = layDepTime.editText?.text.toString()
        val tripDurationNew = layTripDuration.editText?.text.toString()
        val numSeatsNew = layNumSeats.editText?.text.toString()
        val priceNew = layPrice.editText?.text.toString()

        if (depLocNew == "") {
            layDepLocation.error = getString(R.string.no_departure)
            pass.getAndSet(false)
        } else  {
            val mapUtilities = MapUtilities()
            var exist = false
            runBlocking{
                exist = mapUtilities.locationExist(depLocNew)
                delay(1000)

                if(!exist){ pass.getAndSet(false)
                layDepLocation.error = getString(R.string.no_city)}
                else
                    layDepLocation.error = null
            }
        }
        if (arrLocNew == "") {
            layArrLocation.error = getString(R.string.no_arrival)
            pass.getAndSet(false)
        } else  {
            val mapUtilities = MapUtilities()
            var exist = false
            runBlocking{
                exist = mapUtilities.locationExist(arrLocNew)
                delay(1000)

                if(!exist){ pass.getAndSet(false)
                layArrLocation.error = getString(R.string.no_city)}
                else
                    layDepLocation.error = null
            }
        }
        if (depTimeNew == "") {
            layDepTime.error = getString(R.string.no_depDate)
            pass.getAndSet(false)
        } else {
            layDepTime.error = null
        }
        if (tripDurationNew == "") {
            layTripDuration.error = getString(R.string.no_duration)
            pass.getAndSet(false)
        } else {
            layTripDuration.error = null
        }
        if (numSeatsNew == "") {
            layNumSeats.error = getString(R.string.no_seats)
            pass.getAndSet(false)
        } else {
            layNumSeats.error = null
        }
        if (priceNew == "") {
            layPrice.error = getString(R.string.no_price)
            pass.getAndSet(false)
        } else {
            layPrice.error = null
        }
        progress.visibility = View.INVISIBLE
        return pass.get()
    }

    private fun saveEdit() {
        if (checkNecessaryContent()) {
            setHasOptionsMenu(false)
            val depLocNew = editDepLocation.text.toString()
            val arrLocNew = editArrLocation.text.toString()
            val tripDurationNew = editTripDuration.text.toString()
            val numSeatsNew = editNumSeats.text.toString().toLong()
            val depTimeNew = editDepTime.text.toString()
            val priceNew = editPrice.text.toString().toDouble()
            val descriptionNew = editDescription.text.toString()
            val imageBitmap = editCarImage.drawable.toBitmap()
            val stream = ByteArrayOutputStream()
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            byteArray = stream.toByteArray()

            val v = activity?.currentFocus
            if (v != null) {
                val imm: InputMethodManager =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }

            val intermediateTrips = ArrayList<String>()
            for (trip in trips_step) {
                intermediateTrips.add(trip.locationName)
            }

            val db = FirebaseFirestore.getInstance()
            val trips = db.collection("trips")
            if (tripId != "") { // UPDATE USER
                val trip = trips.document(tripId)
                val imageRef = Firebase.storage.reference.child("trips/$tripId.jpg")
                val uploadTask = imageRef.putBytes(byteArray)
                uploadTask.addOnSuccessListener { _ ->
                    trip.update(
                        mutableMapOf(
                            ("ownerId" to args.tripSafe.ownerId),
                            ("departureLocation" to depLocNew),
                            ("arrivalLocation" to arrLocNew),
                            ("departureTime" to depTimeNew),
                            ("tripDuration" to tripDurationNew),
                            ("intermediateStops" to intermediateTrips),
                            ("price" to priceNew),
                            ("seats" to numSeatsNew),
                            ("description" to descriptionNew)
                        ) as Map<String, Any>
                    )
                    Snackbar.make(this.requireView(), R.string.trip_updated, Snackbar.LENGTH_SHORT)
                        .show()
                    findNavController().popBackStack()
                }.addOnFailureListener {
                    Snackbar.make(
                        this.requireView(),
                        R.string.something_wrong,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } else { // ADD USER
                val mAuth = FirebaseAuth.getInstance()
                val currentUser = mAuth.currentUser
                val userId = currentUser?.uid.toString()
                trips.add(
                    mutableMapOf(
                        ("ownerId" to userId),
                        ("ownerName" to ownerName),
                        ("departureLocation" to depLocNew),
                        ("arrivalLocation" to arrLocNew),
                        ("departureTime" to depTimeNew),
                        ("tripDuration" to tripDurationNew),
                        ("price" to priceNew),
                        ("seats" to numSeatsNew),
                        ("bookedUsers" to ArrayList<String>(0)),
                        ("intermediateStops" to intermediateTrips),
                        ("interestedUsers" to ArrayList<String>(0)),
                        ("description" to descriptionNew),
                        ("hasReviewedDriver" to ArrayList<String>(0)),
                        ("passengersReviewed" to ArrayList<String>(0)),
                        ("tripEnded" to false),
                        ("tripDeleted" to false)
                    ) as Map<String, Any>
                )
                    .addOnSuccessListener {
                        tripId = it.id
                        val imageRef = Firebase.storage.reference.child("trips/$tripId.jpg")
                        val uploadTask = imageRef.putBytes(byteArray)
                        uploadTask.addOnSuccessListener { _ ->
                            Snackbar.make(
                                this.requireView(),
                                R.string.trip_added,
                                Snackbar.LENGTH_SHORT
                            ).show()
                            findNavController().popBackStack()
                        }.addOnFailureListener {
                            Snackbar.make(
                                this.requireView(),
                                R.string.something_wrong,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }

            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                if(job==null || job?.isActive == false) {
                    job = MainScope().launch {
                        saveEdit()
                    }
                    val progress = requireView().findViewById<ProgressBar>(R.id.progressSave)
                    progress.visibility = View.VISIBLE
                }
                true
            }
            else -> super.onContextItemSelected(item)
        }

    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
         activity?.menuInflater?.inflate(R.menu.context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.take_picture -> {
                dispatchTakePictureIntent()
                true
            }
            R.id.select_gallery -> {
                val gallery =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                startActivityForResult(gallery, REQUEST_IMAGE_GALLERY)
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } catch (e: ActivityNotFoundException) {
            // display error state to the user
            Snackbar.make(this.requireView(), R.string.file_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // get picture from camera
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == AppCompatActivity.RESULT_OK) {
            val imgBitmap = data?.extras?.get("data") as Bitmap
            editCarImage.setImageBitmap(imgBitmap)
            val stream = ByteArrayOutputStream()
            imgBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            byteArray = stream.toByteArray()
        }
        // choose picture from gallery
        if (resultCode == AppCompatActivity.RESULT_OK && requestCode == REQUEST_IMAGE_GALLERY) {
            val imgUri = data?.data
            editCarImage.setImageURI(imgUri)
            val imgBitmap = editCarImage.drawable.toBitmap()
            val stream = ByteArrayOutputStream()
            imgBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            byteArray = stream.toByteArray()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putByteArray("group28.lab2.CAR_IMAGE_SAVED", byteArray)
        val gson = Gson()
        jsonAsString = gson.toJson(trips_step)
        val savedData = mutableMapOf(("group28.lab3.JSONSTRING" to jsonAsString))
        val jsonList = JSONObject(savedData as Map<*, *>)
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
        with(sharedPref?.edit()) {
            this?.putString("trips_steps", jsonList.toString())
            this?.apply()
        }
        outState.putString("group28.lab4.DEP_LOCATION", editDepLocation.toString())
        outState.putString("group28.lab4.ARR_LOCATION", editArrLocation.toString())
        outState.putString("group28.lab4.DEP_TIME", editDepTime.toString())
        outState.putString("group28.lab4.TRIP_DURATION", editTripDuration.toString())
        outState.putString("group28.lab4.PRICE", editPrice.toString())
        outState.putString("group28.lab4.SEATS", editNumSeats.toString())
    }

    override fun onResume() {
        super.onResume()
        val items =
            listOf(getString(R.string.choose_from_map))
        val adapter = ArrayAdapter(requireContext(), R.layout.list_item, items)
        editDepLocation.setAdapter(adapter)
        editArrLocation.setAdapter(adapter)
        editIntermediateStop.setAdapter(adapter)
    }


}

data class SingleTrip(val locationName: String)

class SingleTripAdapter(val trips_step: List<SingleTrip>, val parentFrag: Fragment) :
    RecyclerView.Adapter<SingleTripAdapter.SingleTripViewHolder>() {
    class SingleTripViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val locationName: TextView = v.findViewById<TextView>(R.id.mtrl_list_item_text)
        val removeButton: Button = v.findViewById<Button>(R.id.removeIntermediateStop)

        fun bind(singleTrip: SingleTrip, parentFrag: Fragment) {
            locationName.text = singleTrip.locationName
            if (parentFrag is TripEditFragment) {
                removeButton.setOnClickListener {
                    trips_step.remove(SingleTrip(locationName.text.toString()))
                    rv.adapter?.notifyDataSetChanged()
                }
            } else if (parentFrag is TripDetailsFragment) {
                removeButton.visibility = View.INVISIBLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SingleTripViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = inflater.inflate(R.layout.material_list_item_single_line, parent, false)
        //return SingleTripViewHolder(layout)
        return SingleTripViewHolder(layout)
    }

    override fun getItemCount(): Int {
        return trips_step.size
    }

    override fun onBindViewHolder(holder: SingleTripViewHolder, position: Int) {
        holder.bind(trips_step[position], parentFrag)
    }
}
