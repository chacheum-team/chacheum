package com.chacheum.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.PathParser

/*
 * 차츰 디자인 시스템(tokens.json, components/bundle.css)을 코드로 옮긴 곳.
 * 크기는 dp(Pleos Connect 160dpi에서 1dp = 1px), 글자는 sp.
 */

/** 글자 단계: 크기/줄 높이/굵기. 타입 표에 있는 조합만 씀. */
enum class Type(val size: Float, val line: Float, val weight: Int) {
    HEADLINE_S(48f, 64f, 700),
    TITLE_L(40f, 52f, 700),
    TITLE_M(36f, 44f, 700),
    TITLE_S(32f, 40f, 700),
    BODY_L(30f, 38f, 400),
    BODY_L_STRONG(30f, 38f, 600),
    BODY_M(28f, 36f, 400),
    BODY_M_STRONG(28f, 36f, 600),
    BODY_S(26f, 34f, 400),
    LABEL_M(24f, 28f, 400),
    LABEL_S(20f, 24f, 400),
}

/** 간격(space-n), 모서리(radius-*), 크기 토큰. */
object Sp {
    const val S1 = 4
    const val S2 = 8
    const val S3 = 12
    const val S4 = 16
    const val S6 = 24
    const val S8 = 32
    const val S10 = 40
    const val S12 = 48
    const val S16 = 64
}

object Rd {
    const val XS = 8
    const val S = 16
    const val M = 24
    const val L = 32
    const val FULL = 9999
}

object Sz {
    const val TOUCH = 64
    const val BTN_L = 100
    const val BTN_M = 80
    const val BTN_S = 64
    const val ICON_L = 48
    const val ICON_M = 40
    const val ICON_S = 32
    const val ICON_XS = 24
    const val CONTENT_MAX = 792
    const val WIN_PORTRAIT = 840
    /** 이 폭부터 가로 창 두 칸(Connect-WF 1256보다 조금 좁게). */
    const val TWO_COLUMN_MIN = 1200
}

/** Asta Sans 세 굵기(400·600·700). 차 화면 앱이 쓰는 42dot 글꼴. */
object Fonts {
    private var regular: Typeface? = null
    private var semibold: Typeface? = null
    private var bold: Typeface? = null

    fun get(ctx: Context, weight: Int): Typeface {
        val res = ctx.resources
        fun load(id: Int, style: Int) = runCatching { res.getFont(id) }.getOrNull() ?: Typeface.defaultFromStyle(style)
        return when {
            weight >= 700 -> bold ?: load(R.font.asta_sans_bold, Typeface.BOLD).also { bold = it }
            weight >= 600 -> semibold ?: load(R.font.asta_sans_semibold, Typeface.BOLD).also { semibold = it }
            else -> regular ?: load(R.font.asta_sans_regular, Typeface.NORMAL).also { regular = it }
        }
    }
}

// ---------------------------------------------------------------------------
// 아이콘: 48 격자, 선 3, 둥근 끝. 색은 글자색을 따름(디자인 시스템 Icons, 임시 아이콘).
// ---------------------------------------------------------------------------

private fun circle(cx: Double, cy: Double, r: Double) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun rect(x: Double, y: Double, w: Double, h: Double, rx: Double) =
    "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx ${rx}v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} ${rx}h${-(w - 2 * rx)}" +
        "a$rx $rx 0 0 1 ${-rx} ${-rx}v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

