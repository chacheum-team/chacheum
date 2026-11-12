package com.chacheum.app

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 후보 경로
// ---------------------------------------------------------------------------

/**
 * 후보 경로 하나. 경로 선택 모델은 소요 시간·좌회전 수·익숙함 세 특성으로 비교함.
 * 순정 내비에는 같은 목적지를 이 경로의 경로 옵션으로 요청함.
 */
data class Route(
    val id: String,
    val name: String,
    /** 문장 속 짧은 이름. 모두 '길'로 끝나서 조사는 '을', '로'를 씀. */
    val short: String,
    val minutes: Int,
    val lefts: Int,
    /** 지난 주행과 겹치는 구간 비율 (0~1). */
    val fam: Double,
    val km: Double,
    val option: RouteOption,
    val optionName: String,
)

/**
 * 시연 경로. 목적지는 NaviHelper 샘플과 같은 장소라 에뮬레이터 순정 내비가 바로 찾음.
 * 경로별 숫자는 시연용으로 정한 값이고, 실제 앱에서는 서버가 후보 경로의 특성을 계산해 줌.
 */
object DemoTrip {
    const val DEST_NAME = "소프트웨어드림센터"
    const val DEST_AREA = "성남 판교"

    fun destination(option: RouteOption) = RouteInfo(
        latitude = 37.41275833,
        longitude = 127.09481944,
        poiName = DEST_NAME,
        poiId = "36khp2hdj2cfcm5nqhe3",
        address = "경기도 성남시 수정구 창업로40번길 6",
        poiSubId = "0",
        routeOption = option,
    )

    val routes = listOf(
        Route("A", "가장 빠른 길", "빠른 길", 24, 4, 0.2, 15.2, RouteOption.SHORT_TIME, "최단 시간"),
        Route("B", "좌회전 적은 길", "좌회전 적은 길", 27, 2, 0.3, 16.0, RouteOption.COMFORTABLE, "편한 길"),
        Route("C", "큰길 위주 길", "큰길", 32, 0, 0.8, 19.4, RouteOption.HIGHWAY, "고속도로 우선"),
    )

    fun route(id: String?) = routes.firstOrNull { it.id == id }

    /** 지난번에 지나쳤던 곳. 시연 모드에서는 경로의 42% 지점에서 300 m 앞 안내를 함. */
    const val CAUTION_AT = 0.42
    const val CAUTION_LEAD = 0.07
    const val CAUTION_SPOT = "판교 진입 갈림길"
    const val CAUTION_TEXT = "지난번에 지나쳤던 갈림길이에요. 미리 오른쪽 차로로 가요."
}

// ---------------------------------------------------------------------------
// 운전자 프로필
// ---------------------------------------------------------------------------

/** 경로 선택 모델의 가중치. t: 1분당, l: 좌회전 1번당, f: 익숙함(0~1) 1당. */
data class Weights(val t: Double, val l: Double, val f: Double) {
    operator fun get(k: Int) = when (k) {
        0 -> t
        1 -> l
        else -> f
    }
}

data class DriverProfile(
    val id: String,
    val name: String,
    val sub: String,
    /** 처음 질문 3개로 정한 시작 가중치 w₀. 학습은 여기서 멀어지지 않게 당김(규제). */
    val start: Weights,
    /** 상황 보정: 동승자가 있으면 좌회전 가중치에 더함. */
    val withPassengerL: Double,
    /** 상황 보정: 비(와이퍼)·밤(전조등)이면 익숙함 가중치에 더함. */
    val rainF: Double,
    val nightF: Double,
    val traits: List<String>,
    val road: String,
    /** 게스트는 기록하지 않음. */
    val record: Boolean = true,
)

