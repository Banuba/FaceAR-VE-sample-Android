package com.banuba.sdk.example.render

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import androidx.annotation.WorkerThread
import com.banuba.sdk.internal.gl.GLFullRectTexture
import com.banuba.sdk.internal.gl.GlUtils
import com.banuba.sdk.offscreen.OffscreenEffectPlayer

class CameraRenderSurfaceTexture(
    surfaceHolder: SurfaceHolder,
    offscreenEffectPlayer: OffscreenEffectPlayer,
    size: Size
) : BaseCameraRenderThread(surfaceHolder, offscreenEffectPlayer, size) {

    companion object {
        private const val TAG = "CameraRenderSurfaceTexture"
    }

    private lateinit var externalDrawTextureRGB: GLFullRectTexture

    private var externalTextureId = 0

    private lateinit var surfaceTexture: SurfaceTexture

    @WorkerThread
    override fun preRunInit() {
        super.preRunInit()

        externalDrawTextureRGB = GLFullRectTexture(true)
        externalTextureId = GlUtils.createExternalTextureObject()
        surfaceTexture = SurfaceTexture(externalTextureId).apply {
            setOnFrameAvailableListener(
                {
                    try {
                        it.updateTexImage()
                        renderHandler.sendDrawFrame()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while draw processed frame", e)
                    }
                },
                renderHandler
            )
            offscreenEffectPlayer.setSurfaceTexture(this)
        }
    }

    @WorkerThread
    override fun postRunClear() {
        super.postRunClear()
        offscreenEffectPlayer.setSurfaceTexture(null)
        surfaceTexture.release()
    }

    override fun handleDrawFrame() {
        windowSurface?.let {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            externalDrawTextureRGB.draw(externalTextureId)
            it.swapBuffers()
        }
    }
}