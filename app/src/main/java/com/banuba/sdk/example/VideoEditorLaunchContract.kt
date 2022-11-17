package com.banuba.sdk.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.banuba.sdk.cameraui.data.PipConfig
import com.banuba.sdk.ve.flow.VideoCreationActivity

/**
 * Activity contract for starting Banuba Video Editor SDK
 */
class VideoEditorLaunchContract : ActivityResultContract<Application, String?>() {

    private var app: SampleApp? = null

    override fun createIntent(context: Context, input: Application): Intent {
        app = input as? SampleApp

        // Prepare Video Editor SDK - it will initialize DI for the SDK
        app?.prepareVideoEditor()

        return VideoCreationActivity.startFromCamera(
            context = context,
            // setup data that will be acceptable during export flow
            additionalExportData = null,
            // set TrackData object if you open VideoCreationActivity with preselected music track
            audioTrackData = null,
            // set PiP video configuration
            pictureInPictureConfig = PipConfig(
                video = Uri.EMPTY,
                openPipSettings = false
            )
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        // Release Video Editor SDK - it will release DI for the SDK
        app?.releaseVideoEditor()
        return null
    }
}