package com.banuba.sdk.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.banuba.sdk.arcloud.di.ArCloudKoinModule
import com.banuba.sdk.audiobrowser.di.AudioBrowserKoinModule
import com.banuba.sdk.effect_player.EffectPlayer
import com.banuba.sdk.effect_player.EffectPlayerConfiguration
import com.banuba.sdk.effect_player.NnMode
import com.banuba.sdk.effectplayer.adapter.BanubaEffectPlayerKoinModule
import com.banuba.sdk.example.camera.Camera2SimpleHandler
import com.banuba.sdk.example.camera.Camera2SimpleThread
import com.banuba.sdk.example.render.CameraRenderHandler
import com.banuba.sdk.example.render.CameraRenderSurfaceTexture
import com.banuba.sdk.export.di.VeExportKoinModule
import com.banuba.sdk.gallery.di.GalleryKoinModule
import com.banuba.sdk.manager.BanubaSdkManager
import com.banuba.sdk.offscreen.OffscreenEffectPlayer
import com.banuba.sdk.offscreen.OffscreenSimpleConfig
import com.banuba.sdk.playback.di.VePlaybackSdkKoinModule
import com.banuba.sdk.recognizer.FaceSearchMode
import com.banuba.sdk.token.storage.di.TokenStorageKoinModule
import com.banuba.sdk.token.storage.license.EditorLicenseManager
import com.banuba.sdk.ve.di.VeSdkKoinModule
import com.banuba.sdk.ve.flow.VideoCreationActivity
import com.banuba.sdk.ve.flow.di.VeFlowKoinModule
import kotlinx.android.synthetic.main.acitivity_face_ar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val size = Size(1280, 720)

    private var effectPlayer: EffectPlayer? = null

    private var offscreenEffectPlayer: OffscreenEffectPlayer? = null

    private var videoEditorKoinModules: List<Module> = emptyList()

    private var cameraHandler: Camera2SimpleHandler? = null

    private var renderHandler: CameraRenderHandler? = null

    private var isMaskApplied = false

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
        CoroutineScope(Dispatchers.Main.immediate).launch {
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
        if (offscreenEffectPlayer == null) {
            initializeFaceAr()
        }
        offscreenEffectPlayer?.let {
            renderHandler = CameraRenderSurfaceTexture(
                surfaceHolder = faceArSurfaceView.holder,
                offscreenEffectPlayer = it,
                size = size
            ).startAndGetHandler()
        }
        if (checkAllPermissionsGranted()) {
            cameraHandler?.sendOpenCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun initializeFaceAr() {
        BanubaSdkManager.deinitialize()
        BanubaSdkManager.initialize(applicationContext, getString(R.string.banuba_token))
        effectPlayer = EffectPlayer.create(
            EffectPlayerConfiguration(
                size.width,
                size.height,
                NnMode.ENABLE,
                FaceSearchMode.GOOD,
                false,
                false
            )
        )?.apply {
            offscreenEffectPlayer = OffscreenEffectPlayer(
                applicationContext,
                this,
                size,
                OffscreenSimpleConfig.newBuilder(null).build()
            ).apply {
                cameraHandler = Camera2SimpleThread(
                    context = applicationContext,
                    offscreenEffectPlayer = this,
                    preferredPreviewSize = size
                ).startAndGetHandler()
            }
        }
    }

    private fun destroyFaceAr() {
        cameraHandler?.sendShutdown()
        renderHandler?.sendShutdown()
        offscreenEffectPlayer?.release()
        renderHandler = null
        offscreenEffectPlayer = null
        isMaskApplied = false
        BanubaSdkManager.deinitialize()
    }

    private fun applyMask() {
        offscreenEffectPlayer?.let {
            if (isMaskApplied) {
                it.unloadEffect()
            } else {
                val maskUrl = Uri.parse(BanubaSdkManager.getResourcesBase())
                    .buildUpon()
                    .appendPath("effects")
                    .appendPath("Beauty")
                    .build()
                    .toString()
                it.loadEffect(maskUrl)
            }
            isMaskApplied = !isMaskApplied
        }
    }

    override fun onStart() {
        super.onStart()
        releaseVideoEditor()

        prepareFaceAR()
    }

    override fun onStop() {
        super.onStop()
        cameraHandler?.sendCloseCamera()
        renderHandler?.sendShutdown()
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
            cameraHandler?.sendOpenCamera()
        } else {
            Toast.makeText(applicationContext, "Please grant permission.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun checkAllPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}