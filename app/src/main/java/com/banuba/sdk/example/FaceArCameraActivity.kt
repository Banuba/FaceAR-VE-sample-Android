package com.banuba.sdk.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.banuba.sdk.arcloud.di.ArCloudKoinModule
import com.banuba.sdk.audiobrowser.di.AudioBrowserKoinModule
import com.banuba.sdk.camera.Facing
import com.banuba.sdk.effectplayer.adapter.BanubaEffectPlayerKoinModule
import com.banuba.sdk.entity.RecordedVideoInfo
import com.banuba.sdk.export.di.VeExportKoinModule
import com.banuba.sdk.gallery.di.GalleryKoinModule
import com.banuba.sdk.manager.BanubaSdkManager
import com.banuba.sdk.manager.IEventCallback
import com.banuba.sdk.playback.di.VePlaybackSdkKoinModule
import com.banuba.sdk.token.storage.di.TokenStorageKoinModule
import com.banuba.sdk.token.storage.license.EditorLicenseManager
import com.banuba.sdk.token.storage.provider.TokenProvider
import com.banuba.sdk.types.Data
import com.banuba.sdk.ve.di.VeSdkKoinModule
import com.banuba.sdk.ve.flow.VideoCreationActivity
import com.banuba.sdk.ve.flow.di.VeFlowKoinModule
import com.banuba.sdk.veui.di.VeUiSdkKoinModule
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FaceArCameraActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001

        private const val VIDEO_EXT = ".mp4"

        private const val RECORD_BTN_SCALE_FACTOR = 1.3f

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private var isFrontCamera = true
    private var captureMic = true
    private var speedRecording = 1f
    private var videosStack = ArrayDeque<Uri>()

    private val cameraEventCallback = object : IEventCallback {
        override fun onCameraOpenError(p0: Throwable) {
        }

        override fun onCameraStatus(opened: Boolean) {
            runOnUiThread { cameraSwitchButton.isClickable = opened }
        }

        override fun onScreenshotReady(bitmap: Bitmap) {
        }

        override fun onHQPhotoReady(bitmap: Bitmap) {
        }

        override fun onVideoRecordingFinished(info: RecordedVideoInfo) {
            videosStack.addLast(File(info.filePath).toUri())
            runOnUiThread {
                updateViews()
            }
        }

        override fun onVideoRecordingStatusChange(p0: Boolean) {
        }

        override fun onImageProcessed(bitmap: Bitmap) {
        }

        override fun onFrameRendered(p0: Data, p1: Int, p2: Int) {
        }
    }

    private val selectVideos = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        if (it.isNotEmpty()) {
            openEditor(it)
        }
    }

    private var videoEditorKoinModules: List<Module> = emptyList()

    private val timeBasedFileNameFormat = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.ENGLISH)

    private var banubaSdkManager: BanubaSdkManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        updateViews()

        applyBeautyButton.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                applyMaskButton.isChecked = false
                applyEffect("Beauty")
            } else {
                cancelEffect()
            }
        }

        applyMaskButton.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                applyBeautyButton.isChecked = false
                applyEffect("Retrowave")
            } else {
                cancelEffect()
            }
        }

        openEditorButton.setOnClickListener {
            openEditor(videosStack.toList())
        }

        removeLastVideoButton.setOnClickListener {
            videosStack.removeLastOrNull()
            updateViews()
        }

        cameraSwitchButton.setOnClickListener {
            it.animate().rotationBy(it.rotationX + 180).setDuration(250).start()
            it.isClickable = false
            isFrontCamera = if (!isFrontCamera) {
                banubaSdkManager?.setCameraFacing(Facing.FRONT, true) ?: false
            } else {
                banubaSdkManager?.setCameraFacing(Facing.BACK, false)?.not() ?: false
            }
        }

        cameraMicButton.setOnCheckedChangeListener { _, checked ->
            captureMic = checked
        }

        cameraSpeedButton.apply {
            text = speedRecording.toString()
            setOnClickListener {
                speedRecording = if (speedRecording == 1f) .5f else 1f
                (it as TextView).text = speedRecording.toString()
            }
        }

        galleryButton.setOnClickListener {
            selectVideos.launch("video/*")
        }

        setupShutterButton()
    }

    override fun onStart() {
        super.onStart()
        releaseVideoEditor()

        prepareFaceAR()
    }

    override fun onResume() {
        super.onResume()
        banubaSdkManager?.effectPlayer?.playbackPlay()
    }

    override fun onPause() {
        super.onPause()
        banubaSdkManager?.effectPlayer?.playbackPause()
    }

    override fun onStop() {
        super.onStop()
        banubaSdkManager?.closeCamera()
        banubaSdkManager?.releaseSurface()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyFaceAr()
        destroyEditor()
        stopKoin()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (checkAllPermissionsGranted()) {
            banubaSdkManager?.openCamera()
        } else {
            Toast.makeText(applicationContext, "Please grant permission.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun applyEffect(name: String) {
        val effectManager = banubaSdkManager?.effectManager ?: return
        val maskUrl = Uri.parse(BanubaSdkManager.getResourcesBase())
            .buildUpon()
            .appendPath("effects")
            .appendPath(name)
            .build()
            .toString()
        effectManager.loadAsync(maskUrl)
    }

    private fun cancelEffect() {
        banubaSdkManager?.effectManager?.loadAsync("maskUrl") ?: return
    }

    private fun prepareFaceAR() {
        if (banubaSdkManager == null) {
            initializeFaceAr()
        }
        banubaSdkManager?.attachSurface(faceArSurfaceView)
        if (checkAllPermissionsGranted()) {
            banubaSdkManager?.openCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun initializeFaceAr() {
        BanubaSdkManager.deinitialize()
        BanubaSdkManager.initialize(applicationContext, getString(R.string.banuba_token))
        banubaSdkManager = BanubaSdkManager(applicationContext)
        banubaSdkManager?.setCallback(cameraEventCallback)
    }

    private fun destroyFaceAr() {
        banubaSdkManager?.closeCamera()
        banubaSdkManager?.setCallback(null)
        banubaSdkManager?.effectPlayer?.playbackStop()
        banubaSdkManager?.recycle()
        banubaSdkManager = null
        BanubaSdkManager.deinitialize()
    }

    private fun checkAllPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupShutterButton() {
        recordButton.setOnTouchListener { view: View?, motionEvent: MotionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    view?.apply {
                        animate()
                            .scaleX(RECORD_BTN_SCALE_FACTOR)
                            .scaleY(RECORD_BTN_SCALE_FACTOR)
                            .setDuration(100)
                            .start()
                    }
                    startVideoRecord()
                }
                MotionEvent.ACTION_UP -> {
                    view?.apply {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    stopVideoRecord()
                }
            }
            true
        }
    }

    private fun startVideoRecord() {
        val fileNamePath = File(externalCacheDir, getTimeBasedFileName()).absolutePath
        banubaSdkManager?.startVideoRecording(
            fileNamePath,
            checkAllPermissionsGranted() && captureMic,
            null,
            speedRecording
        )
    }

    private fun stopVideoRecord() {
        banubaSdkManager?.stopVideoRecording()
    }

    private fun releaseVideoEditor() {
        if (videoEditorKoinModules.isNotEmpty()) {
            destroyEditor()
        }
    }

    private fun destroyEditor() {
        unloadKoinModules(videoEditorKoinModules)
        videoEditorKoinModules = emptyList()
    }

    private fun openEditor(videos: List<Uri>) {
        destroyFaceAr()
        initializeEditor()
        val intent = VideoCreationActivity.startFromTrimmer(this, predefinedVideos = videos.toTypedArray())
        startActivity(intent)
    }

    private fun initializeEditor() {
        videoEditorKoinModules = listOf(
            VeSdkKoinModule().module,
            VeExportKoinModule().module,
            VePlaybackSdkKoinModule().module,
            AudioBrowserKoinModule().module, // use this module only if you bought it
            ArCloudKoinModule().module,
            TokenStorageKoinModule().module,
            VeUiSdkKoinModule().module,
            VeFlowKoinModule().module,
            VideoEditorKoinModule().module,
            GalleryKoinModule().module,
            BanubaEffectPlayerKoinModule().module,
        )
        loadKoinModules(videoEditorKoinModules)
        CoroutineScope(Dispatchers.Main.immediate).launch {
            EditorLicenseManager.initialize(getString(R.string.banuba_token))
        }
    }

    private fun getTimeBasedFileName(): String {
        val name: String = timeBasedFileNameFormat.format(Date())
        return name + VIDEO_EXT
    }

    private fun updateViews() {
        countOfVideos.text = if (videosStack.size > 0) {
            videosStack.size.toString()
        } else {
            ""
        }
        openEditorButton.isVisible = videosStack.size > 0
        galleryButton.isVisible = videosStack.size == 0
    }
}
