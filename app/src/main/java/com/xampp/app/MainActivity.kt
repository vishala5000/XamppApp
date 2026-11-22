package com.xampp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var videoView: VideoView
    private lateinit var btnUpload: MaterialButton
    private lateinit var layoutControls: View
    private lateinit var layoutPreview: View

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val PICK_VIDEO = 1001
    private val REQ_CAMERA = 2001
    private val TAG = "XamppApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            textureView   = findViewById(R.id.textureView)
            videoView     = findViewById(R.id.videoView)
            btnUpload     = findViewById(R.id.btnUpload)
            layoutControls= findViewById(R.id.layoutControls)
            layoutPreview = findViewById(R.id.layoutPreview)

            // Ensure controls are visible and preview is hidden on start
            layoutControls.visibility = View.VISIBLE
            layoutPreview.visibility = View.GONE

            btnUpload.setOnClickListener {
                val i = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
                startActivityForResult(i, PICK_VIDEO)
            }

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                    Log.d(TAG, "SurfaceTexture available")
                    // Only open camera if preview is visible
                    if (layoutPreview.visibility == View.VISIBLE && hasCameraPermission()) {
                        openCamera()
                    }
                }
                override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return try {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission", e)
            false
        }
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rc, perms, res)
        try {
            if (rc == REQ_CAMERA && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted")
                if (layoutPreview.visibility == View.VISIBLE && textureView.isAvailable) {
                    openCamera()
                }
            } else {
                Log.w(TAG, "Camera permission denied")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onRequestPermissionsResult", e)
        }
    }

    override fun onActivityResult(rc: Int, result: Int, data: Intent?) {
        super.onActivityResult(rc, result, data)
        try {
            if (rc == PICK_VIDEO && result == RESULT_OK) {
                data?.data?.let { uri ->
                    layoutControls.visibility = View.GONE
                    layoutPreview.visibility  = View.VISIBLE
                    videoView.setVideoURI(uri)
                    videoView.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.start()
                    }
                    // Open camera after switching to preview
                    if (hasCameraPermission() && textureView.isAvailable) {
                        openCamera()
                    } else if (!hasCameraPermission()) {
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onActivityResult", e)
        }
    }

    private fun openCamera() {
        if (!hasCameraPermission()) {
            Log.w(TAG, "No camera permission")
            return
        }

        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraIdList = manager.cameraIdList
            
            if (cameraIdList.isEmpty()) {
                Log.e(TAG, "No cameras available")
                return
            }

            // Find front camera
            var cameraId = cameraIdList[0]
            for (id in cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id
                    break
                }
            }

            Log.d(TAG, "Opening camera: $cameraId")

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened")
                    cameraDevice = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening camera", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
        }
    }

    private fun startPreview() {
        try {
            val texture = textureView.surfaceTexture ?: run {
                Log.e(TAG, "SurfaceTexture is null")
                return
            }
            
            val device = cameraDevice ?: run {
                Log.e(TAG, "Camera device is null")
                return
            }

            val surface = Surface(texture)
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(surface) }
                .build()

            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Camera configured")
                        captureSession = session
                        try { 
                            session.setRepeatingRequest(request, null, null) 
                        } catch (e: CameraAccessException) { 
                            Log.e(TAG, "Error starting preview", e)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera configuration failed")
                    }
                }, null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in startPreview", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { 
            captureSession?.close() 
            captureSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing capture session", e)
        }
        try { 
            cameraDevice?.close() 
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera device", e)
        }
    }
}
