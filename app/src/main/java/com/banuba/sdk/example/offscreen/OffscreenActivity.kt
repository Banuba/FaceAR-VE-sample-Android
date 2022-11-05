package com.banuba.sdk.example.offscreen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.banuba.sdk.cameraui.data.PipConfig
import com.banuba.sdk.effect_player.ConsistencyMode
import com.banuba.sdk.effect_player.EffectPlayer
import com.banuba.sdk.effect_player.EffectPlayerConfiguration
import com.banuba.sdk.effect_player.NnMode
import com.banuba.sdk.example.R
import com.banuba.sdk.manager.BanubaSdkManager
import com.banuba.sdk.offscreen.ImageProcessResult
import com.banuba.sdk.offscreen.OffscreenEffectPlayer
import com.banuba.sdk.offscreen.OffscreenSimpleConfig
import com.banuba.sdk.recognizer.FaceSearchMode
import com.banuba.sdk.ve.flow.VideoCreationActivity
import java.io.File
import java.nio.ByteBuffer

class OffscreenActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OffscreenActivity"

        private const val VIDEO_EDITOR_REQUEST_CODE = 7788

        private const val REQUEST_CODE_PERMISSIONS = 1001

        private const val SAMPLE_EFFECT_NAME = "Beauty"

        private val CAMERA_CAPTURE_SIZE = Size(1280, 720)

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
        )
    }

    private val buffersQueue = BuffersQueue()
    private var glI420Renderer: GLI420Renderer = GLI420Renderer()

    private var camera: Camera2Simple? = null
    private var offscreenEffectPlayer: OffscreenEffectPlayer? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var loadEffect = false

    private val effectFilePath: String by lazy(LazyThreadSafetyMode.NONE) {
        val pathToEffects = File(this.filesDir, "banuba/bnb-resources/effects")
        File(pathToEffects, SAMPLE_EFFECT_NAME).toString()
    }

    private val frameReadyCallback =
        Camera2Simple.FrameReadyCallback { image, imageOrientation ->
            offscreenEffectPlayer?.processImage(
                image,
                imageOrientation
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offscreen)

        glSurfaceView = findViewById<GLSurfaceView>(R.id.surfaceView)?.apply {
            setEGLContextClientVersion(3)
            setRenderer(glI420Renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        findViewById<Button>(R.id.loadEffect).setOnClickListener {
            toggleEffect()
        }

        findViewById<Button>(R.id.startVideoEditorButton).setOnClickListener {
            startVideoEditor()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        if (checkAllPermissionsGranted()) {
            handleGrantedPermissions()
        } else {
            Log.d(TAG, "Request permissions")
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        releaseOffscreen()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!checkAllPermissionsGranted()) {
                Toast.makeText(
                    applicationContext,
                    "Please grant permissions",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                startCameraPreview()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, results)
        }
    }

    private fun toggleEffect() {
        loadEffect = !loadEffect
        if (loadEffect) {
            offscreenEffectPlayer?.loadEffect(effectFilePath)
        } else {
            offscreenEffectPlayer?.unloadEffect()
        }
    }

    private fun startVideoEditor() {
        Log.d(TAG, "Start Video Editor")
        releaseOffscreen()

        startActivityForResult(
            VideoCreationActivity.startFromCamera(
                context = this,
                // setup data that will be acceptable during export flow
                additionalExportData = null,
                // set TrackData object if you open VideoCreationActivity with preselected music track
                audioTrackData = null,
                // set PiP video configuration
                pictureInPictureConfig = PipConfig(
                    video = Uri.EMPTY,
                    openPipSettings = false
                )
            ), VIDEO_EDITOR_REQUEST_CODE
        )
    }

    private fun prepareOffscreen() {
        Log.d(TAG, "Prepare Offscreen")
        camera = Camera2Simple(
            applicationContext,
            frameReadyCallback,
            CAMERA_CAPTURE_SIZE
        )

        BanubaSdkManager.deinitialize()
        BanubaSdkManager.initialize(applicationContext, getString(R.string.banuba_token))

        val effectPlayerConfig = EffectPlayerConfiguration(
            CAMERA_CAPTURE_SIZE.width,
            CAMERA_CAPTURE_SIZE.height,
            NnMode.ENABLE,
            FaceSearchMode.MEDIUM,
            false,
            false
        )

        offscreenEffectPlayer = EffectPlayer.create(effectPlayerConfig)?.let { player ->
            val oepConfig = OffscreenSimpleConfig.newBuilder(buffersQueue).build()

            player.setRenderConsistencyMode(ConsistencyMode.ASYNCHRONOUS_CONSISTENT)
            OffscreenEffectPlayer(
                this@OffscreenActivity.applicationContext,
                player,
                CAMERA_CAPTURE_SIZE,
                oepConfig
            )
        }

        offscreenEffectPlayer?.setImageProcessListener({ result ->
            handleProcessedImageResult(result)
        }, Handler(Looper.getMainLooper()))
    }

    private fun releaseOffscreen() {
        Log.d(TAG, "Release Offscreen")
        if (offscreenEffectPlayer != null) {
            offscreenEffectPlayer?.unloadEffect()
            offscreenEffectPlayer?.release()
            offscreenEffectPlayer = null
        }

        BanubaSdkManager.deinitialize()
    }

    private fun startCameraPreview() {
        Log.d(TAG, "Start Camera")
        camera?.openCameraAndStartPreview()
    }

    private fun stopCameraPreview() {
        Log.d(TAG, "Stop Camera")
        camera?.closeCamera()
        camera = null
    }

    private fun handleGrantedPermissions() {
        Log.d(TAG, "Handle Granted permissions")
        prepareOffscreen()
        startCameraPreview()
    }

    private fun checkAllPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleProcessedImageResult(result: ImageProcessResult) {
        val buffer: ByteBuffer = result.buffer
        buffersQueue.retainBuffer(buffer)

        buffer.position(result.getPlaneOffset(0))

        val strideY: Int = result.getRowStride(0)
        buffer.limit(result.getPlaneOffset(0) + strideY * result.getPlaneHeight(0))

        val dataY: ByteBuffer = buffer.slice()
        buffer.position(result.getPlaneOffset(1))

        val strideU: Int = result.getRowStride(1)
        buffer.limit(result.getPlaneOffset(1) + strideU * result.getPlaneHeight(1))

        val dataU: ByteBuffer = buffer.slice()
        buffer.position(result.getPlaneOffset(2))

        val strideV: Int = result.getRowStride(2)
        buffer.limit(result.getPlaneOffset(2) + strideV * result.getPlaneHeight(2))

        val dataV: ByteBuffer = buffer.slice()
        val width: Int = result.width
        val height: Int = result.height

        glI420Renderer.drawI420Image(
            dataY, strideY,
            dataU, strideU,
            dataV, strideV,
            width, height,
            result.orientation.imageOrientationAngle
        )
        glSurfaceView?.requestRender()
    }
}