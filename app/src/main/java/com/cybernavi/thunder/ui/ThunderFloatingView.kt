package com.cybernavi.thunder.ui

import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import android.view.animation.*
import android.widget.*
import com.cybernavi.thunder.service.FloatingWindowService

/**
 * ThunderFloatingView — 雷霆懸浮視窗 UI
 *
 * Phase 1：自定義 Canvas 繪製角色（佔位符）
 * Phase 2 升級：將 ThunderAvatarCanvas 替換為 Live2D SDK 的 LAppView
 */
class ThunderFloatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private lateinit var avatarContainer: FrameLayout
    private lateinit var avatarCanvas: ThunderAvatarCanvas
    private lateinit var chatBubble: LinearLayout
    private lateinit var tvMessage: TextView
    private lateinit var tvThinking: TextView
    private lateinit var ivStateIcon: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private var isBubbleVisible = false

    companion object {
        private const val BUBBLE_AUTO_HIDE_MS = 10_000L
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setupViews()
    }

    // ══════════════════════════════════════
    // 視圖初始化
    // ══════════════════════════════════════

    private fun setupViews() {
        avatarContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(dpToPx(76), dpToPx(76))
        }

        avatarCanvas = ThunderAvatarCanvas(context)
        avatarContainer.addView(
            avatarCanvas,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        // buildChatBubble() 同時初始化 tvThinking 和 tvMessage
        chatBubble = buildChatBubble()

        ivStateIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(14), dpToPx(14)).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dpToPx(2), dpToPx(2), 0)
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(chatBubble)
        container.addView(avatarContainer, LinearLayout.LayoutParams(dpToPx(76), dpToPx(76)))

        addView(container)
        addView(ivStateIcon)
    }

    private fun buildChatBubble(): LinearLayout {
        tvThinking = TextView(context).apply {
            text = "⚡ 諗緊..."
            textSize = 11f
            setTextColor(Color.parseColor("#FFD700"))
            visibility = GONE
        }
        tvMessage = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            maxWidth = dpToPx(260)
            isSingleLine = false
            setLineSpacing(0f, 1.2f)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = GONE
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#CC0D1F33"))
                setStroke(dpToPx(1), Color.parseColor("#440078D4"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(6) }
            addView(tvThinking)
            addView(tvMessage)
        }
    }

    // ══════════════════════════════════════
    // 公開 API
    // ══════════════════════════════════════

    fun showMessage(message: String) {
        handler.post {
            tvThinking.visibility = GONE
            tvMessage.text = message
            tvMessage.visibility = VISIBLE
            chatBubble.visibility = VISIBLE
            isBubbleVisible = true
            chatBubble.alpha = 0f
            chatBubble.animate().alpha(1f).setDuration(300)
                .setInterpolator(DecelerateInterpolator()).start()
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ hideBubble() }, BUBBLE_AUTO_HIDE_MS)
        }
    }

    fun toggleChatBubble() {
        if (isBubbleVisible) hideBubble() else {
            chatBubble.visibility = VISIBLE
            isBubbleVisible = true
        }
    }

    fun updateState(state: String) {
        handler.post {
            avatarCanvas.setState(state)
            when (state) {
                FloatingWindowService.STATE_THINKING -> {
                    tvThinking.visibility = VISIBLE
                    tvMessage.visibility = GONE
                    chatBubble.visibility = VISIBLE
                    doThinkingWiggle()
                }
                FloatingWindowService.STATE_TALKING -> {
                    tvThinking.visibility = GONE
                    avatarCanvas.clearAllAnimations()
                }
                FloatingWindowService.STATE_IDLE -> {
                    avatarCanvas.clearAllAnimations()
                }
            }
        }
    }

    fun startIdleAnimation() = avatarCanvas.startBreathing()

    private fun hideBubble() {
        chatBubble.animate().alpha(0f).setDuration(200).withEndAction {
            chatBubble.visibility = GONE
            chatBubble.alpha = 1f
            isBubbleVisible = false
        }.start()
    }

    private fun doThinkingWiggle() {
        avatarCanvas.animate().rotationY(8f).setDuration(250).withEndAction {
            avatarCanvas.animate().rotationY(-8f).setDuration(250).withEndAction {
                avatarCanvas.animate().rotationY(0f).setDuration(200).start()
            }.start()
        }.start()
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}


/**
 * ThunderAvatarCanvas — Phase 1 角色佔位符
 *
 * 深藍圓形 + 閃電圖案 + 眨眼動畫 + 嘴巴動畫
 * 顏色隨狀態改袺：
 *   - 待機  : 深藍 + 藍光
 *   - 思考  : 深紫 + 紫光
 *   - 說話  : 深藍 + 亮藍光 + 嘴巴動
 *   - 低電量: 橙棕 + 橙光
 *   - 充電  : 深黃 + 金光
 *
 * Phase 2 升級路徑：用 Live2D SDK LAppView 替換此 View
 */
class ThunderAvatarCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentState = FloatingWindowService.STATE_IDLE
    private var blinkProgress = 0f
    private var mouthOpen     = 0f
    private var breathScale   = 1f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val blinkHandler  = Handler(Looper.getMainLooper())
    private var breathAnimator: ValueAnimator? = null
    private var mouthAnimator:  ValueAnimator? = null

    private val blinkAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
        duration = 120
        addUpdateListener { blinkProgress = it.animatedValue as Float; invalidate() }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        scheduleNextBlink()
    }

    fun setState(newState: String) {
        currentState = newState
        when (newState) {
            FloatingWindowService.STATE_TALKING -> startMouthAnim()
            else -> { mouthAnimator?.cancel(); mouthOpen = 0f }
        }
        invalidate()
    }

    fun startBreathing() {
        breathAnimator?.cancel()
        breathAnimator = ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { breathScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun clearAllAnimations() {
        breathAnimator?.cancel()
        mouthAnimator?.cancel()
        mouthOpen   = 0f
        breathScale = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width  / 2f
        val cy = height / 2f
        val r  = minOf(width, height) / 2f * 0.82f * breathScale
        val (bodyColor, glowColor) = stateColors()

        // 外發光
        glowPaint.apply {
            color = Color.parseColor(glowColor)
            alpha = 55
            maskFilter = BlurMaskFilter(r * 0.35f, BlurMaskFilter.Blur.OUTER)
        }
        canvas.drawCircle(cx, cy, r * 1.15f, glowPaint)

        // 主體填充
        bodyPaint.apply { color = Color.parseColor(bodyColor); style = Paint.Style.FILL; maskFilter = null }
        canvas.drawCircle(cx, cy, r, bodyPaint)

        // 邊框
        bodyPaint.apply { color = Color.parseColor(glowColor); style = Paint.Style.STROKE; strokeWidth = 2.5f }
        canvas.drawCircle(cx, cy, r, bodyPaint)

        // 閃電
        drawBolt(canvas, cx, cy - r * 0.08f, r * 0.30f, glowColor)

        // 眼睛 + 嘴巴
        drawFace(canvas, cx, cy, r)
    }

    private fun stateColors() = when (currentState) {
        FloatingWindowService.STATE_THINKING    -> "#38006B" to "#9C27B0"
        FloatingWindowService.STATE_TALKING     -> "#002060" to "#1565C0"
        FloatingWindowService.STATE_BATTERY_LOW -> "#7A3900" to "#FF8F00"
        FloatingWindowService.STATE_CHARGING    -> "#5D4100" to "#FDD835"
        else                                    -> "#0D1F33" to "#0078D4"
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, s: Float, color: String) {
        boltPaint.apply {
            this.color = Color.parseColor(color)
            style = Paint.Style.FILL; alpha = 200; maskFilter = null
        }
        canvas.drawPath(Path().apply {
            moveTo(cx,              cy - s)
            lineTo(cx - s * 0.42f,  cy + s * 0.08f)
            lineTo(cx - s * 0.06f,  cy + s * 0.08f)
            lineTo(cx,              cy + s)
            lineTo(cx + s * 0.42f,  cy - s * 0.08f)
            lineTo(cx + s * 0.06f,  cy - s * 0.08f)
            close()
        }, boltPaint)
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val ey = cy + r * 0.32f
        val ex = r * 0.26f
        val ew = r * 0.16f
        val eh = r * 0.13f * (1f - blinkProgress)

        eyePaint.apply { color = Color.WHITE; style = Paint.Style.FILL; alpha = 220 }
        if (eh > 1f) {
            canvas.drawOval(cx - ex - ew, ey - eh, cx - ex + ew, ey + eh, eyePaint)
            canvas.drawOval(cx + ex - ew, ey - eh, cx + ex + ew, ey + eh, eyePaint)
            eyePaint.color = Color.parseColor("#0078D4")
            canvas.drawCircle(cx - ex, ey, ew * 0.5f, eyePaint)
            canvas.drawCircle(cx + ex, ey, ew * 0.5f, eyePaint)
        }
        if (mouthOpen > 0.05f) {
            eyePaint.color = Color.parseColor("#001433")
            val my = cy + r * 0.58f
            canvas.drawOval(
                cx - r * 0.14f, my - r * 0.07f * mouthOpen,
                cx + r * 0.14f, my + r * 0.07f * mouthOpen, eyePaint
            )
        }
    }

    private fun startMouthAnim() {
        mouthAnimator?.cancel()
        mouthAnimator = ValueAnimator.ofFloat(0f, 1f, 0.3f, 0.8f, 0f).apply {
            duration = 900; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { mouthOpen = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun scheduleNextBlink() {
        blinkHandler.postDelayed({
            if (!blinkAnimator.isRunning) blinkAnimator.start()
            scheduleNextBlink()
        }, (2800 + Math.random() * 4000).toLong())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blinkHandler.removeCallbacksAndMessages(null)
        breathAnimator?.cancel()
        mouthAnimator?.cancel()
    }
}
