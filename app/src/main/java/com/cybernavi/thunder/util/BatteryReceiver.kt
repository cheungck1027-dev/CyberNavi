package com.cybernavi.thunder.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.cybernavi.thunder.service.FloatingWindowService

/** 電量狀態感知 — 影響雷霆動畫 */
class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (scale > 0) level * 100 / scale else -1

                if (pct in 0..20) {
                    FloatingWindowService.updateState(context, FloatingWindowService.STATE_BATTERY_LOW)
                }
            }
            Intent.ACTION_POWER_CONNECTED -> {
                FloatingWindowService.updateState(context, FloatingWindowService.STATE_CHARGING)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                FloatingWindowService.updateState(context, FloatingWindowService.STATE_IDLE)
            }
        }
    }
}
