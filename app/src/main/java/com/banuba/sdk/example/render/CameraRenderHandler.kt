package com.banuba.sdk.example.render

import android.os.Message
import com.banuba.sdk.internal.WeakHandler
import java.lang.RuntimeException

class CameraRenderHandler(cameraRenderThread: BaseCameraRenderThread)
    : WeakHandler<BaseCameraRenderThread>(cameraRenderThread) {

    companion object {
        private const val MSG_SHUTDOWN = 0
        private const val MSG_SURFACE_CREATED = 1
        private const val MSG_SURFACE_CHANGED = 2
        private const val MSG_SURFACE_DESTROYED = 3
        private const val MSG_DRAW_FRAME = 4
    }

    fun sendSurfaceCreated() {
        sendMessage(obtainMessage(MSG_SURFACE_CREATED))
    }

    fun sendSurfaceChanged(width: Int, height: Int) {
        sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height))
    }

    fun sendSurfaceDestroyed() {
        sendMessage(obtainMessage(MSG_SURFACE_DESTROYED))
    }

    fun sendDrawFrame() {
        sendMessage(obtainMessage(MSG_DRAW_FRAME))
    }

    fun sendShutdown() {
        removeCallbacksAndMessages(null)
        sendMessage(obtainMessage(MSG_SHUTDOWN))
    }

    override fun handleMessage(msg: Message) {
        thread?.run {
            when (msg.what) {
                MSG_SHUTDOWN -> shutdown()
                MSG_SURFACE_CREATED -> handleSurfaceCreated()
                MSG_SURFACE_CHANGED -> handleSurfaceChanged(msg.arg1, msg.arg2)
                MSG_SURFACE_DESTROYED -> handleSurfaceDestroyed()
                MSG_DRAW_FRAME -> handleDrawFrame()
                else -> throw RuntimeException("Unknown message: ${msg.what}")
            }
        }
    }
}