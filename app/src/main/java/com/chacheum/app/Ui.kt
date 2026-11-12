package com.chacheum.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 차츰 컴포넌트(bundle.css)를 XML 없이 코드로 만드는 곳.
 * 색은 역할 토큰만 쓰고, 주간·야간은 res/values(-night)/colors.xml이 정함.
 */
class Ui(val ctx: Context) {
    private val dm = ctx.resources.displayMetrics

    private fun c(id: Int) = ctx.getColor(id)

    val bg = c(R.color.bg)
    val card = c(R.color.card)
    val raised = c(R.color.raised)
    val inset = c(R.color.inset)
    val line = c(R.color.line)
    val lineStrong = c(R.color.line_strong)
    val text = c(R.color.text)
    val text2 = c(R.color.text2)
    val text3 = c(R.color.text3)
    val textOff = c(R.color.text_off)
    val cta = c(R.color.cta)
    val ctaPressed = c(R.color.cta_pressed)
    val onCta = c(R.color.on_cta)
    val btn = c(R.color.btn)
    val btnPressed = c(R.color.btn_pressed)
    val btnOn = c(R.color.btn_on)
    val accent = c(R.color.accent)
    val accentWeak = c(R.color.accent_weak)
    val accentText = c(R.color.accent_text)
    val onAccent = c(R.color.on_accent)
    val danger = c(R.color.danger)
    val focus = c(R.color.focus)
    val scrim = c(R.color.scrim)
    val toast = c(R.color.toast)
    val onToast = c(R.color.on_toast)

    fun dp(v: Number): Int = (v.toFloat() * dm.density).roundToInt()
    fun dpf(v: Number): Float = v.toFloat() * dm.density
    private fun spPx(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm).roundToInt()

    fun font(weight: Int): Typeface = Fonts.get(ctx, weight)

    // -----------------------------------------------------------------------
    // 바탕: 글자, 묶음, 모양
    // -----------------------------------------------------------------------

