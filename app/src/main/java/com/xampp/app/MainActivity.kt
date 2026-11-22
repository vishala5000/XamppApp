package com.xampp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView   = findViewById(R.id.textureView)
        videoView     = findViewById(R.id.videoView)
        btnUpload     = findViewById(R.id.btnUpload)
        layoutControls= findViewById(R.id.layoutControls)
        layoutPreview = findViewById(R.id.layoutPreview)

        btnUpload.setOnClickListener {
            val i = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
            startActivityForResult(i, PICK_VIDEO)
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                if (hasCameraPermission()) openCamera()
            }
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }

        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun hasCameraPermission() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rc, perms, res)
        if (rc == REQ_CAMERA && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            if (textureView.isAvailable) openCamera()
        }
    }

    override fun onActivityResult(rc: Int, result: Int, data: Intent?) {
        super.onActivityResult(rc, result, data)
        if (rc == PICK_VIDEO && result == RESULT_OK) {
            data?.data?.let { uri ->
                layoutControls.visibility = View.GONE
                layoutPreview.visibility  = View.VISIBLE
                videoView.setVideoURI(uri)
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.start()
                }
            }
        }
    }

    private fun openCamera() {
        if (!hasCameraPermission()) return
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = (0 until manager.cameraIdList.size)
            .map { manager.cameraIdList[it] }
            .firstOrNull {
                manager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList.firstOrNull() ?: return

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, null)
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun startPreview() {
        val texture = textureView.surfaceTexture ?: return
        val surface = Surface(texture)
        val request = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            .apply { addTarget(surface) }
            .build()

        cameraDevice!!.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try { session.setRepeatingRequest(request, null, null) }
                    catch (e: CameraAccessException) { e.printStackTrace() }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try { captureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
    }
}
