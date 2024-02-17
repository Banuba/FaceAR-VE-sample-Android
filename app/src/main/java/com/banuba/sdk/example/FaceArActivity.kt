package com.banuba.sdk.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.banuba.sdk.example.databinding.AcitivityFaceArBinding
import com.banuba.sdk.manager.BanubaSdkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FaceArActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001

        private const val EFFECT_NAME = "AsaiLines"

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
        )
    }

    private lateinit var binding: AcitivityFaceArBinding

    private var banubaSdkManager: BanubaSdkManager? = null
    private val effectHelper = BanubaEffectHelper()

    private val videoEditorLauncher = registerForActivityResult<Application, String?>(VideoEditorLaunchContract()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AcitivityFaceArBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.faceArApplyMaskButton.setOnClickListener {
            applyMask()
        }
        binding.faceArOpenEditorButton.setOnClickListener {
            openVideoEditor()
        }
    }

    /*
     * Banuba Video Editor SDK specific code
     */

    private fun openVideoEditor() {
        destroyFaceAr()

        videoEditorLauncher.launch(application)
    }

    /**
     * Banuba Face AR SDK specific code
     */

    private fun prepareFaceAR() {
        if (banubaSdkManager == null) {
            initializeFaceAr()
        }
        banubaSdkManager?.attachSurface(binding.faceArSurfaceView)
        if (checkAllPermissionsGranted()) {
            banubaSdkManager?.openCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun initializeFaceAr() {
        BanubaSdkManager.deinitialize()
        BanubaSdkManager.initialize(applicationContext, SampleApp.LICENSE_TOKEN)
        banubaSdkManager = BanubaSdkManager(applicationContext)
    }

    private fun destroyFaceAr() {
        banubaSdkManager?.closeCamera()
        banubaSdkManager?.releaseSurface()
        banubaSdkManager = null
        BanubaSdkManager.deinitialize()
    }

    private fun applyMask() {
        val manager = banubaSdkManager?.effectManager
        if (manager == null) {
            Log.w(
                "FaceArActivity",
                "Cannot apply effect: Banuba Face AR Effect Player is not initialized"
            )
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val effect = effectHelper.prepareEffect(assets, EFFECT_NAME)
                manager.loadAsync(effect.uri.toString())
            }
        }
    }

    override fun onStart() {
        super.onStart()
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