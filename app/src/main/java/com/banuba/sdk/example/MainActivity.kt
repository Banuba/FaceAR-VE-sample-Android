package com.banuba.sdk.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.banuba.sdk.example.offscreen.OffscreenActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startFaceAR.setOnClickListener {
            checkVideoEditorLicenseState {
                startActivity(Intent(applicationContext, FaceArActivity::class.java))
            }
        }

        startOffscreenFaceAR.setOnClickListener {
            checkVideoEditorLicenseState {
                startActivity(Intent(applicationContext, OffscreenActivity::class.java))
            }
        }
    }

    private fun checkVideoEditorLicenseState(
        onActiveLicenseBlock: () -> Unit,
    ) {
        val videoEditor = (application as SampleApp).videoEditor
        if (videoEditor == null) {
            Log.e(
                "BanubaVideoEditor",
                "Cannot check license state. Please initialize Video Editor SDK"
            )
            showLicenseErrorMessage(SampleApp.ERR_SDK_NOT_INITIALIZED)
        } else {
            // Checking the license might take around 1 sec in the worst case.
            // Please optimize use if this method in your application for the best user experience
            videoEditor.getLicenseState { isValid ->
                if (isValid) {
                    // ✅ License is active, all good
                    // You can show button that opens Video Editor or
                    // Start Video Editor right away
                    onActiveLicenseBlock()
                } else {
                    // ❌ Use of Video Editor is restricted. License is revoked or expired.
                    showLicenseErrorMessage(SampleApp.ERR_LICENSE_REVOKED)
                }
            }
        }
    }

    private fun showLicenseErrorMessage(msg: String) {
        licenseErrorMessageView.run {
            text = msg
            visibility = View.VISIBLE
        }
    }
}