package com.banuba.sdk.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.banuba.sdk.arcloud.di.ArCloudKoinModule
import com.banuba.sdk.cameraui.domain.MODE_RECORD_VIDEO
import com.banuba.sdk.manager.BanubaSdkManager
import com.banuba.sdk.ve.flow.VideoCreationActivity
import kotlinx.android.synthetic.main.acitivity_face_ar.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
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

    private var editorKoinModules: List<Module> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.acitivity_face_ar)
        startKoin {
            androidContext(applicationContext)
        }
        faceArApplyMaskButton.setOnClickListener {
            applyMask()
        }
        faceArOpenEditorButton.setOnClickListener {
            openVideoEditor()
        }
    }

    override fun onStart() {
        super.onStart()
        if (editorKoinModules.isNotEmpty()) {
            destroyEditor()
        }
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

    private fun openVideoEditor() {
        destroyFaceAr()
        initializeEditor()
        val intent = VideoCreationActivity.buildIntent(this, MODE_RECORD_VIDEO)
        startActivity(intent)
    }

    private fun initializeFaceAr() {
        BanubaSdkManager.deinitialize()
        BanubaSdkManager.initialize(applicationContext, getString(R.string.effect_player_token))
        banubaSdkManager = BanubaSdkManager(applicationContext)
    }

    private fun destroyFaceAr() {
        banubaSdkManager?.closeCamera()
        banubaSdkManager?.releaseSurface()
        banubaSdkManager = null
        BanubaSdkManager.deinitialize()
    }

    private fun initializeEditor() {
        editorKoinModules = listOf(
            ArCloudKoinModule().module,
            VideoEditorKoinModule().module
        )
        loadKoinModules(editorKoinModules)
    }

    private fun destroyEditor() {
        unloadKoinModules(editorKoinModules)
        editorKoinModules = emptyList()
    }

    private fun checkAllPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}