package com.chacheum.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChacheumApp : Application() {
    override fun onCreate() {
        super.onCreate()
        App.start(this)
    }
}

enum class Screen { WHO, PROFILE, RECOMMEND, DRIVE, REVIEW }

/** 진행 중인 주행 하나. */
class Trip(val route: Route, val driver: DriverProfile, val learned: Learned?, val ctx: Ctx) {
    var started = false
    var arrived = false
    /** 시연 모드의 진행률 (0~1). */
    var progress = 0.0
    var deviated = false
    var cautionShown = false
    var cautionSpoken = false
    /** 순정 내비가 알려 준 남은 시간(초)과 거리(m). */
    var remainSec: Int? = null
    var remainM: Int? = null
    val signalCount0 = App.judge.count
    val signalFirst0 = App.judge.first
    var lastJudgment: Judgment? = null
    var lastJudgmentAt = 0L
}

/** 앱 전체 상태. 값은 메인 스레드에서만 바꿈. */
object App {
    private val main = Handler(Looper.getMainLooper())

    fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    lateinit var store: Store
        private set
    lateinit var speaker: Speaker
        private set
    val log = EventLog()
    val car = CarSignals()
    val navi = Navi()
    val judge = SignalJudge()

    var screen = Screen.WHO
    var driver: DriverProfile? = null
    var trip: Trip? = null
    var lastTrip: Trip? = null
    /** 시연 모드: 주행 진행과 주의 지점 안내를 시간표로 재생함. 차 신호는 그대로 실제 값을 씀. */
    var demoMode = true
    /** 기어 신호 없이 시연할 때 쓰는 출발 표시. */
    var manualDrive = false
    var reviewAnswer: String? = null

    // ---- 화면 갱신 알림 ----
    private val observers = mutableListOf<() -> Unit>()
    private var pending = false
    fun observe(o: () -> Unit) { observers += o }
    fun unobserve(o: () -> Unit) { observers -= o }

    /** 여러 번 불러도 한 프레임에 한 번만 다시 그림. */
    fun changed() = ui {
        if (pending) return@ui
        pending = true
        main.postDelayed({
            pending = false
            observers.toList().forEach { it() }
        }, 60)
    }

    fun start(ctx: Context) {
        store = Store(ctx)
        speaker = Speaker(ctx)
        car.start(ctx)
        navi.start(ctx)
        log.add("앱", "시작 · 시연 모드 켜짐")
    }

    // ---- 상황 ----
    fun ctx() = Ctx(passenger = car.passenger, rain = car.wiperOn, night = car.lightOn)

    val driving get() = car.gear == "D" || manualDrive

    fun weightsOf(p: DriverProfile) = store.weights(p)

    fun effectiveWeights(p: DriverProfile = driver ?: Profiles.me) = Choice.effective(weightsOf(p), p, ctx())

    // ---- 화면 이동 ----
    fun go(s: Screen) {
        screen = s
        changed()
    }

    fun chooseDriver(p: DriverProfile) {
        driver = p
        log.add("Pleos ID", "운전자 → ${p.name}")
        go(Screen.PROFILE)
    }

    /** 경로를 고르면: 모델이 배우고, 순정 내비에 그 경로 옵션으로 안내를 요청함. */
    fun startGuide(route: Route) {
        val p = driver ?: Profiles.me
        val c = ctx()
        val base = weightsOf(p)
        val eff = Choice.effective(base, p, c)
        val learned = if (p.record) {
            val after = Choice.learn(base, p.start, eff, route)
            store.saveWeights(p, after)
            log.add(
                "모델",
                "${route.short}을 골라 배움: 좌회전 1번 ${fmt1(Choice.leftMinutes(base))}분 → ${fmt1(Choice.leftMinutes(after))}분",
            )
            Learned(route, base, after)
        } else {
            null
        }
        trip = Trip(route, p, learned, c)
        reviewAnswer = null
        manualDrive = false
        navi.guide(route)
        go(Screen.DRIVE)
        if (driving) beginDrive()
    }

    fun beginDrive() {
        val t = trip ?: return
        if (t.started) return
        t.started = true
        log.add("주행", "출발 · ${t.route.name}")
        if (demoMode) tick()
        changed()
    }

    fun deviate() {
        val t = trip ?: return
        if (t.deviated || t.arrived) return
        t.deviated = true
        log.add("NaviHelper", "재탐색 (안내 경로를 벗어남)")
        changed()
    }

    fun arrive() {
        val t = trip ?: return
        if (t.arrived) return
        t.arrived = true
        t.progress = 1.0
        log.add("NaviHelper", "목적지 도착")
        speaker.say("도착했어요.")
        changed()
    }

    fun openReview() {
        val t = trip ?: return
        val p = t.driver
        if (p.record) {
            store.addTrip(p)
            store.addSignals(p, judge.count - t.signalCount0, judge.first - t.signalFirst0)
            if (t.cautionShown && !t.deviated) store.addCautionPass(p)
        }
        lastTrip = t
        trip = null
        manualDrive = false
        go(Screen.REVIEW)
    }

    fun answerDeviation(onPurpose: Boolean) {
        val t = lastTrip ?: return
        val p = t.driver
        reviewAnswer = if (onPurpose) {
            if (p.record) store.nudgeFamiliar(p, +0.1)
            log.add("모델", "일부러 다른 길 → 그 길의 익숙함을 더 쳐 줌")
            "알겠어요. 다음에도 그 길을 더 쳐 줄게요."
        } else {
            if (p.record) store.resetCautionPass(p)
            log.add("모델", "놓침 → 나만의 주의 지점으로 기억")
            "그곳을 나만의 주의 지점으로 기억해 둘게요."
        }
        changed()
    }

