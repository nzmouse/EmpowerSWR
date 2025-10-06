package com.empowerswr.luksave

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import timber.log.Timber
import java.io.File

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("DownloadReceiver: Broadcast received, action: ${intent?.action}")
        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
        Timber.d("DownloadReceiver: Broadcast download ID: $id")
        val mainActivity = context?.findActivity() as? MainActivity
        val filename = mainActivity?.getDownloadFilename(id)
        if (filename != null) {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename.replace("+", " ").replace("%20", " ").trim())
            Timber.d("DownloadReceiver: Checking file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
            if (file.exists() && file.length() > 0 && file.extension.lowercase() == "pdf") {
                MainActivity.downloadCompleteFlowInternal.tryEmit(id to filename)
                Timber.i("DownloadReceiver: Emitted download complete for ID: $id, filename: $filename")
            } else {
                Timber.e("DownloadReceiver: File invalid: exists=${file.exists()}, size=${file.length()}, extension=${file.extension}")
            }
            mainActivity?.removeDownload(id)
        } else {
            Timber.w("DownloadReceiver: No filename found for download ID: $id")
        }
    }
}