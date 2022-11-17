package com.banuba.sdk.example

import com.banuba.sdk.cameraui.data.CameraTimerStateProvider
import com.banuba.sdk.cameraui.data.TimerEntry

class IntegrationTimerStateProvider : CameraTimerStateProvider {

    override val timerStates = listOf(
        TimerEntry(
            durationMs = 0
        ),
        TimerEntry(
            durationMs = 3000
        )
    )
}