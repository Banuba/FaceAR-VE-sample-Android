package com.banuba.sdk.example.render

interface ICameraRenderer {
    fun handleSurfaceCreated()

    fun handleSurfaceChanged(width: Int, height: Int)

    fun handleSurfaceDestroyed()

    fun handleDrawFrame()

    fun shutdown()
}