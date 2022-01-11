package com.example.goingmad

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


//function to calculate distance of a date; it return:
// 1 if date is in future
// 0 if date is in the past 16 years
// -1 if date is before of 16 year ago
fun isOfAge(
    current_date: Int, current_month: Int,
    current_year: Int, birth_date: Int,
    birth_month: Int, birth_year: Int
): Int {
    var calculatedDate: Int = current_date
    var calculatedMonth: Int = current_month
    var calculatedYear: Int = current_year
    // days of every month
    val month = listOf(
        31, 28, 31, 30, 31, 30, 31,
        31, 30, 31, 30, 31
    )

    // if birth date is greater then current birth
    // month then do not count this month and add 30
    // to the date so as to subtract the date and
    // get the remaining days
    if (birth_date > calculatedDate) {
        calculatedDate += month[birth_month - 1]
        calculatedMonth -= 1
    }

    // if birth month exceeds current month, then do
    // not count this year and add 12 to the month so
    // that we can subtract and find out the difference
    if (birth_month > calculatedMonth) {
        calculatedYear -= 1
        calculatedMonth += 12 //this should not be necessary
    }

    // calculate date, month, year
    calculatedDate -= birth_date
    calculatedMonth -= birth_month
    calculatedYear -= birth_year

    if (calculatedYear < 0 ||
        calculatedMonth < 0 ||
        calculatedDate < 0
    ) {
        return 1
    }
    if (calculatedYear < 16) {
        return 0
    }
    return -1
}

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {
    private lateinit var profileImageEdit: de.hdodenhof.circleimageview.CircleImageView
    private lateinit var nameEdit: TextInputEditText
    private lateinit var genderEdit: AutoCompleteTextView
    private lateinit var nicknameEdit: TextInputEditText
    private lateinit var emailEdit: TextInputEditText
    private lateinit var locationEdit: TextInputEditText
    private lateinit var carEdit: TextInputEditText
    private lateinit var dateOfBirthEdit: TextInputEditText
    var cal = Calendar.getInstance()
    private var REQUEST_IMAGE_CAPTURE = 2
    private var REQUEST_IMAGE_GALLERY = 3
    private lateinit var byteArray: ByteArray
    val args: EditProfileFragmentArgs by navArgs()
    private lateinit var userId: String
    private var saved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //get view element
        profileImageEdit =
            view.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.editProfileImage)
        nameEdit = view.findViewById<TextInputEditText>(R.id.editTextFullname)
        genderEdit = view.findViewById(R.id.editTextGender)
        nicknameEdit = view.findViewById<TextInputEditText>(R.id.editTextNickname)
        emailEdit = view.findViewById<TextInputEditText>(R.id.editTextEmail)
        locationEdit = view.findViewById<TextInputEditText>(R.id.editTextLocation)
        carEdit = view.findViewById<TextInputEditText>(R.id.editTextCar)
        dateOfBirthEdit = view.findViewById<TextInputEditText>(R.id.editTextBirthdate)

        //set view element with the safe arguments received by showProfileFragment
        nameEdit.setText(args.userSafe?.name)
        genderEdit.setText(args.userSafe?.gender)
        nicknameEdit.setText(args.userSafe?.nickname)
        emailEdit.setText(args.userSafe?.email)
        locationEdit.setText(args.userSafe?.location)
        carEdit.setText(args.userSafe?.car)
        dateOfBirthEdit.setText(args.userSafe?.dateOfBirth)
        val mAuth= FirebaseAuth.getInstance()
        userId = mAuth.currentUser!!.uid.toString()

        //get image if screen rotates
        if (savedInstanceState != null) {
            byteArray = savedInstanceState.getByteArray("group28.lab2.IMAGE_SAVED")!!
            val imageBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            profileImageEdit.setImageBitmap(imageBitmap)
        } else {
            if(args.userSafe==null){//first time the user is logged
                (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(false)
                profileImageEdit.setImageResource(R.drawable.default_user_image)
                profileImageEdit.imageAlpha = 255
                val db = FirebaseFirestore.getInstance()
                val users = db.collection("users")
                val user = users.document(userId)
                user.get().addOnSuccessListener {
                    val fullName = it.data?.get("fullName") as String?
                    val email = it.data?.get("email") as String?
                    nameEdit.setText(fullName)
                    emailEdit.setText(email)
                }
                val imgBitmap = profileImageEdit.drawable.toBitmap()
                val stream = ByteArrayOutputStream()
                imgBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                byteArray = stream.toByteArray()
            }
            else {
                byteArray = args.userSafe?.profileImage!!
                val bitmap: Bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                profileImageEdit.setImageBitmap(bitmap)
            }
        }
        profileImageEdit.alpha = 0.5f

        //set date and time picker for date of birth
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
                if (age == -1) {
                    dateOfBirthEdit.setText(sdf.format(cal.time))
                } else if (age == 0) {
                    Snackbar.make(this.requireView(), R.string.not_of_age, Snackbar.LENGTH_SHORT)
                        .show()
                } else {
                    Snackbar.make(
                        this.requireView(),
                        R.string.are_from_future,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }

        dateOfBirthEdit.setOnClickListener {
            DatePickerDialog(
                this.requireContext(), dateSetListener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        //show menu when click on camera icon
        val imageButton = view.findViewById<ImageButton>(R.id.setImageButton)
        registerForContextMenu(imageButton)
        imageButton.setOnClickListener {
            imageButton.showContextMenu()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.manu_save_edit, menu)
    }

    //check if every information has been set
    private fun checkNecessaryContent(): Boolean {
        var pass = true

        val layName = requireView().findViewById<TextInputLayout>(R.id.editTextFullnameLabel)
        val layNickname = requireView().findViewById<TextInputLayout>(R.id.editTextNicknameLabel)
        val layGender = requireView().findViewById<TextInputLayout>(R.id.editTextGenderLabel)
        val layEmail = requireView().findViewById<TextInputLayout>(R.id.editTextEmailLabel)
        val layLocation = requireView().findViewById<TextInputLayout>(R.id.editTextLocationLabel)
        val layCar = requireView().findViewById<TextInputLayout>(R.id.editTextCarLabel)
        val layDateOfBirth =
            requireView().findViewById<TextInputLayout>(R.id.editTextBirthdateLabel)

        val nameSafe = nameEdit.text.toString()
        val genderSafe = genderEdit.text.toString()
        val nicknameSafe = nicknameEdit.text.toString()
        val emailSafe = emailEdit.text.toString()
        val locationSafe = locationEdit.text.toString()
        val carSafe = carEdit.text.toString()
        val dateOfBirthSafe = dateOfBirthEdit.text.toString()

        if (nameSafe == "") {
            layName.error = getString(R.string.no_fullname)
            pass = false
        } else {
            layName.error = null
        }
        if (genderSafe == "") {
            layGender.error = getString(R.string.no_gender)
            pass = false
        } else {
            layGender.error = null
        }
        if (nicknameSafe == "") {
            layNickname.error = getString(R.string.no_nickname)
            pass = false
        } else {
            layNickname.error = null
        }
        if (emailSafe == "") {
            layEmail.error = getString(R.string.no_email)
            pass = false
        } else {
            layEmail.error = null
        }
        if (locationSafe == "") {
            layLocation.error = getString(R.string.no_location)
            pass = false
        } else {
            layLocation.error = null
        }
        if (carSafe == "") {
            layCar.error = getString(R.string.no_car)
            pass = false
        } else {
            layCar.error = null
        }
        if (dateOfBirthSafe == "") {
            layDateOfBirth.error = getString(R.string.no_date_of_birth)
            pass = false
        } else {
            layDateOfBirth.error = null
        }

        return pass
    }

    //save information set in the editTexts
    fun saveEdit(): User? {

        if (checkNecessaryContent()) {
            val nameSafe = nameEdit.text.toString()
            val genderSafe = genderEdit.text.toString()
            val nicknameSafe = nicknameEdit.text.toString()
            val emailSafe = emailEdit.text.toString()
            val locationSafe = locationEdit.text.toString()
            val carSafe = carEdit.text.toString()
            val dateOfBirthSafe = dateOfBirthEdit.text.toString()
            val imageBitmap = profileImageEdit.drawable.toBitmap()
            val stream = ByteArrayOutputStream()
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val profileImageSafe = stream.toByteArray()
            //set navheader information
            val navHeader = activity?.findViewById<View>(R.id.nav_header)
            val imageNav = navHeader?.findViewById<ImageView>(R.id.ImageHeader)
            val nicknameNAv = navHeader?.findViewById<TextView>(R.id.nameHeader)
            val emailNav = navHeader?.findViewById<TextView>(R.id.emailHeader)
            emailNav?.text = emailSafe
            nicknameNAv?.text = nicknameSafe
            imageNav?.setImageBitmap(imageBitmap)

            //close keyboard
            val v = activity?.currentFocus
            if (v != null) {
                val imm: InputMethodManager =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }

            return User(
                nameSafe,
                genderSafe,
                nicknameSafe,
                emailSafe,
                locationSafe,
                carSafe,
                dateOfBirthSafe,
                profileImageSafe
            )
        }
        val user: User? = null
        return user
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                if(!saved){
                    val result = saveEdit()
                    if (result != null) {
                        //upload user data in the db and permorm a popbackstack in order to return to show profile
                        val db = FirebaseFirestore.getInstance()
                        val users = db.collection("users")
                        val user = users.document(userId)
                        //upload image in the db storage
                        val imageRef = Firebase.storage.reference.child("users/$userId.jpg")
                        val uploadTask = imageRef.putBytes(result.profileImage!!)
                        uploadTask.addOnSuccessListener { _ ->
                            user.update(
                                mutableMapOf(
                                    ("fullName" to result.name),
                                    ("nickname" to result.nickname),
                                    ("gender" to result.gender),
                                    ("car" to result.car),
                                    ("email" to result.email),
                                    ("location" to result.location),
                                    ("dateOfBirth" to result.dateOfBirth)
                                ) as Map<String, Any>
                            )
                            Snackbar.make(
                                this.requireView(),
                                R.string.profile_modified,
                                Snackbar.LENGTH_SHORT
                            ).show()
                            if(args.userSafe!=null)//comes from show profile
                                findNavController().popBackStack()
                            else{//first time logged, has to insert the informations
                                val action = EditProfileFragmentDirections.actionNavEditProfileToOthersNavTripList()
                                findNavController().navigate(action)
                            }
                        }.addOnFailureListener {
                            Snackbar.make(
                                this.requireView(),
                                R.string.something_wrong,
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                        saved = true
                    }
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
            Snackbar.make(this.requireView(), R.string.file_error, Snackbar.LENGTH_LONG).show()

        }
    }

    //used to retrieve images from the camera or from the gallery
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // get picture from camera
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == AppCompatActivity.RESULT_OK) {
            /*val imgUri = getUri(requireContext(), data?.extras?.get("data") as Bitmap)
            val imgBitmap = handleRotationBitmap(requireContext(), imgUri!!)
            activity?.contentResolver?.delete(imgUri, null, null)*/
            val imgBitmap = data?.extras?.get("data") as Bitmap
            profileImageEdit.setImageBitmap(imgBitmap)
            val stream = ByteArrayOutputStream()
            imgBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            byteArray = stream.toByteArray()
        }
        // choose picture from gallery
        if (resultCode == AppCompatActivity.RESULT_OK && requestCode == REQUEST_IMAGE_GALLERY) {
            val imgUri = data?.data
            profileImageEdit.setImageURI(imgUri)
            val imgBitmap = profileImageEdit.drawable.toBitmap()
            val stream = ByteArrayOutputStream()
            imgBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            byteArray = stream.toByteArray()
        }
    }

    //save the image bytearray when the screen rotates
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if(!this::byteArray.isInitialized){
            val imageBitmap =
                AppCompatResources.getDrawable(requireContext(), R.drawable.default_car_image)
                    ?.toBitmap()
            val stream = ByteArrayOutputStream()
            imageBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            byteArray = stream.toByteArray()
        }
        outState.putByteArray("group28.lab2.IMAGE_SAVED", byteArray)
    }

    //set the dropdown menu for the gender
    override fun onResume() {
        super.onResume()
        val items =
            listOf(getString(R.string.male), getString(R.string.female), getString(R.string.other))
        val adapter = ArrayAdapter(requireContext(), R.layout.list_item, items)
        genderEdit.setAdapter(adapter)
    }

}
/*
fun handleRotationBitmap(context: Context, selectedImage: Uri): Bitmap{
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    var imageStream: InputStream? = context.contentResolver.openInputStream(selectedImage)
    BitmapFactory.decodeStream(imageStream, null, options)
    imageStream?.close()
    options.inJustDecodeBounds = false
    imageStream = context.contentResolver.openInputStream(selectedImage)
    var img: Bitmap? = BitmapFactory.decodeStream(imageStream, null, options)
    img = rotateImageIfRequired(context, img!!, selectedImage)
    return img
}

fun rotateImageIfRequired(context:Context, img:Bitmap, selectedImage: Uri):Bitmap{
    val input: InputStream? = context.contentResolver.openInputStream(selectedImage)
    val ei:ExifInterface
    if(Build.VERSION.SDK_INT > 23)
        ei = ExifInterface(input!!)
    else
        ei = ExifInterface(selectedImage.path!!)
    return when(ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)){
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
        else -> img
    }
}

fun rotateImage(img:Bitmap, degree: Float):Bitmap{
    val matrix = Matrix()
    matrix.postRotate(degree)
    val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.height, img.width, matrix, true)
    img.recycle()
    return rotatedImg
}

fun getUri(context: Context, bitmap: Bitmap): Uri? {
    val bytes = ByteArrayOutputStream()
    val fos:OutputStream
    var imageFile: File? = null
    var imgUri:Uri? = null
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
        val resolver = context.contentResolver
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "title")
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "GoingMAD")
        imgUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
        fos = resolver.openOutputStream(imgUri)!!
    } else {
        //val imagesDir = context.filesDir
        val imagesDir = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES).toString() + File.separator + "GoingMAD")
        if (!imagesDir.exists())
            imagesDir.mkdir()
        imageFile = File(imagesDir, "profile_image" + ".png")
        //if(!imageFile.exists()) {
            //imageFile.createNewFile()
            fos = FileOutputStream(imageFile)
        //}
    }

    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
    fos.flush()
    fos.close()
    if (imageFile != null){
        imageFile.toString() as String
        MediaScannerConnection.scanFile(context, arrayOf(imageFile.toString()), null, null)
        imgUri = Uri.fromFile(imageFile)
    }

    //val path:String = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "title", null)
    //return Uri.parse(path)
    return imgUri
}
*/