    /** 글자 한 덩이. 여러 줄이 될 수 있는 글은 타입 표의 줄 높이를 씀. */
    fun text(s: CharSequence, t: Type, color: Int = text, oneLine: Boolean = false, tnum: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, t.size)
            typeface = font(t.weight)
            setTextColor(color)
            includeFontPadding = false
            textLocale = Locale.KOREAN
            if (oneLine) {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            } else {
                setLineHeight(spPx(t.line))
                // 한국어는 띄어쓰기 단위로 줄을 바꿈(word-break: keep-all)
                if (Build.VERSION.SDK_INT >= 33) {
                    lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
                }
            }
            if (tnum) fontFeatureSettings = "'tnum'"
        }

    /** 값 자리: 숫자는 그 자리 크기·굵기, 단위는 body-m(28, 400)으로 작게. */
    fun valueText(parts: List<Pair<String, Boolean>>, t: Type = Type.TITLE_S, color: Int = text, unitColor: Int? = null): TextView {
        val sb = SpannableStringBuilder()
        parts.forEach { (s, isUnit) ->
            val start = sb.length
            sb.append(s)
            if (isUnit) {
                sb.setSpan(AbsoluteSizeSpan(spPx(28f)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(TypefaceSpan(font(400)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (unitColor != null) sb.setSpan(ForegroundColorSpan(unitColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text(sb, t, color, oneLine = true, tnum = true)
    }

    fun num(n: String, unit: String, t: Type = Type.TITLE_S, color: Int = text) = valueText(listOf(n to false, unit to true), t, color)

    /** 앞말을 굵게(600) 쓴 한 줄. */
    fun boldLead(lead: String, rest: String, t: Type, color: Int, leadColor: Int): TextView {
        val sb = SpannableStringBuilder(lead)
        sb.setSpan(TypefaceSpan(font(600)), 0, lead.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(leadColor), 0, lead.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(rest)
        return text(sb, t, color)
    }

    private fun gapDrawable(px: Int, color: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setSize(px, px)
        setColor(color)
    }

    /** 세로 묶음. gap은 CSS gap과 같음(빈 칸 구분자). */
    fun col(gap: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (gap > 0) {
            dividerDrawable = gapDrawable(dp(gap))
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }
    }

    fun row(gap: Int = 0, center: Boolean = true): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        if (center) gravity = Gravity.CENTER_VERTICAL
        if (gap > 0) {
            dividerDrawable = gapDrawable(dp(gap))
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }
    }

    fun shape(color: Int, radius: Int, strokeColor: Int? = null, strokeDp: Int = 3): GradientDrawable = GradientDrawable().apply {
        cornerRadius = if (radius >= Rd.FULL) 9999f else dpf(radius)
        setColor(color)
        if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
    }

    private fun stroke(radius: Int, color: Int, w: Int = 3): GradientDrawable = GradientDrawable().apply {
        cornerRadius = if (radius >= Rd.FULL) 9999f else dpf(radius)
        setColor(Color.TRANSPARENT)
        setStroke(dp(w), color)
    }

    /** 누르는 동안 바탕이 바뀌는 면(0.1초 안). 초점은 3px 테두리. */
    private fun pressBg(normal: Int, pressed: Int, radius: Int): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(pressed, radius))
        addState(intArrayOf(-android.R.attr.state_enabled), shape(btn, radius))
        addState(intArrayOf(), shape(normal, radius))
    }

    /** 카드 위에 덧그리는 면: 누름(btn-pressed 겹침), 초점, 고름(accent 테두리). */
    private fun overlay(radius: Int, selected: Boolean): StateListDrawable = StateListDrawable().apply {
        val sel = if (selected) stroke(radius, accent) else null
        fun withSel(d: Drawable): Drawable = if (sel != null) LayerDrawable(arrayOf<Drawable>(d, sel)) else d
        addState(intArrayOf(android.R.attr.state_pressed), withSel(shape(btnPressed, radius)))
        addState(intArrayOf(android.R.attr.state_focused), withSel(stroke(radius, focus)))
        addState(intArrayOf(), sel ?: shape(Color.TRANSPARENT, radius))
    }

    private fun clickable(v: View, onClick: () -> Unit) {
        v.isClickable = true
        v.isFocusable = true
        v.setOnClickListener { onClick() }
    }

    fun icon(ic: Ic, size: Int, color: Int): ImageView = ImageView(ctx).apply {
        setImageDrawable(IconDrawable(ic, color, dp(size)))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun iconDrawable(ic: Ic, size: Int, color: Int): Drawable =
        IconDrawable(ic, color, dp(size)).apply { setBounds(0, 0, dp(size), dp(size)) }

    // -----------------------------------------------------------------------
    // 배치 도우미
    // -----------------------------------------------------------------------

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(w, h)
    fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    fun weight(w: Float = 1f, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(0, h, w)
    fun space(): View = View(ctx)

    // -----------------------------------------------------------------------
    // 카드
    // -----------------------------------------------------------------------

    fun card(pad: Int = Sp.S6, radius: Int = Rd.M, gap: Int = Sp.S4): LinearLayout = col(gap).apply {
        background = shape(card, radius)
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

    // -----------------------------------------------------------------------
    // 버튼: 채운 버튼(화면에 하나), 기본, 텍스트, 아이콘
    // -----------------------------------------------------------------------

    enum class Kind { FILLED, BASIC, TEXT }
    enum class Size { L, M, S }

    fun button(label: String, kind: Kind = Kind.BASIC, size: Size = Size.M, ic: Ic? = null, enabled: Boolean = true, onClick: () -> Unit): TextView {
        val (h, r, pad, type) = when (size) {
            Size.L -> Quad(Sz.BTN_L, Rd.L, Sp.S10, Type.TITLE_S)
            Size.M -> Quad(Sz.BTN_M, Rd.M, Sp.S8, Type.BODY_L_STRONG)
            Size.S -> Quad(Sz.BTN_S, Rd.S, Sp.S6, Type.BODY_M_STRONG)
        }
        val fg = when {
            !enabled -> textOff
            kind == Kind.FILLED -> onCta
            kind == Kind.TEXT -> accentText
            else -> text
        }
        return text(label, if (kind == Kind.TEXT) Type.BODY_M_STRONG else type, fg, oneLine = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(if (kind == Kind.TEXT) Sz.BTN_S else h)
            minWidth = dp(Sz.TOUCH)
            setPadding(dp(if (kind == Kind.TEXT) Sp.S4 else pad), 0, dp(if (kind == Kind.TEXT) Sp.S4 else pad), 0)
            background = when (kind) {
                Kind.FILLED -> pressBg(cta, ctaPressed, r)
                Kind.BASIC -> pressBg(btn, btnPressed, r)
                Kind.TEXT -> pressBg(Color.TRANSPARENT, btnPressed, Rd.S)
            }
            if (ic != null) {
                val isz = when (size) {
                    Size.L -> Sz.ICON_L
                    Size.M -> Sz.ICON_M
                    Size.S -> Sz.ICON_S
                }
                setCompoundDrawablesRelative(iconDrawable(ic, isz, fg), null, null, null)
                compoundDrawablePadding = dp(Sp.S3)
            }
            isEnabled = enabled
            if (enabled) clickable(this, onClick)
        }
    }

    private data class Quad(val h: Int, val r: Int, val pad: Int, val type: Type)

    /** 아이콘만 있는 버튼(누르는 영역 64). plain이면 바탕 없이 text2. */
    fun iconButton(ic: Ic, label: String, plain: Boolean = false, onClick: () -> Unit): FrameLayout = FrameLayout(ctx).apply {
        val color = if (plain) text2 else text
        addView(icon(ic, Sz.ICON_M, color), FrameLayout.LayoutParams(dp(Sz.ICON_M), dp(Sz.ICON_M), Gravity.CENTER))
        background = pressBg(if (plain) Color.TRANSPARENT else btn, btnPressed, Rd.S)
        contentDescription = label
        minimumWidth = dp(Sz.TOUCH)
        minimumHeight = dp(Sz.TOUCH)
        layoutParams = LinearLayout.LayoutParams(dp(Sz.TOUCH), dp(Sz.TOUCH))
        clickable(this, onClick)
    }

    // -----------------------------------------------------------------------
    // 상황 칩, 태그, 탭, 검색 칸
    // -----------------------------------------------------------------------

    fun chip(label: String, ic: Ic, on: Boolean, onClick: () -> Unit): TextView =
        text(label, Type.BODY_L, if (on) text else text2, oneLine = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(Sz.BTN_S)
            setPadding(dp(Sp.S4), 0, dp(Sp.S6), 0)
            background = pressBg(if (on) btnOn else btn, btnPressed, Rd.S)
            if (on) elevation = dpf(3)
            setCompoundDrawablesRelative(iconDrawable(ic, Sz.ICON_S, if (on) accent else text3), null, null, null)
            compoundDrawablePadding = dp(Sp.S3)
            contentDescription = "$label, ${if (on) "켜짐" else "꺼짐"}"
            clickable(this, onClick)
        }

    fun tag(label: String, mine: Boolean = false, bgColor: Int? = null): TextView =
        text(label, Type.LABEL_M, if (mine) onAccent else text2, oneLine = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(Sp.S4), 0, dp(Sp.S4), 0)
            background = shape(if (mine) accent else bgColor ?: btn, Rd.XS)
        }

    fun tabs(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit): LinearLayout = row(Sp.S1).apply {
        background = shape(btn, Rd.M)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        clipToPadding = false
        clipChildren = false
        items.forEach { (id, label) ->
            val on = id == selected
            addView(text(label, Type.BODY_L_STRONG, if (on) text else text2, oneLine = true).apply {
                gravity = Gravity.CENTER
                background = pressBg(if (on) btnOn else Color.TRANSPARENT, btnPressed, Rd.S)
                if (on) elevation = dpf(3)
                clickable(this) { onSelect(id) }
            }, weight(1f, dp(Sz.BTN_S)))
        }
    }

    fun search(placeholder: String, onClick: () -> Unit): LinearLayout = row(Sp.S4).apply {
        minimumHeight = dp(Sz.BTN_M)
        setPadding(dp(Sp.S6), 0, dp(Sp.S2), 0)
        background = pressBg(card, btnPressed, Rd.M)
        addView(icon(Ic.SEARCH, Sz.ICON_M, text2))
        addView(text(placeholder, Type.BODY_L, text3, oneLine = true), weight())
        contentDescription = placeholder
        clickable(this, onClick)
    }

    // -----------------------------------------------------------------------
    // 운전자 고르기
    // -----------------------------------------------------------------------

    fun driverCard(name: String, meta: String, avatar: String?, avatarIc: Ic?, last: Boolean, onClick: () -> Unit): LinearLayout =
        row(Sp.S6).apply {
            minimumHeight = dp(160)
            setPadding(dp(Sp.S6), dp(Sp.S6), dp(Sp.S6), dp(Sp.S6))
            background = shape(card, Rd.L)
            foreground = overlay(Rd.L, last)
            val av = FrameLayout(ctx).apply {
                background = shape(inset, Rd.FULL)
                if (avatarIc != null) {
                    addView(icon(avatarIc, Sz.ICON_M, text2), FrameLayout.LayoutParams(dp(Sz.ICON_M), dp(Sz.ICON_M), Gravity.CENTER))
                } else {
                    addView(this@Ui.text(avatar ?: "", Type.BODY_L_STRONG, text, oneLine = true), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                }
            }
            addView(av, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(col(Sp.S2).apply {
                addView(this@Ui.text(name, Type.TITLE_S, text, oneLine = true))
                addView(this@Ui.text(meta, Type.BODY_S, if (last) accentText else text3))
            }, weight())
            contentDescription = "$name, $meta"
            clickable(this, onClick)
        }

    // -----------------------------------------------------------------------
    // 경로 후보 카드
    // -----------------------------------------------------------------------

    fun routeOption(
        index: Int, name: String, minutes: Int, arrive: String, why: String?, facts: String,
        rec: Boolean, checked: Boolean, onClick: () -> Unit,
    ): LinearLayout = row(Sp.S6).apply {
        setPadding(dp(Sp.S8), dp(Sp.S6), dp(Sp.S6), dp(Sp.S6))
        background = if (checked) LayerDrawable(arrayOf<Drawable>(shape(card, Rd.L), shape(accentWeak, Rd.L))) else shape(card, Rd.L)
        foreground = overlay(Rd.L, checked)
        val main = col(Sp.S2)
        main.addView(row(Sp.S4).apply {
            addView(this@Ui.text(name, Type.TITLE_M, text, oneLine = true))
            if (rec) addView(tag("추천", mine = true))
        })
        main.addView(row(Sp.S3, center = false).apply {
            gravity = Gravity.BOTTOM
            addView(num("$minutes", "분"))
            addView(this@Ui.text("$arrive 도착", Type.BODY_M, text2, oneLine = true, tnum = true).apply { setPadding(0, 0, 0, dp(2)) })
        })
        if (why != null) main.addView(this@Ui.text(why, Type.BODY_M, if (checked || rec) text else text2))
        main.addView(this@Ui.text(facts, Type.BODY_S, text3, tnum = true))
        addView(main, weight())
        addView(SketchView(ctx, index, rec, this@Ui), LinearLayout.LayoutParams(dp(208), dp(120)))
        contentDescription = listOfNotNull(name, if (rec) "추천" else null, "${minutes}분", "$arrive 도착", why, facts).joinToString(", ")
        clickable(this, onClick)
    }

    // -----------------------------------------------------------------------
    // 주행 중 카드
    // -----------------------------------------------------------------------

    fun caution(dist: String, place: String, say: String, say2: String, lanes: Int?, goLane: Int?): LinearLayout = col(Sp.S6).apply {
        background = shape(card, Rd.L)
        elevation = dpf(8)
        setPadding(dp(Sp.S8), dp(Sp.S8), dp(Sp.S8), dp(Sp.S8))
        addView(row(Sp.S4).apply {
            addView(icon(Ic.SPOT, Sz.ICON_L, accent))
            addView(num(dist, "m 앞"), weight())
            addView(tag(place))
        })
        addView(this@Ui.text(say, Type.TITLE_L))
        addView(this@Ui.text(say2, Type.BODY_L, text2))
        if (lanes != null && goLane != null) {
            addView(row(Sp.S2).apply {
                for (i in 0 until lanes) {
                    val go = i == goLane
                    addView(FrameLayout(ctx).apply {
                        background = if (go) LayerDrawable(arrayOf<Drawable>(shape(accentWeak, Rd.S), stroke(Rd.S, accent))) else shape(inset, Rd.S)
                        addView(
                            icon(if (go) Ic.TURN_RIGHT else Ic.STRAIGHT, Sz.ICON_M, if (go) accent else text3),
                            FrameLayout.LayoutParams(dp(Sz.ICON_M), dp(Sz.ICON_M), Gravity.CENTER),
                        )
                    }, LinearLayout.LayoutParams(dp(88), dp(72)))
                }
                contentDescription = "${lanes}차로 중 ${goLane + 1}번째 차로"
            })
        }
    }

    fun driveCard(title: String, sub: String, rows: List<Pair<String, TextView>>): LinearLayout = col(Sp.S4).apply {
        background = shape(card, Rd.L)
        setPadding(dp(Sp.S8), dp(Sp.S8), dp(Sp.S8), dp(Sp.S8))
        addView(this@Ui.text(title, Type.TITLE_M))
        addView(this@Ui.text(sub, Type.BODY_M, text2))
        val kv = col().apply {
            dividerDrawable = gapDrawable(dp(2), line).apply { setSize(dp(2), dp(2)) }
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }
        rows.forEach { (k, v) ->
            kv.addView(row(Sp.S6).apply {
                setPadding(0, dp(Sp.S4), 0, dp(Sp.S4))
                addView(this@Ui.text(k, Type.BODY_M, text2, oneLine = true), weight())
                addView(v)
            })
        }
        addView(kv)
    }

    fun demoLog(lead: String?, rest: String): LinearLayout = row(Sp.S4).apply {
        background = shape(card, Rd.M)
        setPadding(dp(Sp.S6), dp(Sp.S4), dp(Sp.S6), dp(Sp.S4))
        addView(tag("시연 모드"))
        addView(if (lead != null) boldLead(lead, " · $rest", Type.BODY_M, text2, text) else this@Ui.text(rest, Type.BODY_M, text2), weight())
    }

    /** 듣는 중·말하는 중 알약. */
    fun voice(ic: Ic, label: String, sub: String?, active: Boolean): LinearLayout = row(Sp.S4).apply {
        minimumHeight = dp(72)
        setPadding(dp(Sp.S6), dp(Sp.S3), dp(Sp.S8), dp(Sp.S3))
        background = shape(btn, Rd.FULL)
        addView(icon(ic, Sz.ICON_M, if (active) accent else text2))
        addView(this@Ui.text(label, Type.BODY_M, text, oneLine = true))
        if (sub != null) addView(this@Ui.text(sub, Type.BODY_M, text3, oneLine = true))
    }

    // -----------------------------------------------------------------------
    // 프로필 행
    // -----------------------------------------------------------------------

    /** 행 묶음: 카드 바탕, 행 사이 2px 선. */
    fun rows(): LinearLayout = col().apply {
        background = shape(card, Rd.M)
        setPadding(dp(Sp.S6), 0, dp(Sp.S6), 0)
        dividerDrawable = gapDrawable(dp(2), line).apply { setSize(dp(2), dp(2)) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    fun trait(ic: Ic, textS: String, why: String, iconColor: Int = text2): LinearLayout = row(Sp.S4, center = false).apply {
        setPadding(0, dp(Sp.S6), 0, dp(Sp.S6))
        addView(icon(ic, Sz.ICON_M, iconColor))
        addView(col(Sp.S1).apply {
            addView(this@Ui.text(textS, Type.BODY_L))
            addView(this@Ui.text(why, Type.BODY_S, text3, tnum = true))
        }, weight())
    }

    fun pref(label: String, value: View?, action: View?): LinearLayout = row(Sp.S4).apply {
        minimumHeight = dp(112)
        setPadding(0, dp(Sp.S4), 0, dp(Sp.S4))
        addView(this@Ui.text(label, Type.BODY_L), weight())
        if (value != null) addView(value)
        if (action != null) addView(action)
    }

    fun stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit): LinearLayout = row(Sp.S2).apply {
        background = shape(inset, Rd.M)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        addView(iconButton(Ic.MINUS, "줄이기", plain = true, onClick = onMinus).apply { background = pressBg(Color.TRANSPARENT, btnPressed, Rd.S) })
        addView(num(value, "분").apply {
            gravity = Gravity.CENTER
            minWidth = dp(128)
        })
        addView(iconButton(Ic.PLUS, "늘리기", plain = true, onClick = onPlus).apply { background = pressBg(Color.TRANSPARENT, btnPressed, Rd.S) })
    }

    fun situ(ic: Ic, label: String, change: View): LinearLayout = row(Sp.S4).apply {
        minimumHeight = dp(104)
        setPadding(0, dp(Sp.S4), 0, dp(Sp.S4))
        addView(icon(ic, Sz.ICON_M, text2))
        addView(this@Ui.text(label, Type.BODY_L), weight())
        addView(change)
    }

    /** '좌회전 피하기 1.5 → 3분'처럼 앞말 text2, 값 600 text. */
    fun change(lead: String, value: String): TextView {
        val sb = SpannableStringBuilder(lead)
        if (lead.isNotEmpty()) sb.append(" ")
        val s = sb.length
        sb.append(value)
        sb.setSpan(TypefaceSpan(font(600)), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(text), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text(sb, Type.BODY_M, text2, oneLine = true, tnum = true)
    }

    fun spot(name: String, meta: String, passes: Int): LinearLayout = row(Sp.S4).apply {
        setPadding(0, dp(Sp.S6), 0, dp(Sp.S6))
        addView(icon(Ic.SPOT, Sz.ICON_M, accent))
        addView(col(Sp.S1).apply {
            addView(this@Ui.text(name, Type.BODY_L_STRONG))
            addView(this@Ui.text(meta, Type.BODY_S, text3))
        }, weight())
        addView(DotsView(ctx, passes, this@Ui).apply {
            contentDescription = when (passes) {
                0 -> "아직 무사히 지난 적 없음"
                3 -> "이제 익숙해졌어요"
                else -> "3번 중 ${passes}번 무사히 지남, ${3 - passes}번 더 지나면 졸업"
            }
        })
    }

    // -----------------------------------------------------------------------
    // 돌아보기
    // -----------------------------------------------------------------------

    fun reflectCard(): LinearLayout = card(Sp.S8, Rd.L, Sp.S6)

    fun changes(items: List<Pair<String, String>>): LinearLayout = col().apply {
        items.forEach { (k, v) ->
            addView(row(Sp.S6).apply {
                setPadding(0, dp(Sp.S3), 0, dp(Sp.S3))
                addView(this@Ui.text(k, Type.BODY_M, text2), weight())
                addView(this@Ui.text(v, Type.BODY_M_STRONG, text, oneLine = true, tnum = true))
            })
        }
    }

    fun done(label: String): LinearLayout = row(Sp.S3).apply {
        addView(icon(Ic.CHECK, Sz.ICON_M, accent))
        addView(this@Ui.text(label, Type.BODY_M, text2), weight())
    }

    /** 두 대답 버튼(칸 둘). */
    fun answers(a: TextView, b: TextView): LinearLayout = row(Sp.S4).apply {
        addView(a, weight())
        addView(b, weight())
    }

    // -----------------------------------------------------------------------
    // 화면 틀
    // -----------------------------------------------------------------------

    /** 화면 맨 위 줄(높이 112 이상). */
    fun top(): LinearLayout = row(Sp.S4).apply {
        minimumHeight = dp(112)
        setPadding(dp(Sp.S6), dp(Sp.S6), dp(Sp.S6), dp(Sp.S4))
    }

    fun topTitle(title: String, sub: String?): LinearLayout = col(Sp.S1).apply {
        addView(this@Ui.text(title, Type.TITLE_M, text, oneLine = true))
        if (sub != null) addView(this@Ui.text(sub, Type.BODY_S, text3, oneLine = true, tnum = true))
    }

    fun logo(onLongClick: (() -> Unit)? = null): ImageView = ImageView(ctx).apply {
        val d = LogoDrawable(text, dp(36))
        setImageDrawable(d)
        contentDescription = "차츰"
        layoutParams = LinearLayout.LayoutParams(d.intrinsicWidth, dp(36))
        if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
    }

    fun sec(title: String, action: View? = null): LinearLayout = col(Sp.S4).apply {
        addView(row(Sp.S4).apply {
            addView(this@Ui.text(title, Type.BODY_L_STRONG, text2, oneLine = true), weight())
            if (action != null) addView(action)
        })
    }

    fun headline(s: String): TextView = text(s, Type.HEADLINE_S)

    fun locked(label: String): LinearLayout = row(Sp.S3).apply {
        background = shape(card, Rd.M)
        setPadding(dp(Sp.S6), dp(Sp.S6), dp(Sp.S6), dp(Sp.S6))
        addView(icon(Ic.LOCK, Sz.ICON_S, text2))
        addView(this@Ui.text(label, Type.BODY_M, text2), weight())
    }

    fun empty(ic: Ic, label: String): LinearLayout = col(Sp.S4).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(Sp.S6), dp(Sp.S16), dp(Sp.S6), dp(Sp.S16))
        addView(icon(ic, Sz.ICON_L, text3))
        addView(this@Ui.text(label, Type.BODY_L, text2).apply { gravity = Gravity.CENTER })
    }

    fun drivePill(label: String): TextView = text(label, Type.LABEL_M, text2, oneLine = true).apply {
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(40)
        setPadding(dp(Sp.S4), 0, dp(Sp.S4), 0)
        background = shape(btn, Rd.FULL)
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(text2)
            setSize(dp(12), dp(12))
        }
        dot.setBounds(0, 0, dp(12), dp(12))
        setCompoundDrawablesRelative(dot, null, null, null)
        compoundDrawablePadding = dp(Sp.S2)
    }

    fun toastView(msg: String, action: String?, onAction: (() -> Unit)?): LinearLayout = row(Sp.S6).apply {
        minimumHeight = dp(80)
        setPadding(dp(Sp.S8), dp(Sp.S4), dp(if (action != null) Sp.S6 else Sp.S8), dp(Sp.S4))
        background = shape(toast, Rd.M)
        elevation = dpf(8)
        addView(this@Ui.text(msg, Type.BODY_M, onToast).apply { maxWidth = dp(712) }, LinearLayout.LayoutParams(-2, -2, 1f))
        if (action != null) {
            addView(this@Ui.text(action, Type.BODY_M_STRONG, onToast, oneLine = true).apply {
                gravity = Gravity.CENTER
                minHeight = dp(Sz.TOUCH)
                setPadding(dp(Sp.S3), 0, dp(Sp.S3), 0)
                background = pressBg(Color.TRANSPARENT, 0x33FFFFFF, Rd.S)
                clickable(this) { onAction?.invoke() }
            })
        }
    }

    fun popup(title: String, desc: String?, actions: List<TextView>, extra: View? = null): LinearLayout = col(Sp.S4).apply {
        background = shape(raised, Rd.L)
        elevation = dpf(16)
        setPadding(dp(Sp.S10), dp(Sp.S10), dp(Sp.S10), dp(Sp.S10))
        addView(this@Ui.text(title, Type.TITLE_M))
        if (desc != null) addView(this@Ui.text(desc, Type.BODY_M, text2))
        if (extra != null) addView(extra)
        addView(row(Sp.S4).apply {
            actions.forEach { addView(it, weight()) }
        }, lp().apply { topMargin = dp(Sp.S6) })
    }

    fun tintList(color: Int) = ColorStateList.valueOf(color)
}
