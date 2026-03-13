package com.cybernavi.thunder.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cybernavi.thunder.service.FloatingWindowService

/** 開機自動啟動雷霆 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            if (PermissionHelper.hasOverlayPermission(context)) {
                context.startForegroundService(
                    Intent(context, FloatingWindowService::class.java)
                )
            }
        }
    }
}
