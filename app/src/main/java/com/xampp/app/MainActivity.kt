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

    // Screen 1: Main screen
    private lateinit var screenMain: View
    private lateinit var btnAddVideo: MaterialButton

    // Screen 2: Preview screen
    private lateinit var screenPreview: View
    private lateinit var textureView: TextureView
    private lateinit var videoView: VideoView

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var pendingCameraOpen = false
    private val PICK_VIDEO = 1001
    private val REQ_CAMERA = 2001
    private val TAG = "XamppApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Screen 1 views
        screenMain   = findViewById(R.id.screenMain)
        btnAddVideo  = findViewById(R.id.btnAddVideo)

        // Screen 2 views
        screenPreview = findViewById(R.id.screenPreview)
        textureView   = findViewById(R.id.textureView)
        videoView     = findViewById(R.id.videoView)

        // Initial state: show main screen, hide preview
        screenMain.visibility    = View.VISIBLE
        screenPreview.visibility = View.GONE

        btnAddVideo.setOnClickListener {
            val i = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
            startActivityForResult(i, PICK_VIDEO)
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                Log.d(TAG, "SurfaceTexture available")
                if (pendingCameraOpen) {
                    pendingCameraOpen = false
                    if (hasCameraPermission()) openCamera()
                }
            }
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    private fun hasCameraPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rc, perms, res)
        if (rc == REQ_CAMERA && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted")
            if (textureView.isAvailable) openCamera()
        } else {
            Log.w(TAG, "Camera permission denied")
        }
    }

    override fun onActivityResult(rc: Int, result: Int, data: Intent?) {
        super.onActivityResult(rc, result, data)
        if (rc == PICK_VIDEO && result == RESULT_OK) {
            data?.data?.let { uri ->
                // Switch screens
                screenMain.visibility    = View.GONE
                screenPreview.visibility = View.VISIBLE

                // Start video loop
                videoView.setVideoURI(uri)
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.start()
                }

                // Open camera (deferred if surface not ready)
                if (textureView.isAvailable) {
                    if (hasCameraPermission()) openCamera()
                    else requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                } else {
                    pendingCameraOpen = true
                    if (!hasCameraPermission()) {
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                    }
                }
            }
        }
    }

    private fun openCamera() {
        if (!hasCameraPermission()) {
            Log.w(TAG, "No camera permission")
            return
        }
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val ids = manager.cameraIdList
            if (ids.isEmpty()) { Log.e(TAG, "No cameras"); return }

            var cameraId = ids[0]
            for (id in ids) {
                val c = manager.getCameraCharacteristics(id)
                if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id; break
                }
            }
            Log.d(TAG, "Opening camera: $cameraId")

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened")
                    cameraDevice = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error"); camera.close(); cameraDevice = null
                }
            }, null)
        } catch (e: SecurityException) { Log.e(TAG, "Security exception", e) }
          catch (e: Exception) { Log.e(TAG, "Error opening camera", e) }
    }

    private fun startPreview() {
        try {
            val texture = textureView.surfaceTexture ?: return
            val device  = cameraDevice ?: return
            val surface = Surface(texture)
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(surface) }.build()

            device.createCaptureSession(listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try { session.setRepeatingRequest(request, null, null) }
                        catch (e: CameraAccessException) { Log.e(TAG, "Preview error", e) }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Config failed")
                    }
                }, null)
        } catch (e: Exception) { Log.e(TAG, "startPreview error", e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { captureSession?.close(); captureSession = null } catch (_: Exception) {}
        try { cameraDevice?.close(); cameraDevice = null } catch (_: Exception) {}
    }
}
