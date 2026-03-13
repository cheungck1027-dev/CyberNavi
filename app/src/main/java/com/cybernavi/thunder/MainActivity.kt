package com.cybernavi.thunder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cybernavi.thunder.databinding.ActivityMainBinding
import com.cybernavi.thunder.network.ThunderServerClient
import com.cybernavi.thunder.service.FloatingWindowService
import com.cybernavi.thunder.settings.SettingsStore
import com.cybernavi.thunder.util.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MainActivity — 雷電設定頁面
 *
 * 負責：
 * 1. 引導用戶開啟「顯示在其他應用上層」權限
 * 2. 引導用戶開啟「無障礙服務」
 * 3. 啟動 / 停止浮動視窗服務
 * 4. Phase 2: 儲存 / 讀取家用伺服器 URL，測試連綫
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
        loadServerUrl()  // Phase 2: 讀取儲存的 URL
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupUI() {
        // 啟動雷電按鈕
        binding.btnStartThunder.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                requestOverlayPermission()
            } else if (!PermissionHelper.isAccessibilityEnabled(this)) {
                openAccessibilitySettings()
            } else {
                startFloatingService()
            }
        }

        // 停止雷電按鈕
        binding.btnStopThunder.setOnClickListener {
            stopService(Intent(this, FloatingWindowService::class.java))
            binding.tvStatus.text = "雷電已休眠  💤"
        }

        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnGrantAccessibility.setOnClickListener { openAccessibilitySettings() }

        // ── Phase 2: 儲存 Server URL 按鈕 ────────────────
        binding.btnSaveServerUrl.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            lifecycleScope.launch {
                SettingsStore.saveServerUrl(this@MainActivity, url)
                hideKeyboard()
                if (url.isEmpty()) {
                    binding.tvServerStatus.text = "● 離綫模式（規則引擎）"
                    binding.tvServerStatus.setTextColor(0xFF8899AA.toInt())
                    Toast.makeText(this@MainActivity, "URL 已清除，使用本地規則模式", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvServerStatus.text = "● URL 已儲存：$url"
                    binding.tvServerStatus.setTextColor(0xFF00CC77.toInt())
                    Toast.makeText(this@MainActivity, "伺服器 URL 已儲存！", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── Phase 2: 測試連綫按鈕 ────────────────
        binding.btnTestConnection.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "請先輸入伺服器 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.tvServerStatus.text = "● 測試中…"
            binding.tvServerStatus.setTextColor(0xFFFFD700.toInt())
            binding.btnTestConnection.isEnabled = false

            lifecycleScope.launch {
                val client = ThunderServerClient(url)
                val online = client.isServerOnline()
                binding.btnTestConnection.isEnabled = true
                if (online) {
                    binding.tvServerStatus.text = "● 伺服器在綫 ✔"
                    binding.tvServerStatus.setTextColor(0xFF00FF88.toInt())
                    Toast.makeText(this@MainActivity, "連綫成功！雷電 Phase 2 已就緒 ⚡", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvServerStatus.text = "● 伺服器不可達，使用備用規則引擎"
                    binding.tvServerStatus.setTextColor(0xFFFF4444.toInt())
                    Toast.makeText(this@MainActivity, "連綫失敗，請檢查 URL 和網路", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ──────────────────────────────────────
    // Phase 2: DataStore 存取
    // ──────────────────────────────────────

    private fun loadServerUrl() {
        lifecycleScope.launch {
            val url = SettingsStore.getServerUrl(this@MainActivity)
            binding.etServerUrl.setText(url)
            if (url.isNotEmpty()) {
                binding.tvServerStatus.text = "● URL 已設定：$url"
                binding.tvServerStatus.setTextColor(0xFF00CC77.toInt())
            }
        }
    }

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    // ──────────────────────────────────────
    // 權限管理
    // ──────────────────────────────────────

    private fun checkAndRequestPermissions() {
        updatePermissionStatus()
        if (PermissionHelper.hasOverlayPermission(this) &&
            PermissionHelper.isAccessibilityEnabled(this)) {
            startFloatingService()
        }
    }

    private fun updatePermissionStatus() {
        val hasOverlay = PermissionHelper.hasOverlayPermission(this)
        val hasAccessibility = PermissionHelper.isAccessibilityEnabled(this)

        binding.tvOverlayStatus.text = if (hasOverlay) "✅ 浮動視窗權限：已開啟" else "❌ 浮動視窗權限：需要開啟"
        binding.tvAccessibilityStatus.text = if (hasAccessibility) "✅ 無障礙服務：已開啟" else "❌ 無障礙服務：需要開啟"

        binding.btnGrantOverlay.isEnabled = !hasOverlay
        binding.btnGrantAccessibility.isEnabled = !hasAccessibility

        binding.tvStatus.text = when {
            hasOverlay && hasAccessibility -> "雷電準備就緒 ⚡"
            !hasOverlay -> "需要開啟浮動視窗權限"
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
        Toast.makeText(this, "請找到「CyberNavi 雷電」並開啟", Toast.LENGTH_LONG).show()
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
        binding.tvStatus.text = "雷電上綫！⚡ 隨時待命"
        Toast.makeText(this, "雷電啟動成功！", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Using for permission result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            updatePermissionStatus()
        }
    }
}
