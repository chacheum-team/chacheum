package com.chacheum.app

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 후보 경로
// ---------------------------------------------------------------------------

/**
 * 후보 경로 하나. 경로 선택 모델은 걸리는 시간·회전 수(좌회전+유턴)·익숙함으로 비교하고,
 * 아이가 타면 어린이보호구역 수도 봄.
 */
data class Route(
    val id: String,
    /** 카드·버튼 이름: 빠른 길, 내 길, 큰길 위주 */
    val name: String,
    /** 문장 속 이름. 모두 '길'로 끝나서 조사는 '을', '로'를 씀. */
    val noun: String,
    val minutes: Int,
    val lefts: Int,
    val uturns: Int,
    /** 지나는 어린이보호구역 수. */
    val zones: Int,
    /** 지난 주행과 겹치는 구간 비율 (0~1). */
    val fam: Double,
    /** 길의 성질 한 가지(큰길 비중 등). 사실 줄에 씀. */
    val roadFact: String?,
    val km: Double,
    val option: RouteOption,
    /** 이 길로 가려고 순정 내비에 넣는 경유지. 없으면 경로 옵션으로만 요청함. */
    val via: Via?,
) {
    val turns get() = lefts + uturns
}

data class Via(val name: String, val lat: Double, val lng: Double, val address: String)

/** 나만의 주의 지점. pos는 시연 경로에서의 위치(0~1). */
data class Spot(
    val id: String,
    val name: String,
    val meta: String,
    val say: String,
    val say2: String,
    /** 차로 칸: 차로 수와 갈 차로(0부터). 차로 정보가 없으면 null. */
    val lanes: Int?,
    val goLane: Int?,
    val pos: Double,
)

/**
 * 시연 경로. 에뮬레이터의 처음 위치(성남 수정구)에서 서울숲까지.
 * 경로별 숫자는 시연용으로 정한 값이고, 실제 앱에서는 서버가 후보 경로의 특성을 계산해 줌.
 */
object DemoTrip {
    const val DEST_NAME = "서울숲"

    fun destination(option: RouteOption) = RouteInfo(
        latitude = 37.544388,
        longitude = 127.037442,
        poiName = DEST_NAME,
        poiId = "",
        address = "서울특별시 성동구 뚝섬로 273",
        poiSubId = "0",
        routeOption = option,
    )

    fun waypoint(v: Via) = RequestWaypointInfo(
        latitude = v.lat,
        longitude = v.lng,
        poiName = v.name,
        waypointIndex = WaypointIndex.FIRST,
        poiId = "",
        address = v.address,
        poiSubId = "0",
    )

    val routes = listOf(
        Route("A", "빠른 길", "빠른 길", 32, 3, 1, 2, 0.3, null, 17.8, RouteOption.SHORT_TIME, null),
        Route(
            "B", "내 길", "내 길", 35, 1, 0, 1, 0.8, "큰 국도 60%", 18.6, RouteOption.RECOMMENDED,
            Via("성수역", 37.544581, 127.055961, "서울특별시 성동구 아차산로 지하 100"),
        ),
        Route("C", "큰길 위주", "큰길 위주 길", 38, 0, 0, 0, 0.2, "큰길 85%", 21.4, RouteOption.COMFORTABLE, null),
    )

    fun route(id: String?) = routes.firstOrNull { it.id == id }
    val fastest get() = routes.minByOrNull { it.minutes }!!

    val spots = listOf(
        Spot("seongsu", "성수사거리", "회전 놓침 · 오른쪽 차로로", "지난번에 지나쳤던 교차로예요", "미리 오른쪽 차로로 가요", 4, 3, 0.42),
        Spot("ttukseom", "뚝섬역 앞 합류", "급정거 2번 · 합류 차로", "지난번에 급히 멈췄던 곳이에요", "합류 차로를 미리 봐요", null, null, 0.86),
    )

