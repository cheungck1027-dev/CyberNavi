package com.cybernavi.thunder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.cybernavi.thunder.MainActivity
import com.cybernavi.thunder.R
import com.cybernavi.thunder.ui.ThunderFloatingView
import kotlinx.coroutines.*

/**
 * FloatingWindowService — 雷霆懸浮視窗服務
 *
 * 這是整個系統的「身體」——讓雷霆顯示在所有 App 上方。
 * 採用前台服務確保系統不會因為省電而殺死進程。
 *
 * 核心功能：
 * 1. 創建 SYSTEM_ALERT_WINDOW 懸浮視窗
 * 2. 渲染 ThunderFloatingView（雷霆角色 + 對話氣泡）
 * 3. 處理拖動、點擊、最小化
 * 4. 接收來自 AccessibilityService 的訊息並顯示
 * 5. 電量/充電狀態感知（更新角色動畫）
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: ThunderFloatingView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "thunder_foreground_channel"
        const val NOTIFICATION_ID = 1

        // Intent Actions — 其他 Service 用於發訊息給雷霆
        const val ACTION_SHOW_MESSAGE  = "com.cybernavi.thunder.SHOW_MESSAGE"
        const val ACTION_UPDATE_STATE  = "com.cybernavi.thunder.UPDATE_STATE"
        const val EXTRA_MESSAGE        = "message"
        const val EXTRA_ORIGINAL_TEXT  = "original_text"
        const val EXTRA_STATE          = "state"

        // 狀態常量
        const val STATE_IDLE     = "idle"
        const val STATE_THINKING = "thinking"
        const val STATE_TALKING  = "talking"
        const val STATE_BATTERY_LOW = "battery_low"
        const val STATE_CHARGING = "charging"

        // 靜態方法：從外部發訊息給雷霆
        fun sendMessage(context: Context, thunderText: String, originalText: String = "") {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_SHOW_MESSAGE
                putExtra(EXTRA_MESSAGE, thunderText)
                putExtra(EXTRA_ORIGINAL_TEXT, originalText)
            }
            context.startService(intent)
        }

        fun updateState(context: Context, state: String) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_STATE, state)
            }
            context.startService(intent)
        }
    }

    // ══════════════════════════════════════
    // Service 生命周期
    // ══════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_MESSAGE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
                val original = intent.getStringExtra(EXTRA_ORIGINAL_TEXT) ?: ""
                showThunderMessage(message, original)
            }
            ACTION_UPDATE_STATE -> {
                val state = intent.getStringExtra(EXTRA_STATE) ?: STATE_IDLE
                floatingView.updateState(state)
            }
        }
        return START_STICKY  // 系統殺死後自動重啟
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    // ══════════════════════════════════════
    // 懸浮視窗創建
    // ══════════════════════════════════════

    private fun createFloatingWindow() {
        floatingView = ThunderFloatingView(this)

        // 懸浮視窗參數配置
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // Android 8+ 標準
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or      // 不搶奪鍵盤焦點
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or    // 點擊外部穿透
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END  // 默認右上角
            x = 32
            y = 200
        }

        windowManager.addView(floatingView, layoutParams)
        setupTouchHandler()

        // 啟動動畫
        floatingView.startIdleAnimation()
    }

    // ══════════════════════════════════════
    // 拖動處理
    // ══════════════════════════════════════

    private fun setupTouchHandler() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var dragStartTime = 0L

        floatingView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    dragStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    // 移動超過 10px 才算拖動
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 煭按 = 切換對話氣泡顯示
                        floatingView.toggleChatBubble()
                    }
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    // ══════════════════════════════════════
    // 顯示雷霆訊息
    // ══════════════════════════════════════

    private fun showThunderMessage(message: String, originalText: String) {
        serviceScope.launch {
            // 先顯示「思考中」狀態
            floatingView.updateState(STATE_THINKING)
            delay(600)  // 短暫停頓，模擬思考感

            // 顯示訊息和說話動畫
            floatingView.updateState(STATE_TALKING)
            floatingView.showMessage(message)

            // 訊息顯示完後回到待機狀態
            delay(8000)
            floatingView.updateState(STATE_IDLE)
        }
    }

    // ══════════════════════════════════════
    // 前台通知
    // ══════════════════════════════════════

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "雷霆 AI 領航員",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "雷霆正在待機，隨時為你服務"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("雷霆上線 ⚡")
            .setContentText("AI 領航員正在待機，隨時待命")
            .setSmallIcon(R.drawable.ic_thunder_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