enum class Ic(val strokes: List<String>, val fills: List<String> = emptyList()) {
    CLOSE(listOf("M13 13l22 22M35 13L13 35")),
    PERSON(listOf(circle(24.0, 15.0, 7.0), "M10 41a14 14 0 0 1 28 0")),
    GUEST(listOf(circle(21.0, 15.0, 7.0), "M8 41a13 13 0 0 1 21-10", "M33 32l8 8M41 32l-8 8")),
    PLUS(listOf("M24 10v28M10 24h28")),
    MINUS(listOf("M10 24h28")),
    CHECK(listOf("M10 25l9 9 19-19")),
    SEARCH(listOf(circle(21.0, 21.0, 13.0), "M31 31l10 10")),
    SPEAKER(listOf("M8 19h7l9-7v24l-9-7H8z", "M31 18a8 8 0 0 1 0 12M36 13a14 14 0 0 1 0 22")),
    ROUTE(listOf(circle(12.0, 36.0, 4.0), circle(36.0, 12.0, 4.0), "M16 36h13a6 6 0 0 0 0-12H19a6 6 0 0 1 0-12h13")),
    SPOT(listOf("M24 43S11 31 11 20a13 13 0 0 1 26 0c0 11-13 23-13 23z", "M24 14v8"), listOf(circle(24.0, 27.5, 2.0))),
    SIGNAL(listOf("M22 9L7 24l15 15v-9h12V18H22z")),
    STEERING(listOf(circle(24.0, 24.0, 17.0), circle(24.0, 24.0, 5.0), "M8 22h11M29 22h11M24 29v12")),
    CHILD(listOf(circle(17.0, 11.0, 5.0), "M8 42V28a9 9 0 0 1 18 0v14", circle(34.0, 22.0, 4.0), "M28 42v-8a6 6 0 0 1 12 0v8")),
    RAIN(listOf("M15 30h20a7 7 0 0 0 .5-14 11 11 0 0 0-21.3 2A6 6 0 0 0 15 30z", "M18 36l-2 5M26 36l-2 5M34 36l-2 5")),
    NIGHT(listOf("M42 25.6A18 18 0 1 1 22.4 6 14 14 0 0 0 42 25.6z")),
    ROAD(listOf("M17 6L9 42M31 6l8 36", "M24 9v5M24 21v6M24 34v6")),
    STRAIGHT(listOf("M24 42V8", "M15 17l9-9 9 9")),
    TURN_RIGHT(listOf("M18 42V25a7 7 0 0 1 7-7h12", "M30 11l7 7-7 7")),
    LOCK(listOf(rect(11.0, 21.0, 26.0, 20.0, 3.0), "M16 21v-5a8 8 0 0 1 16 0v5")),
    TRASH(listOf("M8 13h32M19 13V8h10v5", "M12 13l2 28h20l2-28", "M21 21v12M27 21v12")),
    NOTE(listOf(rect(10.0, 6.0, 28.0, 36.0, 4.0), "M17 16h14M17 24h14M17 32h8")),
    ALERT(listOf("M24 7l19 33H5z", "M24 19v10"), listOf(circle(24.0, 34.5, 2.0))),
}

private object Paths {
    private val cache = HashMap<String, Path>()
    fun of(d: String): Path = cache.getOrPut(d) { PathParser.createPathFromPathData(d) }
}

/** 선 아이콘 한 개. 크기는 setBounds로 정함(48 격자를 그 크기로 늘림). */
class IconDrawable(private val ic: Ic, color: Int, private val sizePx: Int) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 3f
        this.color = color
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = b.width() / 48f
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(s, s)
        ic.strokes.forEach { canvas.drawPath(Paths.of(it), stroke) }
        ic.fills.forEach { canvas.drawPath(Paths.of(it), fill) }
        canvas.restore()
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** 워드마크 '차츰'(작은 판: 글자 한 색). 높이 48 아래에서 씀. */
class LogoDrawable(color: Int, private val heightPx: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = b.height() / 954f
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(s, s)
        canvas.drawPath(Paths.of(WORDMARK), paint)
        canvas.restore()
    }

    override fun getIntrinsicWidth() = (heightPx * 2268f / 954f).toInt()
    override fun getIntrinsicHeight() = heightPx
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private const val WORDMARK =
            "M965 521L874 949L870 953L865 954L683 954L680 953L677 950L677 946L857 101L861 89L872 70L878 61L892 46L900 39L917 29L926 24L944 19L964 15L984 15L1002 19L1018 25L1031 34L1043 46L1050 62L1054 79L1054 100L1052 112L995 383L1097 383L1112 385L1127 388L1145 396L1162 407L1178 420L1208 450L1217 456L2217 456L2226 457L2237 461L2245 465L2254 473L2259 480L2265 490L2267 499L2268 511L2267 525L2261 543L2253 555L2243 567L2228 580L2215 587L2197 593L2183 594L1179 594L1164 592L1149 589L1137 584L1123 576L1103 561L1070 529L1059 521Z" +
                "M1599 74L1607 56L1618 41L1630 29L1644 18L1660 10L1679 5L1698 1L1719 0L1739 1L1757 5L1773 11L1785 19L1795 29L1802 42L1806 57L1806 74L2060 74L2079 77L2092 82L2098 86L2107 95L2113 104L2115 110L2116 117L2117 129L2113 150L2105 169L2096 182L2091 187L2080 196L2066 203L2058 207L2041 211L2021 212L1765 212L1748 247L1785 244L1821 242L1854 242L1885 244L1914 247L1941 253L1966 260L1988 270L2009 281L2027 295L2043 311L2056 330L2067 351L2076 374L2083 401L2085 419L2084 424L2080 428L2076 429L1884 429L1881 427L1875 416L1867 405L1856 395L1844 386L1830 378L1815 372L1798 366L1780 362L1761 360L1720 357L1675 359L1651 362L1604 370L1561 388L1516 403L1470 414L1447 419L1401 426L1377 428L1332 430L1310 430L1267 428L1246 426L1230 422L1217 416L1206 407L1202 402L1196 390L1193 377L1193 361L1197 344L1203 327L1212 313L1223 302L1234 293L1241 289L1248 286L1262 281L1278 279L1294 278L1345 282L1386 281L1426 277L1463 270L1497 260L1527 246L1540 239L1552 231L1563 222L1572 212L1323 212L1304 211L1288 206L1276 200L1267 191L1263 186L1259 176L1257 163L1259 143L1265 123L1269 116L1277 105L1287 94L1300 86L1314 80L1332 75L1342 74Z" +
                "M1151 948L1139 944L1130 939L1122 934L1117 927L1112 918L1109 908L1108 897L1107 884L1110 854L1117 817L1132 751L1141 717L1146 702L1152 690L1158 679L1165 669L1172 661L1182 653L1192 648L1203 643L1216 639L1231 636L1248 634L1286 633L1899 633L1932 636L1946 639L1957 643L1967 648L1975 654L1981 661L1985 670L1988 679L1989 690L1990 702L1988 717L1983 750L1942 946L1940 950L1935 954L1217 954L1180 953Z" +
                "M1322 814L1323 819L1325 822L1329 824L1335 825L1765 825L1774 781L1775 772L1774 767L1770 764L1767 763L1760 762L1346 762L1337 765L1334 768L1331 772L1328 781Z" +
                "M368 122L374 99L377 88L386 68L398 51L412 37L429 25L448 18L458 15L481 11L506 11L527 15L545 21L559 31L569 44L575 59L578 77L577 98L572 122L696 122L717 124L733 130L746 138L754 150L760 162L762 177L762 192L758 214L753 230L745 245L735 258L723 270L709 280L691 287L671 291L532 291L510 394L498 435L484 482L508 491L530 502L551 515L570 529L586 545L601 563L613 584L624 607L631 633L637 662L639 693L639 727L637 765L631 806L622 850L612 892L608 896L603 898L434 898L429 896L428 893L428 890L439 852L447 810L451 774L450 741L448 727L445 714L440 701L435 690L429 679L421 670L413 661L403 653L374 697L342 736L326 755L309 772L273 804L255 820L217 846L198 858L158 879L138 889L108 900L90 904L72 905L55 903L39 898L26 890L15 881L10 874L3 861L0 847L0 832L3 817L9 803L16 789L21 782L33 770L40 764L48 759L81 740L106 725L129 708L151 691L172 672L191 652L210 630L227 606L243 581L258 554L272 525L286 494L298 461L309 425L319 387L340 291L206 291L186 289L169 283L157 275L151 270L144 258L140 245L138 230L139 215L142 200L147 185L151 177L160 163L172 149L185 139L193 134L211 126L231 123L242 122Z"
    }
}

