package com.banuba.sdk.example.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraAccessException
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.banuba.sdk.camera.Facing
import com.banuba.sdk.internal.utils.CameraUtils
import com.banuba.sdk.offscreen.ImageOrientation
import com.banuba.sdk.offscreen.OffscreenEffectPlayer

class Camera2Simple(
    context: Context,
    effectPlayer: OffscreenEffectPlayer,
    private val cameraManager: CameraManager,
    private val preferredPreviewSize: Size
) : ICamera2Simple {

    companion object {
        private const val TAG = "Camera2Simple"

        private const val FIXED_FRAME_RATE = 30
    }

    private val handler = Handler(requireNotNull(Looper.myLooper()))

    private val displaySurfaceRotation = context.display?.rotation ?: Surface.ROTATION_0

    private val rotation = 90 * displaySurfaceRotation

    private var sensorOrientation = 0

    private var previewSize = preferredPreviewSize

    private var isCameraOpened = false

    private var cameraDevice: CameraDevice? = null

    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var imageReader: ImageReader? = null

    private var captureSession: CameraCaptureSession? = null

    private val cameraFacing = Facing.FRONT

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        reader.acquireLatestImage()?.let { image ->
            try {
                val imageOrientation = (sensorOrientation + rotation) % 360
                val isRequireMirroring = cameraFacing == Facing.FRONT
                val frameOrientation =
                    ImageOrientation.getForCamera(imageOrientation, 0, displaySurfaceRotation, isRequireMirroring)
                effectPlayer.processImage(image, frameOrientation)
            } catch (e: Exception) {
                Log.e(TAG, "Error while process camera image", e)
            }
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            onCameraCloseState(camera)
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onCameraCloseState(camera)
        }

        override fun onClosed(camera: CameraDevice) {
            onCameraCloseState(camera)
        }
    }

    override fun openCameraAndStartPreview() {
        openCamera()
    }

    override fun stopPreviewAndCloseCamera() {
        closeCamera()
    }

    private fun openCamera() {
        if (!isCameraOpened) {
            try {
                for (cameraId in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    facing?.takeIf { it == cameraFacing.value }?.let {
                        setupCameraCharacteristics(characteristics)
                        cameraManager.openCamera(cameraId, stateCallback, null)
                        isCameraOpened = true
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error when camera open", e)
            }
        }
    }

    private fun closeCamera() {
        isCameraOpened = false
        handler.removeCallbacksAndMessages(null)
        captureSession?.let {
            try {
                it.stopRepeating()
            } catch (e: Exception) {
                Log.e(TAG, "Error when camera close", e)
            }
            it.close()
            captureSession = null
        }
        cameraDevice?.let {
            it.close()
            cameraDevice = null
        }
        imageReader?.setOnImageAvailableListener(null, null)
    }

    private fun setupCameraCharacteristics(characteristics: CameraCharacteristics) =
        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let {
            sensorOrientation = CameraUtils.getSensorOrientation(characteristics)
            previewSize = CameraUtils.getPreviewSize(characteristics, preferredPreviewSize)
        }

    private fun createCameraPreviewSession() {
        try {
            createPreviewRequest()
            cameraDevice?.createCaptureSession(
                listOf(imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            previewRequestBuilder?.build()?.let {
                                cameraCaptureSession.setRepeatingRequest(
                                    it, null, handler
                                )
                            }
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Error when configure camera preview session", e)
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                        Log.e(TAG, "Error when configure camera preview session")
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error when create camera preview session", e)
        }
    }

    @Throws(CameraAccessException::class)
    private fun createPreviewRequest() {
        previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            previewSize.width, previewSize.height, ImageFormat.YUV_420_888,5
        ).apply {
            setOnImageAvailableListener(onImageAvailableListener, null)
            previewRequestBuilder?.let {
                it.addTarget(surface)
                it.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
                it.set(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range.create(FIXED_FRAME_RATE, FIXED_FRAME_RATE)
                )
            }
        }
    }

    private fun onCameraCloseState(camera: CameraDevice) {
        camera.close()
        if (cameraDevice == camera) {
            cameraDevice = null
            isCameraOpened = false
        }
    }
}