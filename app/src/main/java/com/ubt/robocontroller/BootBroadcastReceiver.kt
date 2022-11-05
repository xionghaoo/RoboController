package com.ubt.robocontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import com.ubt.robocontroller.uvc.service.UVCService
import xh.zero.core.utils.ToastUtil
import java.io.File

class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.intent.action.BOOT_COMPLETED") {
            ToastUtil.show(context, "启动触控服务")
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val f1 = File(downloadDir, "module/touchscreen/userdata/Homography.dat")
            val f2 = File(downloadDir, "module/touchscreen/userdata/ThresholdTemplate.dat")
            val pointFile = File(downloadDir, "MarkPoints.json")
            if (pointFile.exists() && f1.exists() && f2.exists()) {
                // 标注点文件和触控数据存在时启动触控运行服务
                val i = Intent(context, UVCService::class.java)
                i.putExtra(UVCService.EXTRA_BOOT_CMD, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context?.startForegroundService(i);
                } else {
                    context?.startService(i);
                }
            }
        }
    }
}