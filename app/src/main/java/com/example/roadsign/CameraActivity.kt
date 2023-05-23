package com.example.roadsign

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.util.Size
import android.view.TextureView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import java.io.FileInputStream
import java.nio.channels.FileChannel
import android.content.res.AssetManager
import java.io.InputStream


class CameraActivity : AppCompatActivity() {

    //private lateinit var cameraView: TextureView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var interpreter: Interpreter
    private lateinit var viewFinder: PreviewView


    private val modelInputSize = 32
    private val modelOutputClasses = arrayOf(
        "Speed Limit 20 km/h",
        "Speed Limit 30 km/h",
        "Speed Limit 50 km/h",
        "Speed Limit 60 km/h",
        "Speed Limit 70 km/h",
        "Speed Limit 80 km/h",
        "End of Speed Limit 80 km/h",
        "Speed Limit 100 km/h",
        "Speed Limit 120 km/h",
        "No passing",
        "No passing for vechiles over 3.5 metric tons",
        "Right-of-way at the next intersection",
        "Priority road",
        "Yield",
        "Stop",
        "No vechiles",
        "Vechiles over 3.5 metric tons prohibited",
        "No entry",
        "General caution",
        "Dangerous curve to the left",
        "Dangerous curve to the right",
        "Double curve",
        "Bumpy road",
        "Slippery road",
        "Road narrows on the right",
        "Road work",
        "Traffic signals",
        "Pedestrians",
        "Children crossing",
        "Bicycles crossing",
        "Beware of ice/snow",
        "Wild animals crossing",
        "End of all speed and passing limits",
        "Turn right ahead",
        "Turn left ahead",
        "Ahead only",
        "Go straight or right",
        "Go straight or left",
        "Keep right",
        "Keep left",
        "Roundabout mandatory",
        "End of no passing",
        "End of no passing by vechiles over 3.5 metric tons",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        viewFinder = findViewById(R.id.viewFinder)

        // cameraView = findViewById(R.id.cameraView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Load the TensorFlow Lite model
        interpreter = Interpreter(loadModelFile())

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { image ->
                            val bitmap = image.toBitmap()
                            if (bitmap != null) {
                                val inputBuffer = preprocessImage(bitmap)
                                val outputBuffer =
                                    ByteBuffer.allocateDirect(4 * modelOutputClasses.size)
                                outputBuffer.order(ByteOrder.nativeOrder())

                                interpreter.run(inputBuffer, outputBuffer)
                                outputBuffer.rewind()

                                val probabilities = FloatArray(modelOutputClasses.size)
                                outputBuffer.asFloatBuffer().get(probabilities)

                                val maxIndex =
                                    probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
                                val className =
                                    if (maxIndex != -1) modelOutputClasses[maxIndex] else "Unknown"

                                GlobalScope.launch(Dispatchers.Main) {
                                    updateClassTextView(className)
                                    updateProbabilityTextView(probabilities[maxIndex])
                                }
                            }
                            image.close()
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalyzer
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun Image.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, outputStream)

        val jpegData = outputStream.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

        return bitmap
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize)
        inputBuffer.order(ByteOrder.nativeOrder())

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)

        val pixels = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(
            pixels,
            0,
            resizedBitmap.width,
            0,
            0,
            resizedBitmap.width,
            resizedBitmap.height
        )

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF).toFloat() / 255.0f
            val g = (pixel shr 8 and 0xFF).toFloat() / 255.0f
            val b = (pixel and 0xFF).toFloat() / 255.0f

            val gray = (0.2989 * r + 0.587 * g + 0.114 * b).toFloat()

            inputBuffer.putFloat(gray)
        }

        return inputBuffer
    }

    private fun updateClassTextView(className: String) {
        val classTextView = findViewById<TextView>(R.id.classTextView)
        classTextView.text = "CLASS: $className"
    }

    private fun updateProbabilityTextView(probability: Float) {
        val probabilityTextView = findViewById<TextView>(R.id.probabilityTextView)
        probabilityTextView.text = "PROBABILITY: ${(probability * 100).toInt()}%"
    }

    private fun loadModelFile(): ByteBuffer {
        val assetManager: AssetManager = assets
        val modelFilename = "model3.tflite"

        val inputStream: InputStream = assetManager.open(modelFilename)
        val fileBytes = inputStream.readBytes()

        val modelBuffer = ByteBuffer.allocateDirect(fileBytes.size)
        modelBuffer.order(ByteOrder.nativeOrder())
        modelBuffer.put(fileBytes)
        modelBuffer.flip()

        return modelBuffer
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

private fun ImageProxy.toBitmap(): Bitmap? {
    val buffer: ByteBuffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

