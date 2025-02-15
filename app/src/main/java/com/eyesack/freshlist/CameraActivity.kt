package com.eyesack.freshlist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class CameraActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val CAMERA_PERMISSION_CODE = 100
    private val TAG = "CameraActivity"

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        imageView = findViewById(R.id.imageView)

        val useImagePicker = intent.getBooleanExtra("use_image_picker", false)
        if (useImagePicker) {
            // Launch the image picker if the extra flag is set to true
            dispatchPickImageIntent()
        } else {
            // Launch the camera as usual if the flag is not set
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
            } else {
                dispatchTakePictureIntent()
            }
        }
    }

    private fun dispatchPickImageIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        } else {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }


    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        // Create a file to save the image
        val photoFile = createImageFile() // Method to create the file
        photoUri = FileProvider.getUriForFile(
            this,
            "com.eyesack.freshlist.fileprovider", // Ensure this is declared in your AndroidManifest.xml
            photoFile
        )

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)

        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    // Method to create the image file where the picture will be stored
    private fun createImageFile(): File {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${System.currentTimeMillis()}", ".jpg", storageDir)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature", Toast.LENGTH_SHORT).show()
                finish() // Close the activity if permission is denied
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    // Handle image captured from camera
                    photoUri?.let { uri ->
                        try {
                            var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            bitmap = rotateImageIfRequired(bitmap, uri)
                            imageView.setImageBitmap(bitmap)
                            sendImageToServer(bitmap)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    // Handle image selected from gallery
                    data?.data?.let { uri ->
                        try {
                            var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            bitmap = rotateImageIfRequired(bitmap, uri)
                            imageView.setImageBitmap(bitmap)
                            sendImageToServer(bitmap)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            finish() // Close the activity if no image was selected or captured
        }
    }

    private fun rotateImageIfRequired(img: Bitmap, uri: Uri): Bitmap {
        val input = contentResolver.openInputStream(uri)
        val ei = input?.let { ExifInterface(it) }
        val orientation = ei?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }

    private fun sendImageToServer(bitmap: Bitmap) {
        // Resize the bitmap if needed (optional)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 1024, 1024, true) // Resize to a reasonable size

        // Convert the bitmap to a byte array
        val stream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream) // Adjust JPEG quality here
        val byteArray = stream.toByteArray()

        // Retrieve stored list items and convert to a comma-separated string
        val sharedPreferences = getSharedPreferences("shopping_list_prefs", Context.MODE_PRIVATE)
        val jsonList = sharedPreferences.getString("shopping_list", "[]")

        // Assuming jsonList is a JSON array string; convert it to a List<String>
        val listItems = Gson().fromJson(jsonList, Array<String>::class.java).toList()
        val listString = listItems.joinToString(separator = ",")

        // Create request body with image and list parameters
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files", "image.jpg", RequestBody.create("image/jpeg".toMediaType(), byteArray))
            .addFormDataPart("list", listString)  // Add the list parameter here
            .build()

        // Send POST request using OkHttp
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://10.0.0.116:8000/process-images/")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send image", e)
                runOnUiThread { Toast.makeText(this@CameraActivity, "Failed to send image, is server up?", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response code: ${response.code}")
                    runOnUiThread { Toast.makeText(this@CameraActivity, "Error: ${response.message}", Toast.LENGTH_LONG).show() }
                } else {
                    val jsonResponse = response.body?.string()
                    handleApiResponse(jsonResponse ?: "No response")
                    runOnUiThread { showJsonPopup(jsonResponse ?: "No response") }
                }
                response.close()
            }
        })
    }
    private fun handleApiResponse(response: String) {
        try {
            // Step 1: Convert the response to a JSONObject
            val jsonResponse = JSONObject(response)

            // Step 2: Extract the "items_for_removal" array
            val itemsForRemoval = jsonResponse.getJSONArray("items_for_removal")

            // Step 3: Initialize the list to hold items that need to be crossed off
            val itemsToRemove = mutableListOf<String>()

            // Step 4: Loop through the items in the "items_for_removal" array
            for (i in 0 until itemsForRemoval.length()) {
                // Get each item as a string and add it to the list (convert to lowercase for matching)
                val item = itemsForRemoval.getString(i).toLowerCase()
                itemsToRemove.add(item)
            }

            // Step 5: Send the list of items to be crossed off back to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("items_to_cross_off", ArrayList(itemsToRemove))
            startActivity(intent)

        } catch (e: JSONException) {
            e.printStackTrace()
            Toast.makeText(this, "Error parsing API response: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showJsonPopup(jsonResponse: String) {
        val textView = TextView(this).apply {
            text = jsonResponse
            setPadding(16, 16, 16, 16)
        }

        AlertDialog.Builder(this)
            .setTitle("JSON Response")
            .setView(ScrollView(this).apply { addView(textView) })
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