object Profiles {
    val me = DriverProfile(
        id = "me", name = "나", sub = "면허 2년 차",
        start = Weights(t = -0.5, l = -1.0, f = 0.0),
        withPassengerL = -0.5, rainF = 2.0, nightF = 1.0,
        traits = listOf(
            "차로를 바꾸기 전 깜빡이를 먼저 켜는 편이에요",
            "복잡한 교차로에서 가끔 지나치는 편이에요",
            "핸들을 부드럽게 쓰는 편이에요",
        ),
        road = "고속도로보다 큰 국도가 편해요",
    )
    val parent = DriverProfile(
        id = "parent", name = "부모님", sub = "운전 20년",
        start = Weights(t = -0.5, l = -0.2, f = 0.0),
        withPassengerL = -0.2, rainF = 1.0, nightF = 0.5,
        traits = listOf(
            "빨리 가는 길을 좋아해요",
            "고속도로를 자주 타요",
            "브레이크를 일찍 밟는 편이에요",
        ),
        road = "고속도로를 자주 타요",
    )
    val guest = DriverProfile(
        id = "guest", name = "게스트", sub = "기록하지 않음",
        start = Weights(t = -0.5, l = -0.5, f = 0.0),
        withPassengerL = -0.3, rainF = 1.0, nightF = 0.5,
        traits = listOf("게스트로 타면 운전 기록을 남기지 않아요"),
        road = "보통 빠른 길을 추천해요",
        record = false,
    )
    val all = listOf(me, parent, guest)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: me
}

/** 지금 상황. 좌석 착석·와이퍼·전조등 신호에서 옴. */
data class Ctx(val passenger: Boolean, val rain: Boolean, val night: Boolean) {
    fun text(): String = listOf(
        if (passenger) "동승자 있음" else "혼자",
        if (rain) "비" else "맑음",
        if (night) "밤" else "낮",
    ).joinToString(" · ")
}

// ---------------------------------------------------------------------------
// 경로 선택 모델 (조건부 로짓)
// ---------------------------------------------------------------------------

data class Scored(val probs: Map<String, Double>, val best: Route, val w: Weights)

data class Learned(val route: Route, val before: Weights, val after: Weights)

object Choice {
    const val ETA = 0.01
    const val LAMBDA = 0.1

    /** 상황 보정을 더한 가중치. */
    fun effective(base: Weights, p: DriverProfile, c: Ctx) = Weights(
        t = base.t,
        l = base.l + if (c.passenger) p.withPassengerL else 0.0,
        f = base.f + (if (c.rain) p.rainF else 0.0) + (if (c.night) p.nightF else 0.0),
    )

    fun features(r: Route) = doubleArrayOf(r.minutes.toDouble(), r.lefts.toDouble(), r.fam)

    /** 효용 U(r) = w_t·시간 + w_l·좌회전 + w_f·익숙함 */
    fun utility(w: Weights, r: Route): Double {
        val x = features(r)
        return w.t * x[0] + w.l * x[1] + w.f * x[2]
    }

    /** 고를 확률 P(r) = exp U(r) / Σ exp U */
    fun score(w: Weights, routes: List<Route> = DemoTrip.routes): Scored {
        val u = routes.associate { it.id to utility(w, it) }
        val m = u.values.maxOrNull() ?: 0.0
        val e = u.mapValues { exp(it.value - m) }
        val sum = e.values.sum()
        val probs = e.mapValues { it.value / sum }
        val best = routes.maxByOrNull { probs[it.id] ?: 0.0 } ?: routes.first()
        return Scored(probs, best, w)
    }

    /** 좌회전 1번을 피하려고 더 쓰는 분 = w_l ÷ w_t */
    fun leftMinutes(w: Weights): Double = if (w.t == 0.0) 0.0 else w.l / w.t

    /**
     * 고른 경로로 가중치를 고침.
     * w ← w + η(x(r*) − Σ P(r)·x(r)) − λ(w − w₀)
     * 확률은 그때 상황을 반영한 가중치로 계산하고, 고치는 것은 기본 가중치임.
     */
    fun learn(base: Weights, start: Weights, eff: Weights, chosen: Route, routes: List<Route> = DemoTrip.routes): Weights {
        val p = score(eff, routes).probs
        val expected = DoubleArray(3)
        routes.forEach { r -> features(r).forEachIndexed { k, v -> expected[k] += (p[r.id] ?: 0.0) * v } }
        val x = features(chosen)
        val next = DoubleArray(3) { k -> base[k] + ETA * (x[k] - expected[k]) - LAMBDA * (base[k] - start[k]) }
        return Weights(next[0], next[1], next[2])
    }

