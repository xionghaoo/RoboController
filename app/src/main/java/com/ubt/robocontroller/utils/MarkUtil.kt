package com.ubt.robocontroller.utils

import android.os.Environment
import java.io.File

class MarkUtil {
    companion object {
        fun isRunMode(): Boolean {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val f1 = File(downloadDir, "module/touchscreen/userdata/Homography.dat")
            val f2 = File(downloadDir, "module/touchscreen/userdata/ThresholdTemplate.dat")
            return f1.exists() && f2.exists()
        }
    }
}