    /** '아니에요'라고 답하면 새로 생기는 주의 지점. 시연 경로 밖이라 알림은 다음 주행부터. */
    val missedSpot = Spot("dongbu", "동부간선도로 진입로", "회전 놓침 · 오늘 주행", "지난번에 지나쳤던 진입로예요", "미리 오른쪽 차로로 가요", null, null, 2.0)

    /** 시연 모드에서 경로를 벗어나는 곳과 그 길 이름. */
    const val DEVIATE_AT = 0.65
    const val DEVIATE_ROAD = "동부간선도로"

    /** 300m 앞 알림을 띄우는 거리(진행률). 75초 시연에서 약 5초. */
    const val LEAD = 0.07
}

// ---------------------------------------------------------------------------
// 운전자 프로필
// ---------------------------------------------------------------------------

/**
 * 경로 선택 모델의 가중치. t10: 10분당, l: 회전(좌회전·유턴) 1번당, f: 익숙함(0~1) 1당, z: 보호구역 1곳당.
 * 시간은 10분 단위로 넣어 특성들의 크기를 맞춤(학습률 하나로 고침).
 */
data class Weights(val t10: Double, val l: Double, val f: Double, val z: Double = 0.0) {
    operator fun get(k: Int) = when (k) {
        0 -> t10
        1 -> l
        2 -> f
        else -> z
    }
}

enum class RoadPref { NATIONAL, HIGHWAY }

/** 처음 두는 성향 문장(시연용 지난 기록). 계산하는 깜빡이 문장은 따로 만듦. */
data class Trait(val icon: Ic, val text: String, val why: String)

data class DriverProfile(
    val id: String,
    val name: String,
    /** 처음 질문 3개로 정한 시작 가중치 w₀. 학습은 여기서 멀어지지 않게 당김(규제). */
    val start: Weights,
    /** 상황 보정: 어른 동승자·아이가 타면 회전 가중치에 더함, 아이면 보호구역 가중치도. */
    val adultL: Double,
    val childL: Double,
    val childZ: Double,
    /** 상황 보정: 비(와이퍼)·밤(전조등)이면 익숙함 가중치에 더함. 밤은 아직 배우는 중이라 0. */
    val rainF: Double,
    val nightF: Double,
    val road: RoadPref,
    val traits: List<Trait>,
    /** 시연용 지난 기록. */
    val seedTrips: Int,
    val seedSignals: String,
    val seedChoices: Int,
    val seedRainTrips: Int,
    /** 게스트는 기록하지 않음. */
    val record: Boolean = true,
)

object Profiles {
    val me = DriverProfile(
        id = "me", name = "나",
        start = Weights(t10 = -5.0, l = -0.75, f = 0.0),
        adultL = -0.25, childL = -0.75, childZ = -1.0, rainF = 1.0, nightF = 0.0,
        road = RoadPref.NATIONAL,
        traits = listOf(
            Trait(Ic.STEERING, "핸들을 부드럽게 쓰는 편이에요", "지난달과 비슷해요"),
            Trait(Ic.SPOT, "복잡한 교차로에서 가끔 지나치는 편이에요", "최근 20번 중 3번 · 지난달 5번"),
        ),
        seedTrips = 30, seedSignals = "111101111101", seedChoices = 8, seedRainTrips = 4,
    )
    val jisu = DriverProfile(
        id = "jisu", name = "지수",
        start = Weights(t10 = -6.0, l = -0.3, f = 0.0),
        adultL = -0.2, childL = -0.6, childZ = -0.8, rainF = 0.6, nightF = 0.0,
        road = RoadPref.HIGHWAY,
        traits = listOf(
            Trait(Ic.ROAD, "고속도로를 자주 타요", "최근 18번 중 11번"),
            Trait(Ic.STEERING, "핸들을 크게 쓰지 않는 편이에요", "지난달과 비슷해요"),
        ),
        seedTrips = 18, seedSignals = "101110110101", seedChoices = 10, seedRainTrips = 2,
    )
    val guest = DriverProfile(
        id = "guest", name = "게스트",
        start = Weights(t10 = -5.0, l = -0.4, f = 0.0),
        adultL = -0.2, childL = -0.5, childZ = -0.8, rainF = 0.5, nightF = 0.0,
        road = RoadPref.NATIONAL, traits = emptyList(),
        seedTrips = 0, seedSignals = "", seedChoices = 0, seedRainTrips = 0,
        record = false,
    )

