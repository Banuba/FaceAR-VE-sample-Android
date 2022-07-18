package com.banuba.sdk.example.render

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import android.view.SurfaceHolder
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.banuba.sdk.internal.BaseWorkThread
import com.banuba.sdk.internal.gl.EglCore
import com.banuba.sdk.internal.gl.GlUtils
import com.banuba.sdk.internal.gl.OffscreenSurface
import com.banuba.sdk.internal.gl.WindowSurface
import com.banuba.sdk.offscreen.OffscreenEffectPlayer

abstract class BaseCameraRenderThread(
    private val surfaceHolder: SurfaceHolder,
    protected val offscreenEffectPlayer: OffscreenEffectPlayer,
    private val size: Size
) : BaseWorkThread<CameraRenderHandler>("CameraRenderThread"), ICameraRenderer {

    private val identity = GlUtils.getIdentityMatrix()

    protected lateinit var renderHandler: CameraRenderHandler

    private lateinit var eglCore: EglCore

    protected var windowSurface: WindowSurface? = null

    private val surfaceHolderCallback = object : SurfaceHolder.Callback {
        @MainThread
        override fun surfaceCreated(holder: SurfaceHolder) {
            renderHandler.sendSurfaceCreated()
        }

        @MainThread
        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            renderHandler.sendSurfaceChanged(width, height)
            renderHandler.sendDrawFrame()
        }

        @MainThread
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            renderHandler.sendSurfaceDestroyed()
        }
    }

    init {
        Matrix.setIdentityM(identity, 0)
        surfaceHolder.addCallback(surfaceHolderCallback)
    }

    @CallSuper
    @WorkerThread
    override fun preRunInit() {
        eglCore = EglCore(null, 0x2)
        OffscreenSurface(eglCore, 16, 16).makeCurrent()
    }

    @CallSuper
    @WorkerThread
    override fun postRunClear() {
        eglCore.release()
    }

    override fun constructHandler(): CameraRenderHandler {
        return CameraRenderHandler(this).also { renderHandler = it }
    }

    @WorkerThread
    override fun handleSurfaceCreated() {
        windowSurface = WindowSurface(eglCore, surfaceHolder.surface, false)
        windowSurface?.makeCurrent()

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        GlUtils.checkGlErrorNoException("prepareGl")

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    }

    @WorkerThread
    override fun handleSurfaceChanged(width: Int, height: Int) {
        setViewPortWithAspect(width, height, size)
    }

    @WorkerThread
    override fun handleSurfaceDestroyed() {
        eglCore.makeNothingCurrent()
        windowSurface?.release()
        windowSurface = null
    }

    private fun setViewPortWithAspect(surfaceWidth: Int, surfaceHeight: Int, size: Size) {
        val renderMin = size.width.coerceAtMost(size.height)
        val renderMax = size.width.coerceAtLeast(size.height)

        val ratioW = surfaceWidth / renderMin.toFloat()
        val ratioH = surfaceHeight / renderMax.toFloat()

        val rationMin = ratioW.coerceAtMost(ratioH)

        val sideW = (rationMin * renderMin).toInt()
        val sideH = (rationMin * renderMax).toInt()

        GLES20.glViewport(
            (surfaceWidth - sideW) / 2,
            (surfaceHeight - sideH) / 2,
            sideW,
            sideH
        )
    }
}