    /** 추천 이유 한 줄. 가장 빠른 길과 비교함. */
    fun reason(best: Route, w: Weights, routes: List<Route> = DemoTrip.routes): String {
        val fastest = routes.minByOrNull { it.minutes } ?: return ""
        if (best.id == fastest.id) return "가장 빨리 가는 길이에요"
        val parts = mutableListOf<String>()
        if (fastest.lefts > best.lefts) parts += "좌회전 ${fastest.lefts - best.lefts}번 줄이고"
        parts += "+${best.minutes - fastest.minutes}분"
        var s = parts.joinToString(" ")
        if (w.f > 0.05 && best.fam > fastest.fam) s += " · 익숙한 길 ${pct(best.fam)}%"
        return s
    }
}

// ---------------------------------------------------------------------------
// 깜빡이 먼저 판정 (확인용 앱과 같은 규칙)
// ---------------------------------------------------------------------------

enum class Turn { OFF, LEFT, RIGHT }

/**
 * 조향각이 기준(15°)을 넘으면 차로 변경·회전으로 보고, 같은 쪽 깜빡이를 먼저 켰는지 봄.
 * 깜빡이가 지금 켜져 있거나, 꺼진 지 5초가 안 됐으면 '먼저 켬'.
 * 조향이 5° 안으로 돌아오면 다음 판정을 준비함.
 */
class SignalJudge {
    var threshold = 15f
    /** Pleos 조향각 부호. 확인용 앱 결과로 맞춤 (왼쪽이 음수면 true). */
    var leftIsNegative = true

    private var armed = true
    private var current = Turn.OFF
    private val onAt = HashMap<Turn, Long>()
    private val offAt = HashMap<Turn, Long>()

    var count = 0
        private set
    var first = 0
        private set

    fun onTurn(v: Turn, now: Long = System.currentTimeMillis()) {
        if (v == current) return
        if (current != Turn.OFF) offAt[current] = now
        if (v != Turn.OFF) onAt[v] = now
        current = v
    }

    /** 판정이 나면 그 결과 문장을, 아니면 null. */
    fun onSteer(angle: Float, now: Long = System.currentTimeMillis()): Judgment? {
        if (armed && abs(angle) >= threshold) {
            armed = false
            val left = if (leftIsNegative) angle < 0 else angle > 0
            val dir = if (left) Turn.LEFT else Turn.RIGHT
            val lead = leadMs(dir, now)
            count++
            if (lead != null) first++
            return Judgment(dir, lead)
        }
        if (!armed && abs(angle) <= 5f) armed = true
        return null
    }

    private fun leadMs(dir: Turn, now: Long): Long? {
        val on = onAt[dir] ?: return null
        if (current == dir) return now - on
        val off = offAt[dir] ?: return null
        return if (off >= on && now - off <= 5_000) now - on else null
    }

    fun reset() {
        count = 0
        first = 0
        armed = true
    }
}

data class Judgment(val dir: Turn, val leadMs: Long?) {
    val signaled get() = leadMs != null
    fun sentence(): String {
        val side = if (dir == Turn.LEFT) "왼쪽" else "오른쪽"
        return if (leadMs != null) {
            "$side 깜빡이를 ${String.format(Locale.US, "%.1f", leadMs / 1000.0)}초 먼저 켰어요"
        } else {
            "깜빡이 없이 ${side}으로 움직였어요"
        }
    }
}

// ---------------------------------------------------------------------------
// 숫자 표시
// ---------------------------------------------------------------------------

fun pct(v: Double): Int = (v * 100).roundToInt()

fun fmt1(v: Double): String {
    val r = (v * 10).roundToInt() / 10.0
    return if (abs(r - r.roundToInt()) < 1e-9) r.roundToInt().toString() else String.format(Locale.US, "%.1f", r)
}
