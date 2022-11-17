package com.banuba.sdk.example

import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import com.banuba.sdk.core.ext.isDirectory
import com.banuba.sdk.manager.BanubaSdkManager
import java.io.File
import java.io.IOException

/**
 * Banuba Face AR EffectPlayer can only apply effects stored on the internal memory of the device.
 * This util class prepares AR effect before applying in Banuba Face AR EffectPlayer.
 * You can use this implementation it in your project.
 */
class BanubaEffectHelper {

    companion object {
        const val TAG = "BanubaEffectHelper"

        private const val DIR_EFFECTS = "effects"
        private const val assetsEffectsDir = "bnb-resources/$DIR_EFFECTS"
    }

    fun prepareEffect(
        assetManager: AssetManager,
        assetEffectName: String
    ): Effect {
        val effectUri = Uri.parse(BanubaSdkManager.getResourcesBase())
            .buildUpon()
            .appendPath(DIR_EFFECTS)
            .appendPath(assetEffectName)
            .build()

        val file = File(effectUri.toString())
        copyResources(
            assetManager,
            file,
            "$assetsEffectsDir/$assetEffectName"
        )
        val uri = Uri.fromFile(file)
        val previewImagePath = uri
            .buildUpon()
            .appendPath("preview.png")
            .build()

        return Effect(effectUri, assetEffectName, previewImagePath)
    }

    private fun copyResources(
        assetManager: AssetManager,
        targetDir: File,
        assetRoot: String
    ) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        if (assetManager.isDirectory(assetRoot)) {
            assetManager.list(assetRoot)?.forEach { filename ->
                val sourcePath = Uri.parse(assetRoot)
                    .buildUpon()
                    .appendEncodedPath(filename)
                    .build()
                    .path ?: throw IllegalStateException("Source path cannot be null!")

                val destFile = File(targetDir, filename)

                if (assetManager.isDirectory(sourcePath)) {
                    destFile.mkdirs()
                    copyResources(
                        assetManager = assetManager,
                        targetDir = destFile,
                        assetRoot = sourcePath
                    )
                } else {
                    copyFile(assetManager, sourcePath, destFile)
                }
            }
        } else {
            copyFile(assetManager, assetRoot, targetDir)
        }
    }

    private fun copyFile(
        assetManager: AssetManager,
        sourcePath: String,
        desFile: File
    ) {
        try {
            assetManager.open(sourcePath).use { input ->
                desFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not copy file $sourcePath")
        }
    }

    data class Effect(
        val uri: Uri,
        val name: String,
        val previewImagePath: Uri
    )
}