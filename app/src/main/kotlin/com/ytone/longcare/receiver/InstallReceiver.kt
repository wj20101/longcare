package com.ytone.longcare.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.common.utils.FileProviderHelper
import com.ytone.longcare.worker.DownloadWorker
import java.io.File

class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.DOWNLOAD_COMPLETE") {
            val filePath = intent.getStringExtra(DownloadWorker.KEY_FILE_PATH)
            if (filePath != null) {
                installApk(context, filePath)
            }
        }
    }

    private fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri = FileProviderHelper.getUriForFile(context, file)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        context.startActivity(intent)
    }
}