/**
 * 경로 그림(208×120). 세 후보 길을 다 그리고, 이 카드의 길만 진하게.
 * 추천 후보는 accent, 다른 후보는 text2, 나머지 길은 line.
 */
class SketchView(ctx: Context, private val index: Int, private val mine: Boolean, private val ui: Ui) : View(ctx) {
    private val other = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 6f; color = ui.line
    }
    private val self = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 8f
        color = if (mine) ui.accent else ui.text2
    }
    private val startFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = ui.card }
    private val startStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = ui.text }
    private val endFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = ui.text }

    override fun onDraw(canvas: Canvas) {
        val s = width / 208f
        canvas.save()
        canvas.scale(s, s)
        PATHS.forEachIndexed { i, d -> if (i != index) canvas.drawPath(Paths.of(d), other) }
        canvas.drawPath(Paths.of(PATHS[index.coerceIn(0, 2)]), self)
        canvas.drawCircle(24f, 96f, 10f, startFill)
        canvas.drawCircle(24f, 96f, 10f, startStroke)
        canvas.drawCircle(184f, 24f, 10f, endFill)
        canvas.restore()
    }

    companion object {
        /** 빠른 길(사선), 내 길(꺾인 길), 큰길 위주(바깥 큰길). */
        val PATHS = listOf("M24 96V60l36-36h124", "M24 96h72V24h88", "M24 96h160V24")
    }
}

/** 졸업까지의 점 세 개(지난 수만큼 채움). */
class DotsView(ctx: Context, private val on: Int, private val ui: Ui) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = ui.accent }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ui.lineStrong }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(ui.dp(20 * 3 + Sp.S2 * 2), ui.dp(20))
    }

    override fun onDraw(canvas: Canvas) {
        val d = ui.dpf(20)
        val gap = ui.dpf(Sp.S2)
        ring.strokeWidth = ui.dpf(3)
        for (i in 0 until 3) {
            val cx = i * (d + gap) + d / 2
            val cy = d / 2
            if (i < on) canvas.drawCircle(cx, cy, d / 2, fill) else canvas.drawCircle(cx, cy, d / 2 - ring.strokeWidth / 2, ring)
        }
    }
}
