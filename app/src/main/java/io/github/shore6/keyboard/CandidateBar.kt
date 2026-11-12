package io.github.shore6.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** 変換候補を横に並べるバー */
@SuppressLint("ViewConstructor")
class CandidateBar(context: Context, private val onPick: (Int) -> Unit) : HorizontalScrollView(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val density = resources.displayMetrics.density
    private var views = listOf<TextView>()

    init {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(0xFF161B1E.toInt())
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun setCandidates(list: List<Candidate>, selected: Int) {
        row.removeAllViews()
        views = list.mapIndexed { i, c ->
            TextView(context).apply {
                text = c.text
                setTextColor(0xFFECEFF1.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                val pad = (14 * density).toInt()
                setPadding(pad, 0, pad, 0)
                minWidth = (44 * density).toInt()
                setOnClickListener { onPick(i) }
                row.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
            }
        }
        scrollTo(0, 0)
        setSelected(selected)
    }

    fun setSelected(selected: Int) {
        views.forEachIndexed { i, v ->
            v.background = if (i == selected) GradientDrawable().apply {
                setColor(0xFF37474F.toInt())
                cornerRadius = 8 * density
            } else null
        }
        views.getOrNull(selected)?.let { v ->
            post {
                val left = v.left - width / 3
                smoothScrollTo(maxOf(0, left), 0)
            }
        }
    }
}
