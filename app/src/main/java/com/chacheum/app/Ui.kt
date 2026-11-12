package com.chacheum.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** 색 (어두운 차 화면 기준, 대비 4.5:1 이상). */
object C {
    const val BG = 0xFF0E1116.toInt()
    const val CARD = 0xFF1A1F27.toInt()
    const val CARD2 = 0xFF252C38.toInt()
    const val LINE = 0xFF333C4B.toInt()
    const val TEXT = 0xFFF2F4F7.toInt()
    const val TEXT2 = 0xFFB9C1CC.toInt()
    const val TEXT3 = 0xFF8A94A3.toInt()
    const val ACCENT = 0xFF5B9BFF.toInt()
    const val ACCENT_BG = 0xFF1C3560.toInt()
    const val OK = 0xFF52C98F.toInt()
    const val WARN = 0xFFFFBE5C.toInt()
    const val WARN_BG = 0xFF3B2D12.toInt()
}

/** 글자 크기(sp). Pleos 글자 단계(Title 32~40, Body 26~30, Label 20~24)에 맞춤. */
object T {
    const val TITLE = 34f
    const val H2 = 28f
    const val BODY = 24f
    const val LABEL = 20f
    const val SMALL = 17f
}

/** XML 없이 코드로 화면을 만드는 도구. */
class Ui(private val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density).roundToInt()

    fun text(s: CharSequence, size: Float = T.BODY, color: Int = C.TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            setLineSpacing(0f, 1.25f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun vbox(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun hbox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun rounded(color: Int, radius: Int = 20, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        if (stroke != null) setStroke(dp(2), stroke)
    }

    fun card(color: Int = C.CARD, stroke: Int? = null): LinearLayout = vbox().apply {
        background = rounded(color, 20, stroke)
        setPadding(dp(24), dp(20), dp(24), dp(22))
    }

    fun gap(h: Int): View = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun pressable(v: View) {
        val tv = TypedValue()
        if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            v.foreground = ctx.getDrawable(tv.resourceId)
        }
        v.isClickable = true
        v.isFocusable = true
    }

    /** 채운 버튼 (화면의 주인공, 높이 88). */
    fun primary(label: String, onClick: () -> Unit): TextView = text(label, T.BODY, 0xFFFFFFFF.toInt(), bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(C.ACCENT, 18)
        minHeight = dp(88)
        setPadding(dp(24), dp(12), dp(24), dp(12))
        pressable(this)
        setOnClickListener { onClick() }
    }

    /** 기본 버튼 (높이 72). */
    fun secondary(label: String, onClick: () -> Unit): TextView = text(label, T.LABEL, C.TEXT, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(C.CARD2, 16)
        minHeight = dp(72)
        setPadding(dp(20), dp(10), dp(20), dp(10))
        pressable(this)
        setOnClickListener { onClick() }
    }

    /** 상태 칩. 켜지면 파랑. */
    fun chip(label: String, on: Boolean, onClick: (() -> Unit)? = null): TextView =
        text(label, T.SMALL, if (on) C.ACCENT else C.TEXT3, bold = on).apply {
            background = rounded(if (on) C.ACCENT_BG else C.CARD2, 40)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            if (onClick != null) {
                pressable(this)
                setOnClickListener { onClick() }
            }
        }

    /** 가로 막대 (0~1). */
    fun bar(fraction: Double, color: Int): LinearLayout = hbox().apply {
        background = rounded(C.CARD2, 8)
        val f = fraction.coerceIn(0.0, 1.0).toFloat()
        val fill = View(ctx).apply { background = rounded(color, 8) }
        addView(fill, LinearLayout.LayoutParams(0, dp(12), f.coerceAtLeast(0.001f)))
        addView(View(ctx), LinearLayout.LayoutParams(0, dp(12), (1f - f).coerceAtLeast(0.001f)))
    }

    fun full(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun fullWithTop(top: Int): LinearLayout.LayoutParams = full().apply { topMargin = dp(top) }

    fun weight(w: Float = 1f, start: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w).apply { marginStart = dp(start) }

    fun wrapStart(start: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(start) }
}