    /** 질문 3개로 시작하는 새 운전자. */
    fun newDriver(fast: Boolean, turnsHard: Boolean, road: RoadPref) = DriverProfile(
        id = "new", name = "새 운전자",
        start = Weights(t10 = if (fast) -6.0 else -4.0, l = if (turnsHard) -0.8 else -0.25, f = 0.0),
        adultL = -0.25, childL = -0.6, childZ = -0.8, rainF = 0.0, nightF = 0.0,
        road = road, traits = emptyList(),
        seedTrips = 0, seedSignals = "", seedChoices = 0, seedRainTrips = 0,
    )

    val family = listOf(me, jisu)
}

enum class People { SOLO, ADULT, CHILD }

/** 지금 상황. 좌석 착석·와이퍼·전조등 신호에서 오고, 경로 고르기에서 바로잡을 수 있음. */
data class Ctx(val people: People, val rain: Boolean, val night: Boolean) {
    companion object {
        val PLAIN = Ctx(People.SOLO, rain = false, night = false)
    }
}

// ---------------------------------------------------------------------------
// 경로 선택 모델 (조건부 로짓)
// ---------------------------------------------------------------------------

data class Scored(val probs: Map<String, Double>, val best: Route, val w: Weights)

data class Learned(val route: Route, val before: Weights, val after: Weights)

object Choice {
    /** 학습률. 한 번 고를 때 좌회전 1번이 0.3분 안팎 움직이는 크기. */
    const val ETA = 0.3
    /** 시작값으로 당기는 세기(규제). */
    const val LAMBDA = 0.1

    /** 상황 보정을 더한 가중치. */
    fun effective(base: Weights, p: DriverProfile, c: Ctx) = Weights(
        t10 = base.t10,
        l = base.l + when (c.people) {
            People.SOLO -> 0.0
            People.ADULT -> p.adultL
            People.CHILD -> p.childL
        },
        f = base.f + (if (c.rain) p.rainF else 0.0) + (if (c.night) p.nightF else 0.0),
        z = if (c.people == People.CHILD) p.childZ else 0.0,
    )

    fun features(r: Route) = doubleArrayOf(r.minutes / 10.0, r.turns.toDouble(), r.fam, r.zones.toDouble())

