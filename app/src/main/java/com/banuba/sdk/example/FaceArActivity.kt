package com.banuba.sdk.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.banuba.sdk.arcloud.di.ArCloudKoinModule
import com.banuba.sdk.audiobrowser.di.AudioBrowserKoinModule
import com.banuba.sdk.effectplayer.adapter.BanubaEffectPlayerKoinModule
import com.banuba.sdk.export.di.VeExportKoinModule
import com.banuba.sdk.gallery.di.GalleryKoinModule
import com.banuba.sdk.manager.BanubaSdkManager
import com.banuba.sdk.playback.di.VePlaybackSdkKoinModule
import com.banuba.sdk.token.storage.CoroutineDispatcherProvider
import com.banuba.sdk.token.storage.di.TokenStorageKoinModule
import com.banuba.sdk.token.storage.license.EditorLicenseManager
import com.banuba.sdk.ve.di.VeSdkKoinModule
import com.banuba.sdk.ve.flow.VideoCreationActivity
import com.banuba.sdk.ve.flow.di.VeFlowKoinModule
import kotlinx.android.synthetic.main.acitivity_face_ar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

class FaceArActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
        )
    }

    private var banubaSdkManager: BanubaSdkManager? = null

    private var videoEditorKoinModules: List<Module> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.acitivity_face_ar)

        faceArApplyMaskButton.setOnClickListener {
            applyMask()
        }
        faceArOpenEditorButton.setOnClickListener {
            openVideoEditor()
        }
    }

    /*
     * Banuba Video Editor SDK specific code
     */
    private fun releaseVideoEditor() {
        if (videoEditorKoinModules.isNotEmpty()) {
            destroyEditor()
        }
    }


    private fun initializeEditor() {
        videoEditorKoinModules = listOf(
            VeSdkKoinModule().module,
            VeExportKoinModule().module,
            VePlaybackSdkKoinModule().module,
            AudioBrowserKoinModule().module, // use this module only if you bought it
            ArCloudKoinModule().module,
            TokenStorageKoinModule().module,
            VeFlowKoinModule().module,
            VideoEditorKoinModule().module,
            GalleryKoinModule().module,
            BanubaEffectPlayerKoinModule().module,
        )
        loadKoinModules(videoEditorKoinModules)
        CoroutineScope(CoroutineDispatcherProvider.Immediate).launch {
            EditorLicenseManager.initialize(getString(R.string.banuba_token))
        }
    }

    private fun destroyEditor() {
        unloadKoinModules(videoEditorKoinModules)
        videoEditorKoinModules = emptyList()
    }

    private fun openVideoEditor() {
        destroyFaceAr()
        initializeEditor()
        val intent = VideoCreationActivity.startFromCamera(this)
        startActivity(intent)
    }

    /**
     * Banuba Face AR SDK specific code
     */

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
    }

    private fun destroyFaceAr() {
        banubaSdkManager?.closeCamera()
        banubaSdkManager?.releaseSurface()
        banubaSdkManager = null
        BanubaSdkManager.deinitialize()
    }

    private fun applyMask() {
        val effectManager = this.banubaSdkManager?.effectManager ?: return
        val maskUrl = if (effectManager.current()?.url() != "") {
            ""
        } else {
            Uri.parse(BanubaSdkManager.getResourcesBase())
                .buildUpon()
                .appendPath("effects")
                .appendPath("Beauty")
                .build()
                .toString()
        }
        effectManager.loadAsync(maskUrl)
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

    private fun checkAllPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}