    /** 시연 모드를 켜고 끔. 켜면 멈춰 있던 진행을 다시 돌림. */
    fun toggleDemo() {
        demoMode = !demoMode
        log.add("앱", if (demoMode) "시연 모드 켬" else "시연 모드 끔 · 순정 내비 이벤트로 진행")
        if (demoMode) trip?.let { if (it.started && !it.arrived) tick() }
        changed()
    }

    fun resetAll() {
        store.clear()
        judge.reset()
        trip = null
        lastTrip = null
        driver = null
        manualDrive = false
        log.add("앱", "처음 상태로 되돌림")
        go(Screen.WHO)
    }

    // ---- 차 신호가 바뀔 때 ----
    fun onGear(g: String) {
        log.add("Vehicle", "기어 $g")
        val t = trip
        if (g == "D") {
            if (t == null && screen == Screen.RECOMMEND) {
                // 고르지 않고 출발하면 추천한 길로 감
                startGuide(Choice.score(effectiveWeights()).best)
                return
            }
            beginDrive()
        } else if (g == "P" && t != null && t.arrived) {
            openReview()
            return
        }
        changed()
    }

    fun onJudgment(j: Judgment) {
        log.add("판정", j.sentence())
        trip?.let {
            it.lastJudgment = j
            it.lastJudgmentAt = System.currentTimeMillis()
        }
        changed()
    }

    // ---- 시연 모드 진행 ----
    private const val DEMO_SECONDS = 75.0
    private const val TICK_MS = 250L

    private fun tick() {
        val t = trip ?: return
        if (!demoMode || !t.started || t.arrived) return
        if (driving) {
            t.progress = (t.progress + TICK_MS / 1000.0 / DEMO_SECONDS).coerceAtMost(1.0)
            val near = t.progress >= DemoTrip.CAUTION_AT - DemoTrip.CAUTION_LEAD && t.progress < DemoTrip.CAUTION_AT + 0.03
            val p = t.driver
            if (near && !t.cautionShown && p.record && store.cautionActive(p)) {
                t.cautionShown = true
                log.add("주의 지점", "${DemoTrip.CAUTION_SPOT} 300 m 앞")
            }
            if (t.cautionShown && !t.cautionSpoken) {
                t.cautionSpoken = true
                speaker.say(DemoTrip.CAUTION_TEXT)
            }
            if (t.progress >= 1.0) {
                arrive()
                return
            }
            changed()
        }
        main.postDelayed({ tick() }, TICK_MS)
    }

    /** 순정 내비가 보낸 남은 시간·거리 (시연 모드가 꺼져 있을 때 화면에 씀). */
    fun onRemain(sec: Int, meters: Int) {
        val t = trip ?: return
        t.remainSec = sec
        t.remainM = meters
        changed()
    }
}

/** 화면 아래에 보이는 기록. 어떤 신호와 SDK 호출이 오갔는지 보여 줌. */
class EventLog {
    data class Entry(val time: String, val src: String, val text: String)

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val entries = ArrayDeque<Entry>()

    fun add(src: String, text: String) {
        Log.d("Chacheum", "[$src] $text")
        App.ui {
            entries.addFirst(Entry(fmt.format(Date()), src, text))
            while (entries.size > 60) entries.removeLast()
            App.changed()
        }
    }
}

/** 운전자별 가중치와 기록. 차 안(이 기기)에만 저장함. */
class Store(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("chacheum", Context.MODE_PRIVATE)

    fun weights(p: DriverProfile): Weights = Weights(
        t = d("${p.id}.t", p.start.t),
        l = d("${p.id}.l", p.start.l),
        f = d("${p.id}.f", p.start.f),
    )

    fun saveWeights(p: DriverProfile, w: Weights) {
        prefs.edit().putString("${p.id}.t", w.t.toString())
            .putString("${p.id}.l", w.l.toString())
            .putString("${p.id}.f", w.f.toString())
            .apply()
    }

    fun nudgeFamiliar(p: DriverProfile, by: Double) {
        val w = weights(p)
        saveWeights(p, w.copy(f = w.f + by))
    }

    fun trips(p: DriverProfile) = prefs.getInt("${p.id}.trips", if (p.id == "me") 12 else if (p.id == "parent") 40 else 0)
    fun addTrip(p: DriverProfile) = prefs.edit().putInt("${p.id}.trips", trips(p) + 1).apply()

    fun signals(p: DriverProfile) = prefs.getInt("${p.id}.sigN", 0) to prefs.getInt("${p.id}.sigFirst", 0)
    fun addSignals(p: DriverProfile, n: Int, first: Int) {
        if (n <= 0) return
        val (n0, f0) = signals(p)
        prefs.edit().putInt("${p.id}.sigN", n0 + n).putInt("${p.id}.sigFirst", f0 + first).apply()
    }

    /** 주의 지점을 무사히 지난 횟수. 3번 연속이면 알림을 거둠. */
    fun cautionPasses(p: DriverProfile) = prefs.getInt("${p.id}.cautionPass", 0)
    fun cautionActive(p: DriverProfile) = p.id == "me" && cautionPasses(p) < 3
    fun addCautionPass(p: DriverProfile) = prefs.edit().putInt("${p.id}.cautionPass", cautionPasses(p) + 1).apply()
    fun resetCautionPass(p: DriverProfile) = prefs.edit().putInt("${p.id}.cautionPass", 0).apply()

    fun clear() = prefs.edit().clear().apply()

    private fun d(key: String, def: Double) = prefs.getString(key, null)?.toDoubleOrNull() ?: def
}
