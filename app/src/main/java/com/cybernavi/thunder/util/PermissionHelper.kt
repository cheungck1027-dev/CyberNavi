package com.cybernavi.thunder.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.cybernavi.thunder.service.ThunderAccessibilityService

object PermissionHelper {

    /** 檢查「顯示在其他應用上層」權限 */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** 檢查無障礙服務是否已啟用 */
    fun isAccessibilityEnabled(context: Context): Boolean {
        return ThunderAccessibilityService.isEnabled(context)
    }

    /** 獲取所有權限狀態（調試用） */
    fun getPermissionSummary(context: Context): String {
        return buildString {
            appendLine("=== 雷霆權限狀態 ===")
            appendLine("懸浮視窗：${if (hasOverlayPermission(context)) "✅" else "❌"}")
            appendLine("無障礙服務：${if (isAccessibilityEnabled(context)) "✅" else "❌"}")
        }
    }
}
