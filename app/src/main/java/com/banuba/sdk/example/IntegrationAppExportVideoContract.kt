package com.banuba.sdk.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.banuba.sdk.export.data.ExportResult
import com.banuba.sdk.export.utils.EXTRA_EXPORTED_SUCCESS

class IntegrationAppExportVideoContract: ActivityResultContract<Intent?, ExportResult?>() {

    override fun createIntent(context: Context, input: Intent?): Intent {
        check(input != null) {
            "Can not create Intent to create video"
        }
        return input
    }

    override fun parseResult(resultCode: Int, intent: Intent?): ExportResult? {
        if (resultCode == Activity.RESULT_OK) {
            return intent?.getParcelableExtra(EXTRA_EXPORTED_SUCCESS) as? ExportResult.Success
        }
        return ExportResult.Inactive
    }

}