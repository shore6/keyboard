package io.github.shore6.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/** キーボード本体。キーの描画とタップ・フリック・長押しの判定を行う */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onKey(key: Key, dir: Int)
        /** 長押しを処理したら true */
        fun onLongPress(key: Key): Boolean
        /** 状態によって変わるラベル (null ならキーのラベル) */
        fun dynamicLabel(key: Key): String?
        fun shiftState(): Int // 0: off, 1: on, 2: lock
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    var layout: KeyboardLayout = Layouts.english
        set(value) {
            field = value
            presses.clear()
            computeRects()
            invalidate()
        }

    var keyboardHeight: Int = dp(250f).toInt()
        set(value) {
            field = value
            requestLayout()
        }

    private class KeyRect(val key: Key, val rect: RectF)
    private var rects = listOf<KeyRect>()

    private class Press(val kr: KeyRect, val downX: Float, val downY: Float) {
        var dir = Dir.CENTER
        var consumed = false
        var moved = false
    }

    private val presses = LinkedHashMap<Int, Press>()
    private val handler = Handler(Looper.getMainLooper())

    // ---- 色 ----
    private val colorBg = 0xFF111417.toInt()
    private val colorKey = 0xFF263238.toInt()
    private val colorKeyPressed = 0xFF3D4D55.toInt()
    private val colorFunc = 0xFF1A2226.toInt()
    private val colorText = 0xFFECEFF1.toInt()
    private val colorHint = 0xFF90A4AE.toInt()
    private val colorAccent = 0xFF82B1FF.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorHint
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val gap = dp(3f)
    private val radius = dp(6f)
    private val flickThreshold = dp(22f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), keyboardHeight + dp(6f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeRects()

    private fun computeRects() {
        val w = width.toFloat()
        if (w <= 0f) return
        val top = dp(3f)
        val rowH = keyboardHeight.toFloat() / layout.rows.size
        val list = ArrayList<KeyRect>()
        layout.rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (w - dp(4f)) / total
            var x = dp(2f)
            val y = top + r * rowH
            for (key in row) {
                val kw = unit * key.weight
                list.add(KeyRect(key, RectF(x, y, x + kw, y + rowH)))
                x += kw
            }
        }
        rects = list
    }

    // ---------------- 描画 ----------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colorBg)
        val pressedKeys = presses.values.map { it.kr }
        for (kr in rects) drawKey(canvas, kr, kr in pressedKeys)
        presses.values.lastOrNull()?.let { if (it.kr.key.flick != null && it.kr.key.code == Code.KANA || it.kr.key.sub != null) drawFlickGuide(canvas, it) }
    }

    private fun drawKey(canvas: Canvas, kr: KeyRect, pressed: Boolean) {
        val key = kr.key
        if (key.style == KeyStyle.SPACER && key.code == Code.SPACER && key.weight < 1f) return
        val r = RectF(kr.rect.left + gap, kr.rect.top + gap, kr.rect.right - gap, kr.rect.bottom - gap)
        fill.color = when {
            pressed -> colorKeyPressed
            key.style == KeyStyle.NORMAL -> colorKey
            else -> colorFunc
        }
        canvas.drawRoundRect(r, radius, radius, fill)

        val cx = r.centerX()
        val cy = r.centerY()
        when (key.code) {
            Code.BACKSPACE -> { drawBackspace(canvas, cx, cy, min(r.width(), r.height())); return }
            Code.SHIFT -> { drawShift(canvas, cx, cy, min(r.width(), r.height()), listener.shiftState()); return }
            Code.GLOBE -> { drawGlobe(canvas, cx, cy, min(r.width(), r.height())); return }
            else -> {}
        }

        var label = listener.dynamicLabel(key) ?: key.label
        if (key.code == Code.TEXT && listener.shiftState() > 0 && label.length == 1 && label[0].isLowerCase()) {
            label = label.uppercase()
        }
        textPaint.color = if (key.style == KeyStyle.ENTER) colorAccent else colorText

        val base = min(r.height(), r.width())
        var size = when {
            label.length <= 1 -> base * 0.46f
            label.length == 2 -> base * 0.36f
            else -> base * 0.30f
        }.coerceAtMost(dp(26f))
        textPaint.textSize = size
        val maxW = r.width() * 0.86f
        while (textPaint.measureText(label) > maxW && size > dp(8f)) {
            size *= 0.9f
            textPaint.textSize = size
        }

        if (key.sub != null) {
            // 数字キー: 上に大きく数字、下に小さくフリック文字
            drawText(canvas, label, cx, r.top + r.height() * 0.42f, textPaint)
            hintPaint.textSize = min(base * 0.2f, dp(13f))
            drawText(canvas, key.sub, cx, r.top + r.height() * 0.76f, hintPaint)
        } else {
            drawText(canvas, label, cx, cy, textPaint)
            key.hint?.let {
                hintPaint.textSize = min(base * 0.24f, dp(12f))
                drawText(canvas, it, r.right - r.width() * 0.16f, r.top + r.height() * 0.2f, hintPaint)
            }
        }
    }

    private fun drawText(canvas: Canvas, s: String, cx: Float, cy: Float, p: Paint) {
        val fm = p.fontMetrics
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    private fun drawFlickGuide(canvas: Canvas, press: Press) {
        val key = press.kr.key
        val flick = key.flick ?: return
        val r = press.kr.rect
        val w = r.width()
        val h = r.height() * 0.85f
        val cells = arrayOf(
            RectF(r.left, r.top, r.right, r.bottom),
            RectF(r.left - w, r.centerY() - h / 2, r.left, r.centerY() + h / 2),
            RectF(r.left, r.top - h, r.right, r.top),
            RectF(r.right, r.centerY() - h / 2, r.right + w, r.centerY() + h / 2),
            RectF(r.left, r.bottom, r.right, r.bottom + h),
        )
        for (d in 0..4) {
            val s = flick.getOrNull(d) ?: continue
            val c = cells[d]
            val cr = RectF(c.left + gap, c.top + gap, c.right - gap, c.bottom - gap)
            fill.color = if (d == press.dir) colorAccent else 0xFF455A64.toInt()
            canvas.drawRoundRect(cr, radius, radius, fill)
            textPaint.color = if (d == press.dir) 0xFF0D1B2A.toInt() else colorText
            textPaint.textSize = min(cr.height(), cr.width()) * 0.45f
            drawText(canvas, s, cr.centerX(), cr.centerY(), textPaint)
        }
    }

    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val s = size * 0.2f
        val p = Path().apply {
            moveTo(cx - s * 1.6f, cy)
            lineTo(cx - s * 0.8f, cy - s)
            lineTo(cx + s * 1.4f, cy - s)
            lineTo(cx + s * 1.4f, cy + s)
            lineTo(cx - s * 0.8f, cy + s)
            close()
        }
        iconPaint.color = colorText
        canvas.drawPath(p, iconPaint)
        val x0 = cx + s * 0.3f
        canvas.drawLine(x0 - s * 0.45f, cy - s * 0.45f, x0 + s * 0.45f, cy + s * 0.45f, iconPaint)
        canvas.drawLine(x0 - s * 0.45f, cy + s * 0.45f, x0 + s * 0.45f, cy - s * 0.45f, iconPaint)
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, size: Float, state: Int) {
        val s = size * 0.22f
        val p = Path().apply {
            moveTo(cx, cy - s * 1.2f)
            lineTo(cx + s * 1.1f, cy)
            lineTo(cx + s * 0.5f, cy)
            lineTo(cx + s * 0.5f, cy + s * 0.9f)
            lineTo(cx - s * 0.5f, cy + s * 0.9f)
            lineTo(cx - s * 0.5f, cy)
            lineTo(cx - s * 1.1f, cy)
            close()
        }
        iconPaint.color = if (state > 0) colorAccent else colorText
        if (state > 0) {
            fill.color = colorAccent
            canvas.drawPath(p, fill)
        }
        canvas.drawPath(p, iconPaint)
        if (state == 2) canvas.drawLine(cx - s * 0.5f, cy + s * 1.3f, cx + s * 0.5f, cy + s * 1.3f, iconPaint)
    }

    private fun drawGlobe(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val rad = size * 0.22f
        iconPaint.color = colorText
        canvas.drawCircle(cx, cy, rad, iconPaint)
        canvas.drawOval(RectF(cx - rad * 0.45f, cy - rad, cx + rad * 0.45f, cy + rad), iconPaint)
        canvas.drawLine(cx - rad, cy, cx + rad, cy, iconPaint)
        canvas.drawLine(cx - rad * 0.87f, cy - rad * 0.5f, cx + rad * 0.87f, cy - rad * 0.5f, iconPaint)
        canvas.drawLine(cx - rad * 0.87f, cy + rad * 0.5f, cx + rad * 0.87f, cy + rad * 0.5f, iconPaint)
    }

    // ---------------- タッチ ----------------

    private fun keyAt(x: Float, y: Float): KeyRect? {
        val candidates = rects.filter { !(it.key.code == Code.SPACER && it.key.weight < 1f) }
        candidates.firstOrNull { it.rect.contains(x, y) }?.let { return it }
        // 端の隙間は最も近いキーへ
        return candidates.minByOrNull {
            val dx = maxOf(it.rect.left - x, 0f, x - it.rect.right)
            val dy = maxOf(it.rect.top - y, 0f, y - it.rect.bottom)
            dx * dx + dy * dy
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 前のキーを押したまま次を押したら、前のキーを確定させる
                for (id in presses.keys.toList()) release(id)
                val i = e.actionIndex
                val kr = keyAt(e.getX(i), e.getY(i)) ?: return true
                if (kr.key.code == Code.SPACER) return true
                val press = Press(kr, e.getX(i), e.getY(i))
                presses[e.getPointerId(i)] = press
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (kr.key.repeatable) {
                    listener.onKey(kr.key, Dir.CENTER)
                    press.consumed = true
                    scheduleRepeat(press, REPEAT_START)
                } else {
                    handler.postDelayed({
                        if (presses.containsValue(press) && !press.moved && !press.consumed) {
                            if (listener.onLongPress(kr.key)) {
                                press.consumed = true
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                presses.values.remove(press)
                                invalidate()
                            }
                        }
                    }, LONG_PRESS)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val press = presses[e.getPointerId(i)] ?: continue
                    val dx = e.getX(i) - press.downX
                    val dy = e.getY(i) - press.downY
                    val newDir = if (abs(dx) < flickThreshold && abs(dy) < flickThreshold) Dir.CENTER
                    else if (abs(dx) > abs(dy)) (if (dx < 0) Dir.LEFT else Dir.RIGHT)
                    else (if (dy < 0) Dir.UP else Dir.DOWN)
                    if (newDir != Dir.CENTER) press.moved = true
                    if (newDir != press.dir) {
                        press.dir = newDir
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                release(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                presses.clear()
                handler.removeCallbacksAndMessages(null)
                invalidate()
            }
        }
        return true
    }

    private fun release(pointerId: Int) {
        val press = presses.remove(pointerId) ?: return
        if (!press.consumed) {
            val key = press.kr.key
            var dir = press.dir
            // 割当のない方向は中央扱い
            if (dir != Dir.CENTER && key.flick?.getOrNull(dir) == null) dir = Dir.CENTER
            listener.onKey(key, dir)
        }
        invalidate()
    }

    private fun scheduleRepeat(press: Press, delay: Long) {
        handler.postDelayed({
            if (presses.containsValue(press)) {
                listener.onKey(press.kr.key, Dir.CENTER)
                scheduleRepeat(press, REPEAT_INTERVAL)
            }
        }, delay)
    }

    companion object {
        private const val LONG_PRESS = 400L
        private const val REPEAT_START = 400L
        private const val REPEAT_INTERVAL = 50L
    }
}
