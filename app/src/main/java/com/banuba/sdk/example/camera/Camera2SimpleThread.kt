package com.banuba.sdk.example.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.annotation.WorkerThread
import com.banuba.sdk.internal.BaseWorkThread
import com.banuba.sdk.offscreen.OffscreenEffectPlayer

class Camera2SimpleThread(
    private val context: Context,
    private val offscreenEffectPlayer: OffscreenEffectPlayer,
    private val preferredPreviewSize: Size
) : BaseWorkThread<Camera2SimpleHandler>("Camera2SimpleThread") {


    private lateinit var cameraApi: ICamera2Simple

    @WorkerThread
    override fun preRunInit() {
        cameraApi = Camera2Simple(
            context = context,
            effectPlayer = offscreenEffectPlayer,
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager,
            preferredPreviewSize
        )
    }

    @WorkerThread
    override fun constructHandler(): Camera2SimpleHandler {
        return Camera2SimpleHandler(this)
    }

    fun handleOpenCamera() {
        cameraApi.openCameraAndStartPreview()
    }

    fun handleReleaseCamera() {
        cameraApi.stopPreviewAndCloseCamera()
    }
}