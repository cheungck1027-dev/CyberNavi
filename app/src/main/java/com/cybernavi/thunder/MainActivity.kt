package com.cybernavi.thunder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cybernavi.thunder.databinding.ActivityMainBinding
import com.cybernavi.thunder.service.FloatingWindowService
import com.cybernavi.thunder.util.PermissionHelper

/**
 * MainActivity — 雷霆設定頁面
 *
 * 負責：
 * 1. 引導用戶開啟「顯示在其他應用上層」權限
 * 2. 引導用戶開啟「無障礙服務」
 * 3. 啟動 / 停止懸浮視窗服務
 * 4. 顯示連接狀態（Phase 2: 家用伺服器連線）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val REQ_OVERLAY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到主頁都更新權限狀態顯示
        updatePermissionStatus()
    }

    private fun setupUI() {
        // 啟動雷霆按鈕
        binding.btnStartThunder.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                requestOverlayPermission()
            } else if (!PermissionHelper.isAccessibilityEnabled(this)) {
                openAccessibilitySettings()
            } else {
                startFloatingService()
            }
        }

        // 停止雷霆按鈕
        binding.btnStopThunder.setOnClickListener {
            stopService(Intent(this, FloatingWindowService::class.java))
            binding.tvStatus.text = "雷霆已休眠 💤"
        }

        // 開啟懸浮視窗權限
        binding.btnGrantOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        // 開啟無障礙服務
        binding.btnGrantAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    private fun checkAndRequestPermissions() {
        updatePermissionStatus()

        // 如果兩個權限都已開啟，自動啟動
        if (PermissionHelper.hasOverlayPermission(this) &&
            PermissionHelper.isAccessibilityEnabled(this)) {
            startFloatingService()
        }
    }

    private fun updatePermissionStatus() {
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        val hasAccessibility = PermissionHelper.isAccessibilityEnabled(this)

        binding.tvOverlayStatus.text = if (hasOverlay) "✅ 懸浮視窗權限：已開啟" else "❌ 懸浮視窗權限：需要開啟"
        binding.tvAccessibilityStatus.text = if (hasAccessibility) "✅ 無障礙服務：已開啟" else "❌ 無障礙服務：需要開啟"

        binding.btnGrantOverlay.isEnabled = !hasOverlay
        binding.btnGrantAccessibility.isEnabled = !hasAccessibility

        // 更新整體狀態
        binding.tvStatus.text = when {
            hasOverlay && hasAccessibility -> "雷霆準備就緒 ⚡"
            !hasOverlay -> "需要開啟懸浮視窗權限"
            else -> "需要開啟無障礙服務"
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQ_OVERLAY)
        Toast.makeText(this, "請開啟「允許顯示在其他應用上層」", Toast.LENGTH_LONG).show()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "請找到「CyberNavi 雷霆」並開啟", Toast.LENGTH_LONG).show()
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
        binding.tvStatus.text = "雷霆上線！⚡ 隨時待命"
        Toast.makeText(this, "雷霆啟動成功！", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Using for permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            updatePermissionStatus()
        }
    }
}
