package com.ubt.robocontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ubt.robocontroller.uvc.service.UVCService
import xh.zero.core.utils.ToastUtil

class BootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.intent.action.BOOT_COMPLETED") {
            ToastUtil.show(context, "启动触控服务")
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