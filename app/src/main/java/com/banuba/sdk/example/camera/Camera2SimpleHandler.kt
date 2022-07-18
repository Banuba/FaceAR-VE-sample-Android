package com.banuba.sdk.example.camera

import android.os.Message
import com.banuba.sdk.internal.WeakHandler
import java.lang.RuntimeException

class Camera2SimpleHandler(cameraThread: Camera2SimpleThread)
    : WeakHandler<Camera2SimpleThread>(cameraThread) {

    companion object {
        private const val MSG_SHUTDOWN = 0
        private const val MSG_OPEN_CAMERA = 1
        private const val MSG_CLOSE_CAMERA = 2
    }

    fun sendOpenCamera() {
        sendMessage(obtainMessage(MSG_OPEN_CAMERA))
    }

    fun sendCloseCamera() {
        sendMessage(obtainMessage(MSG_CLOSE_CAMERA))
    }

    fun sendShutdown() {
        removeCallbacksAndMessages(null)
        sendMessage(obtainMessage(MSG_CLOSE_CAMERA))
        sendMessage(obtainMessage(MSG_SHUTDOWN))
    }

    override fun handleMessage(msg: Message) {
        thread?.run {
            when (msg.what) {
                MSG_SHUTDOWN -> shutdown()
                MSG_OPEN_CAMERA -> handleOpenCamera()
                MSG_CLOSE_CAMERA -> handleReleaseCamera()
                else -> throw RuntimeException("Unknown message: ${msg.what}")
            }
        }
    }
}