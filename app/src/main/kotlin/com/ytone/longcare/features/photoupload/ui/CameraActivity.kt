package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.ytone.longcare.R
import com.ytone.longcare.databinding.ActivityCameraBinding
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap

class CameraActivity : AppCompatActivity(), PreviewDialogFragment.PreviewDialogListener {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var flashMode: Int = ImageCapture.FLASH_MODE_OFF

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val watermarkLines = intent.getStringArrayListExtra("watermarkLines")
        binding.watermark.text = watermarkLines?.joinToString("\n")

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.captureButton.setOnClickListener { takePhoto() }
        binding.switchCameraButton.setOnClickListener { switchCamera() }
        binding.flashButton.setOnClickListener { toggleFlash() }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                    val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
                    val watermarkBitmap = viewToBitmap(binding.watermark)
                    val watermarkedBitmap = addWatermark(rotatedBitmap, watermarkBitmap)

                    PreviewDialogFragment.newInstance(watermarkedBitmap)
                        .show(supportFragmentManager, "preview")

                    image.close()
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun viewToBitmap(view: View): Bitmap {
        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun addWatermark(bitmap: Bitmap, watermark: Bitmap): Bitmap {
        val result = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.drawBitmap(watermark, 10f, (bitmap.height - watermark.height - 10).toFloat(), null)
        return result
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Use case binding failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun toggleFlash() {
        val flashButton = binding.flashButton
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> {
                flashButton.setImageResource(R.drawable.ic_flash_on)
                ImageCapture.FLASH_MODE_ON
            }
            ImageCapture.FLASH_MODE_ON -> {
                flashButton.setImageResource(R.drawable.ic_flash_auto)
                ImageCapture.FLASH_MODE_AUTO
            }
            else -> {
                flashButton.setImageResource(R.drawable.ic_flash_off)
                ImageCapture.FLASH_MODE_OFF
            }
        }
        imageCapture?.flashMode = flashMode
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onConfirm(bitmap: Bitmap) {
        val photoFile = File(
            cacheDir,
            "captured_image_${System.currentTimeMillis()}.jpg"
        )

        FileOutputStream(photoFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }

        val savedUri = Uri.fromFile(photoFile)
        val intent = Intent()
        intent.putExtra("captured_image_uri", savedUri.toString())
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onRetake() {
        // The dialog is dismissed, so the user can retake the photo.
    }
}
