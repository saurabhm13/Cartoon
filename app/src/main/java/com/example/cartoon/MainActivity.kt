package com.example.cartoon

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.cartoon.ml.WhiteboxCartoonGanDr
import com.example.cartoon.ml.WhiteboxCartoonGanFp16
import com.example.cartoon.ml.WhiteboxCartoonGanInt8
import org.tensorflow.lite.support.image.TensorImage
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_IMAGE = 1
        private const val DIRECTORY_NAME = "CartoonImages"
    }

    private lateinit var imageView: ImageView
    private lateinit var convertButton: Button
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        convertButton = findViewById(R.id.Convert)
        saveButton = findViewById(R.id.Save)

        findViewById<Button>(R.id.selectImagebtn).setOnClickListener {
            fetchImageFromStorage()
        }

        convertButton.setOnClickListener {
            convertImageToCartoon()
        }

        saveButton.setOnClickListener {
            saveCartoonizedImage()
        }
    }

    private fun fetchImageFromStorage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_IMAGE)
    }

    private fun convertImageToCartoon() {
        val drawable = imageView.drawable
        if (drawable == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Load the image drawable into a TensorImage for inference
        val sourceImage = drawable.toTensorImage()
        val cartoonizedImage = inferenceWithInt8Model(sourceImage)

        // Set the cartoonized image in the ImageView using Glide
        Glide.with(this)
            .load(cartoonizedImage.bitmap)
            .into(imageView)
    }

    private fun saveCartoonizedImage() {
        val drawable = imageView.drawable
        if (drawable == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Convert the drawable to a TensorImage for saving
        val sourceImage = drawable.toTensorImage()

        // Check if external storage is available for read and write
        if (isExternalStorageWritable()) {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                DIRECTORY_NAME
            )
            if (!directory.exists()) {
                directory.mkdirs()
            }

            // Generate a unique filename for the saved image
            val fileName = "cartoon_${System.currentTimeMillis()}.jpg"
            val file = File(directory, fileName)

            // Save the image to the file using Glide
            Glide.with(this)
                .asBitmap()
                .load(sourceImage.bitmap)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        // Save the bitmap to the file
                        var outputStream: OutputStream? = null
                        try {
                            outputStream = FileOutputStream(file)
                            resource.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                            outputStream.flush()

                            // Scan the saved image file to make it visible in the gallery
                            MediaScannerConnection.scanFile(
                                this@MainActivity,
                                arrayOf(file.toString()),
                                null,
                                null
                            )

                            Toast.makeText(
                                this@MainActivity,
                                "Image saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to save image",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            outputStream?.close()
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Not used
                    }
                })
        } else {
            Toast.makeText(this, "External storage is not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_IMAGE && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data

            // Load the selected image into the ImageView using Glide
            Glide.with(this)
                .load(imageUri)
                .into(imageView)
        }
    }

    private fun inferenceWithFp16Model(sourceImage: TensorImage): TensorImage {
        val model = WhiteboxCartoonGanFp16.newInstance(this)

        // Runs model inference and gets result.
        val outputs = model.process(sourceImage)
        val cartoonizedImage = outputs.cartoonizedImageAsTensorImage

        // Releases model resources if no longer used.
        model.close()

        return cartoonizedImage
    }

    private fun inferenceWithDrModel(sourceImage: TensorImage): TensorImage {
        val model = WhiteboxCartoonGanDr.newInstance(this)

        // Runs model inference and gets result.
        val outputs = model.process(sourceImage)
        val cartoonizedImage = outputs.cartoonizedImageAsTensorImage

        // Releases model resources if no longer used.
        model.close()

        return cartoonizedImage
    }

    private fun inferenceWithInt8Model(sourceImage: TensorImage): TensorImage {
        val model = WhiteboxCartoonGanInt8.newInstance(this)

        // Runs model inference and gets result.
        val outputs = model.process(sourceImage)
        val cartoonizedImage = outputs.cartoonizedImageAsTensorImage

        // Releases model resources if no longer used.
        model.close()

        return cartoonizedImage
    }

    /**
     * Extension function to convert a Drawable to a TensorImage.
     */
    private fun Drawable.toTensorImage(): TensorImage {
        val bitmap = (this as? BitmapDrawable)?.bitmap
            ?: Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        return TensorImage.fromBitmap(bitmap)
    }
}