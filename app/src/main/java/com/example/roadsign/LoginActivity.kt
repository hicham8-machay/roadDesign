package com.example.roadsign
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import androidx.core.app.ActivityCompat
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.widget.Toast

class LoginActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView

    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraPreviewSize: Size
    private val REQUEST_CAMERA_PERMISSION = 1
    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val startButton = findViewById<Button>(R.id.start_Button)
        startButton.setOnClickListener {

            val intent = Intent(this@LoginActivity, CameraActivity::class.java)
            startActivity(intent)
            Toast.makeText(
                applicationContext, "Camera",
                Toast.LENGTH_SHORT
            ).show()
            // Hide the button
            //startButton.setVisibility(View.GONE);
            // Start camera preview and object detection
            //startCameraPreview();
            //startObjectDetection();
        }
    }
    private fun getBackFacingCameraId(): String {
        val cameraIds = cameraManager.cameraIdList
        for (cameraId in cameraIds) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId
            }
        }
        return cameraIds[0]
    }

    private fun getPreviewOutputSize(display: Display, cameraCharacteristics: CameraCharacteristics, surfaceTextureClass: Class<*>): Size {
        val previewSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(surfaceTextureClass)
        val rotation = display.rotation
        val isPortrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
        val width = if (isPortrait) textureView.height else textureView.width
        val height = if (isPortrait) textureView.width else textureView.height
        val aspectRatio = width.toFloat() / height.toFloat()
        var bestSize: Size? = null
        var minDistortion = Float.MAX_VALUE
        previewSizes?.forEach {
            val previewWidth = it.width
            val previewHeight = it.height
            val previewAspectRatio = previewWidth.toFloat() / previewHeight.toFloat()
            val distortion = Math.abs(previewAspectRatio - aspectRatio)
            if (distortion < minDistortion) {
                bestSize = it
                minDistortion = distortion
            }
        }
        return bestSize ?: previewSizes?.get(0) ?: Size(1920, 1080)
    }
    private fun startCameraPreview() {
        val cameraId = getBackFacingCameraId()
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

        // Get the largest available preview size
        cameraPreviewSize = getPreviewOutputSize(
            textureView.display,
            cameraCharacteristics,
            SurfaceTexture::class.java
        )

        // Open the camera
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraDevice.close()
                    this@LoginActivity.finish()
                }
            }, null)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    private fun createCameraPreviewSession() {
        val surfaceTexture = textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(cameraPreviewSize.width, cameraPreviewSize.height)
        val previewSurface = Surface(surfaceTexture)
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(previewSurface)
        cameraDevice.createCaptureSession(
            listOf(previewSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    cameraCaptureSession = session
                    try {
                        captureRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        )
                        cameraCaptureSession.setRepeatingRequest(
                            captureRequestBuilder.build(),
                            null,
                            null
                        )
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // Handle configuration failure
                    // For example, you could display an error message to the user
                }
            }, null)
    }}