    /** 효용 U(r) = w_t·시간 + w_l·회전 + w_f·익숙함 + w_z·보호구역 */
    fun utility(w: Weights, r: Route): Double {
        val x = features(r)
        return (0..3).sumOf { w[it] * x[it] }
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

    /** 회전 1번을 피하려고 더 쓰는 분 = w_l ÷ w_t */
    fun leftMinutes(w: Weights): Double = if (w.t10 == 0.0) 0.0 else w.l / w.t10 * 10.0

    /** 다 익숙한 길에 더 쓰는 분 = w_f ÷ |w_t| */
    fun famMinutes(w: Weights): Double = if (w.t10 == 0.0) 0.0 else w.f / abs(w.t10) * 10.0

    /**
     * 고른 경로로 기본 가중치(시간·회전·익숙함)를 고침.
     * w ← w + η(x(r*) − Σ P(r)·x(r)) − λ(w − w₀)
     * 확률은 그때 상황을 반영한 가중치로 계산함. sign이 -1이면 그 길에서 멀어지게 고침(별로였어요).
     */
    fun learn(
        base: Weights, start: Weights, eff: Weights, chosen: Route,
        eta: Double = ETA, sign: Double = 1.0, routes: List<Route> = DemoTrip.routes,
    ): Weights {
        val p = score(eff, routes).probs
        val expected = DoubleArray(4)
        routes.forEach { r -> features(r).forEachIndexed { k, v -> expected[k] += (p[r.id] ?: 0.0) * v } }
        val x = features(chosen)
        val next = DoubleArray(3) { k -> base[k] + sign * eta * (x[k] - expected[k]) - LAMBDA * (base[k] - start[k]) }
        // 시간은 늘 비용이고, 회전은 부담, 익숙함은 좋은 쪽으로만 둠(문장이 뒤집히지 않게)
        return Weights(t10 = next[0].coerceAtMost(-1.0), l = next[1].coerceAtMost(0.0), f = next[2].coerceAtLeast(0.0))
    }

    /** 추천 까닭 한 줄. 가장 빠른 길과 비교하고, 상황 때문에 추천이 바뀌었으면 그 상황을 앞에 씀. */
    fun reason(best: Route, p: DriverProfile, base: Weights, c: Ctx, routes: List<Route> = DemoTrip.routes): String {
        val fastest = routes.minByOrNull { it.minutes } ?: return ""
        if (best.id == fastest.id) return "가장 빨리 가요"
        val plus = "+${best.minutes - fastest.minutes}분"
        val plainBest = score(effective(base, p, Ctx(People.SOLO, false, false)), routes).best
        if (plainBest.id != best.id) {
            if (c.people == People.CHILD) {
                return if (best.lefts == 0) "아이가 타서 좌회전 없는 길로 $plus" else "아이가 타서 좌회전 ${fastest.lefts - best.lefts}번 줄이고 $plus"
            }
            if (c.people == People.ADULT && best.turns < plainBest.turns) return "동승자가 있어서 회전을 줄이고 $plus"
            if (c.rain && best.fam > plainBest.fam) return "비가 와서 익숙한 길로 $plus"
            if (c.night && best.fam > plainBest.fam) return "밤이라 익숙한 길로 $plus"
        }
        return when {
            fastest.lefts > best.lefts -> "좌회전 ${fastest.lefts - best.lefts}번 줄이고 $plus"
            fastest.turns > best.turns -> "유턴 없이 $plus"
            else -> plus
        }
    }

    /** 카드의 사실 줄: 회전 수 · 상황에 맞는 길의 성질 · 거리 */
    fun facts(r: Route, c: Ctx): String {
        val parts = mutableListOf(if (r.lefts == 0) "좌회전 없음" else "좌회전 ${r.lefts}번")
        val second = when {
            c.people == People.CHILD -> if (r.zones == 0) "보호구역 없음" else "보호구역 ${r.zones}곳"
            c.rain || c.night -> "익숙한 길 ${pct(r.fam)}%"
            else -> r.roadFact ?: if (r.uturns > 0) "유턴 ${r.uturns}번" else null
        }
        if (second != null) parts += second
        parts += "${fmt1(r.km)}km"
        return parts.joinToString(" · ")
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

    fun onTurn(v: Turn, now: Long = System.currentTimeMillis()) {
        if (v == current) return
        if (current != Turn.OFF) offAt[current] = now
        if (v != Turn.OFF) onAt[v] = now
        current = v
    }

    /** 판정이 나면 그 결과를, 아니면 null. */
    fun onSteer(angle: Float, now: Long = System.currentTimeMillis()): Judgment? {
        if (armed && abs(angle) >= threshold) {
            armed = false
            val left = if (leftIsNegative) angle < 0 else angle > 0
            val dir = if (left) Turn.LEFT else Turn.RIGHT
            return Judgment(dir, leadMs(dir, now))
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
        armed = true
    }
}

data class Judgment(val dir: Turn, val leadMs: Long?) {
    val signaled get() = leadMs != null
    val side get() = if (dir == Turn.LEFT) "왼쪽" else "오른쪽"

    /** 시연 모드 기록 줄: 앞말(굵게)과 뒷말. */
    fun logParts(): Pair<String, String> = if (leadMs != null) {
        "깜빡이 먼저" to "켠 지 ${String.format(Locale.US, "%.1f", leadMs / 1000.0)}초 뒤 조향"
    } else {
        "깜빡이 없이" to "${side}으로 조향"
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
