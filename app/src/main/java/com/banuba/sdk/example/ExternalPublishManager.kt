package com.banuba.sdk.example

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.banuba.sdk.core.ext.getMimeTypeByContentResolver
import com.banuba.sdk.core.media.MediaFileNameHelper
import com.banuba.sdk.export.data.ExportedVideo
import com.banuba.sdk.export.data.PublishManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ExternalPublishManager(
    private val context: Context,
    private val albumName: String,
    private val mediaFileNameHelper: MediaFileNameHelper,
    private val dispatcher: CoroutineDispatcher
) : PublishManager {

    companion object {
        private const val TAG = "ExternalPublishManager"
    }

    override suspend fun publish(video: ExportedVideo) {
        withContext(dispatcher) {
            val videoFilePath = video.sourceUri.path
            if (videoFilePath.isNullOrEmpty()) return@withContext
            val publishedFileName = "${mediaFileNameHelper.generatePublishName()}.mp4"
            val mimeType = video.sourceUri.getMimeTypeByContentResolver(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val newVideoDetails = ContentValues().apply {
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Video.Media.DISPLAY_NAME,
                        publishedFileName
                    )
                    put(
                        MediaStore.Video.Media.TITLE,
                        publishedFileName
                    )
                    put(MediaStore.Video.Media.ALBUM, albumName)
                    put(
                        MediaStore.Video.Media.DURATION,
                        video.durationMs.toInt()
                    )
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/$albumName"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                with(context.contentResolver) {
                    val videoCollectionUri =
                        MediaStore.Video.Media.getContentUri(
                            MediaStore.VOLUME_EXTERNAL_PRIMARY
                        )
                    insert(videoCollectionUri, newVideoDetails)?.let { publishUri ->
                        val exportedFile = File(videoFilePath)
                        try {
                            openOutputStream(publishUri)?.let { outputStream ->
                                copyInputToOutput(exportedFile.inputStream(), outputStream)
                                newVideoDetails.clear()
                                newVideoDetails.put(MediaStore.Video.Media.IS_PENDING, 0)
                                update(publishUri, newVideoDetails, null, null)
                            }
                        } catch (e: IOException) {
                            Log.w(TAG, e)
                            return@withContext
                        }
                    }
                }
            } else {
                val publishDir = File(
                    "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)}/$albumName"
                )
                var success = true
                if (!publishDir.exists()) {
                    success = publishDir.mkdirs()
                }
                if (success) {
                    val exportedFile = File(videoFilePath)
                    val publishedFile =
                        File(publishDir, publishedFileName)
                    try {
                        copyInputToOutput(exportedFile.inputStream(), publishedFile.outputStream())
                    } catch (e: IOException) {
                        Log.w(TAG, e)
                        publishedFile.delete()
                        return@withContext
                    }
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(publishedFile.toString()), arrayOf(mimeType)
                    ) { _, _ -> }
                }
            }
        }
    }

    private fun copyInputToOutput(inputStream: InputStream, outputStream: OutputStream) {
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }
}