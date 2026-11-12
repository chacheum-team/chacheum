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

/** 화면 일곱 장(디자인 시스템 "화면과 동작")과 처음 질문 3개. */
enum class Screen { WHO, ONBOARD, HOME, ROUTES, DRIVE, REFLECT, PROFILE }

/** 진행 중인 주행 하나. */
class Trip(val route: Route, val driver: DriverProfile, val learned: Learned?, val ctx: Ctx) {
    val requestedAt = System.currentTimeMillis()
    var started = false
    var startedAt = 0L
    var arrived = false
    var arrivedAt = 0L
    /** 시연 모드의 진행률 (0~1). */
    var progress = 0.0
    var deviated = false
    /** 지점별 상태: 0 아직, 1 알리는 중, 2 지남. */
    val spotState = HashMap<String, Int>()
    var spotNow: Spot? = null
    /** 순정 내비가 알려 준 남은 시간(초)과 거리(m). */
    var remainSec: Int? = null
    var remainM: Int? = null
    val judgments = mutableListOf<Judgment>()
    var lastJudgment: Judgment? = null
}

data class Override(val people: People? = null, val rain: Boolean? = null, val night: Boolean? = null)

/** 토스트 한 장. 동작 버튼이 있으면 6초, 없으면 4초. */
data class ToastMsg(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/** 앱 전체 상태. 값은 메인 스레드에서만 바꿈. */
object App {
    private val main = Handler(Looper.getMainLooper())

    fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    lateinit var store: Store
        private set
    var speaker: Speaker? = null
        private set
    val log = EventLog()
    val car = CarSignals()
    val navi = Navi()
    val judge = SignalJudge()

    var screen = Screen.WHO
        private set
    private val backStack = ArrayDeque<Screen>()
    var driver: DriverProfile? = null
        private set
    var trip: Trip? = null
        private set
    var lastTrip: Trip? = null
        private set
    /** 시연 모드: 주행 진행과 주의 지점 알림을 시간표로 재생함. 차 신호는 그대로 실제 값을 씀. */
    var demoMode = true
        private set
    /** 기어 신호 없이 시연할 때 쓰는 출발 표시. */
    var manualDrive = false
        private set
    /** 경로 고르기에서 바로잡은 상황(이번 주행에만). */
    var override = Override()
        private set
    /** 경로 고르기에서 사용자가 직접 고른 후보. null이면 추천을 따름. */
    var picked: String? = null
        private set
    var deviationAnswer: Boolean? = null
        private set
    var feelAnswer: Boolean? = null
        private set
    /** 나의 운전 화면에서 보는 운전자와, 고치는 중인 길 취향 값(분). */
    var profileOf: String? = null
        private set
    var editing: Double? = null
        private set
    /** 처음 질문 3개: 몇 번째 질문인지와 답. */
    var onboardStep = 0
        private set
    private val onboardAnswers = mutableListOf<Boolean>()
    private var onboardFor: String = "new"

    var toaster: ((ToastMsg) -> Unit)? = null

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
        }, 40)
    }

    fun toast(msg: ToastMsg) = ui { toaster?.invoke(msg) }

    /** 차 신호·내비 없이 화면만 쓸 때(미리보기)도 부르는 준비. */
    fun initLocal(ctx: Context) {
        if (!::store.isInitialized) store = Store(ctx)
        if (speaker == null) speaker = Speaker(ctx)
    }

    fun start(ctx: Context) {
        initLocal(ctx)
        car.start(ctx)
        navi.start(ctx)
        log.add("앱", "시작 · 시연 모드 켜짐")
    }

    // ---- 상황 ----
    fun carCtx() = Ctx(
        people = when {
            car.rearOccupied -> People.CHILD
            car.frontOccupied -> People.ADULT
            else -> People.SOLO
        },
        rain = car.wiperOn,
        night = car.lightOn,
    )

    fun ctx(): Ctx {
        val c = carCtx()
        return Ctx(override.people ?: c.people, override.rain ?: c.rain, override.night ?: c.night)
    }

    val driving get() = car.gear == "D" || manualDrive || car.restricted

    fun profile(id: String?): DriverProfile = when (id) {
        Profiles.jisu.id -> Profiles.jisu
        Profiles.guest.id -> Profiles.guest
        "new" -> store.newDriver() ?: Profiles.me
        else -> Profiles.me
    }

    fun weightsOf(p: DriverProfile) = store.weights(p)
    fun effectiveWeights(p: DriverProfile = driver ?: Profiles.me, c: Ctx = ctx()) = Choice.effective(weightsOf(p), p, c)

    // ---- 화면 이동 ----
    private fun go(s: Screen, push: Boolean = true) {
        if (push && s != screen) backStack.addLast(screen)
        screen = s
        editing = null
        changed()
    }

    /** 시스템 뒤로 가기: 한 단계씩 닫음. false면 앱을 나감(주행 중 화면은 뒤로 보냄). */
    fun back(): Boolean {
        when (screen) {
            Screen.DRIVE -> return false
            Screen.ONBOARD -> if (onboardStep > 0) {
                onboardStep--
                onboardAnswers.removeLastOrNull()
                changed()
                return true
            }
            Screen.PROFILE -> if (editing != null) {
                editing = null
                changed()
                return true
            }
            Screen.REFLECT -> {
                goHome()
                return true
            }
            else -> {}
        }
        val prev = backStack.removeLastOrNull() ?: return false
        go(prev, push = false)
        return true
    }

    /** 닫기(X): 이 화면을 닫고 앞 화면으로. */
    fun leave() {
        val prev = backStack.removeLastOrNull()
        if (prev != null) go(prev, push = false) else goHome()
    }

    fun goHome() {
        backStack.clear()
        go(if (driver == null) Screen.WHO else Screen.HOME, push = false)
    }

    /** 출발 전에 안내를 거둠. */
    fun goHomeCancel() {
        val t = trip
        if (t != null && !t.started) {
            trip = null
            navi.cancel()
            log.add("주행", "출발 전에 안내를 거둠")
        }
        goHome()
    }

    // ---- 누가 운전해요? ----
    private var askedWho = false
    private var whoTimer: Runnable? = null

    /** 시동 직후 한 번 묻고, 5초 안에 답이 없으면 지난번 운전자로 둠. */
    fun onWhoShown() {
        if (askedWho) return
        askedWho = true
        speaker?.say("누가 운전해요?")
        val r = Runnable {
            whoTimer = null
            if (screen == Screen.WHO && driver == null) {
                chooseDriver(profile(store.lastDriver()), auto = true)
            }
        }
        whoTimer = r
        main.postDelayed(r, 5_000)
    }

    /** 화면을 만지면 기다리기를 멈춤. */
    fun userTouched() {
        whoTimer?.let { main.removeCallbacks(it) }
        whoTimer = null
    }

    fun openWho() {
        userTouched()
        go(Screen.WHO)
    }

    fun chooseDriver(p: DriverProfile, auto: Boolean = false) {
        userTouched()
        driver = p
        if (p.record) store.setLastDriver(p)
        log.add("운전자", "${p.name}${if (auto) " (5초 동안 답이 없어 지난번 운전자로)" else ""}")
        backStack.clear()
        go(Screen.HOME, push = false)
        if (auto) toast(ToastMsg("지난번처럼 ${p.name}로 시작해요"))
    }

    fun startOnboard(forId: String = "new") {
        userTouched()
        onboardFor = forId
        onboardStep = 0
        onboardAnswers.clear()
        go(Screen.ONBOARD)
    }

    fun answerOnboard(first: Boolean) {
        onboardAnswers += first
        if (onboardStep < 2) {
            onboardStep++
            changed()
            return
        }
        val road = if (onboardAnswers[2]) RoadPref.HIGHWAY else RoadPref.NATIONAL
        val p: DriverProfile
        if (onboardFor == "new") {
            store.saveNewDriver(onboardAnswers[0], onboardAnswers[1], road)
            p = profile("new")
        } else {
            p = profile(onboardFor)
            val q = Profiles.newDriver(onboardAnswers[0], onboardAnswers[1], road)
            store.saveWeights(p, Weights(q.start.t10, q.start.l, weightsOf(p).f))
            store.setRoad(p, road)
        }
        log.add("모델", "질문 3개로 시작값: 좌회전 1번 ${fmt1(Choice.leftMinutes(store.weights(p)))}분")
        if (onboardFor == "new") {
            chooseDriver(p)
            toast(ToastMsg("시작값을 잡았어요. 운전할수록 맞춰 갈게요"))
        } else {
            profileOf = p.id
            backStack.clear()
            backStack.addLast(Screen.HOME)
            go(Screen.PROFILE, push = false)
            toast(ToastMsg("길 취향을 다시 잡았어요"))
        }
    }

    // ---- 첫 화면·경로 고르기 ----
    fun openRoutes() {
        override = Override()
        picked = null
        go(Screen.ROUTES)
        log.add("목적지", "${DemoTrip.DEST_NAME} · 후보 경로 3개")
    }

    fun pick(r: Route) {
        picked = r.id
        changed()
    }

    /** 상황 칩을 눌러 바로잡음(이번 주행에만). 사람 칩은 혼자 → 동승자 → 아이 탐 순서로 바뀜. */
    fun togglePeople() {
        val now = ctx().people
        val next = People.entries[(now.ordinal + 1) % People.entries.size]
        override = override.copy(people = next)
        log.add("상황", "바로잡음: ${peopleName(next)}")
        changed()
    }

    fun toggleRain() {
        val v = !ctx().rain
        override = override.copy(rain = v)
        log.add("상황", "바로잡음: 비 ${if (v) "켬" else "끔"}")
        changed()
    }

    fun toggleNight() {
        val v = !ctx().night
        override = override.copy(night = v)
        log.add("상황", "바로잡음: 밤 ${if (v) "켬" else "끔"}")
        changed()
    }

    fun setPeopleOverride(p: People?) {
        override = override.copy(people = p)
        changed()
    }

    fun peopleName(p: People) = when (p) {
        People.SOLO -> "혼자"
        People.ADULT -> "동승자"
        People.CHILD -> "아이 탐"
    }

    /** 경로를 고르면: 모델이 배우고, 순정 내비에 그 길을 넣음. */
    fun startGuide(route: Route) {
        val p = driver ?: Profiles.me
        val c = ctx()
        val base = weightsOf(p)
        val eff = Choice.effective(base, p, c)
        val learned = if (p.record) {
            val after = Choice.learn(base, p.start, eff, route)
            store.saveWeights(p, after)
            store.addChoice(p)
            log.add("모델", "${route.noun}을 골라 배움: 좌회전 1번 ${fmt1(Choice.leftMinutes(base))}분 → ${fmt1(Choice.leftMinutes(after))}분")
            Learned(route, base, after)
        } else {
            null
        }
        trip = Trip(route, p, learned, c)
        deviationAnswer = null
        feelAnswer = null
        manualDrive = false
        val sent = navi.guide(route)
        backStack.clear()
        go(Screen.DRIVE, push = false)
        toast(
            when {
                !sent -> ToastMsg("경유지를 넣지 못했어요", "다시") { navi.guide(route) }
                route.via != null -> ToastMsg("${route.noun}을 경유지로 넣었어요")
                else -> ToastMsg("${route.noun}로 안내를 시작했어요")
            },
        )
        if (driving) beginDrive()
    }

    fun beginDrive() {
        val t = trip ?: return
        if (t.started) return
        t.started = true
        t.startedAt = System.currentTimeMillis()
        log.add("주행", "출발 · ${t.route.name}")
        if (demoMode) tick()
        changed()
    }

    fun startWithoutGear() {
        manualDrive = true
        log.add("시연", "기어 없이 출발")
        beginDrive()
    }

    fun deviate() {
        val t = trip ?: return
        if (t.deviated || t.arrived) return
        t.deviated = true
        log.add("NaviHelper", "재탐색 · 안내 경로를 벗어남 (${DemoTrip.DEVIATE_ROAD})")
        changed()
    }

    fun arrive() {
        val t = trip ?: return
        if (t.arrived) return
        t.arrived = true
        t.arrivedAt = System.currentTimeMillis()
        t.progress = 1.0
        t.spotNow = null
        log.add("NaviHelper", "목적지 도착")
        speaker?.say("도착했어요.")
        // 기어 신호 없이 시연할 때는 잠시 뒤 주차한 것으로 봄
        if (manualDrive) main.postDelayed({ if (trip === t) openReflect() }, 2_500)
        changed()
    }

    fun openReflect() {
        val t = trip ?: return
        val p = t.driver
        if (p.record) {
            store.addTrip(p)
            store.addSignals(p, t.judgments.map { it.signaled })
            t.spotState.forEach { (id, st) -> if (st == 2) store.addSpotPass(p, id) }
        }
        lastTrip = t
        trip = null
        manualDrive = false
        override = Override()
        backStack.clear()
        go(Screen.REFLECT, push = false)
    }

    fun answerDeviation(onPurpose: Boolean) {
        val t = lastTrip ?: return
        val p = t.driver
        deviationAnswer = onPurpose
        if (onPurpose) {
            if (p.record) store.nudgeFamiliar(p, +0.1)
            log.add("모델", "일부러 다른 길 → 길 취향에 넣음 (익숙함 +0.1)")
        } else {
            if (p.record) store.addSpot(p, DemoTrip.missedSpot.id)
            log.add("주의 지점", "놓친 곳으로 기억: ${DemoTrip.missedSpot.name}")
        }
        changed()
    }

    fun answerFeel(good: Boolean) {
        val t = lastTrip ?: return
        val p = t.driver
        feelAnswer = good
        if (p.record) {
            val base = weightsOf(p)
            val after = Choice.learn(base, p.start, Choice.effective(base, p, t.ctx), t.route, eta = Choice.ETA / 2, sign = if (good) 1.0 else -1.0)
            store.saveWeights(p, after)
            log.add("모델", "주행 후 한마디(${if (good) "편했어요" else "별로였어요"}) → 좌회전 1번 ${fmt1(Choice.leftMinutes(after))}분")
        }
        changed()
    }

    // ---- 나의 운전 ----
    fun openProfile(id: String? = null) {
        profileOf = id ?: driver?.takeIf { it.record }?.id ?: Profiles.me.id
        go(Screen.PROFILE)
    }

    fun showProfileOf(id: String) {
        profileOf = id
        editing = null
        changed()
    }

    fun startEdit(p: DriverProfile) {
        editing = (Choice.leftMinutes(weightsOf(p)) * 2).let { kotlin.math.round(it) / 2.0 }.coerceIn(0.0, 10.0)
        changed()
    }

    fun stepEdit(by: Double) {
        val v = editing ?: return
        editing = (v + by).coerceIn(0.0, 10.0)
        changed()
    }

    fun saveEdit(p: DriverProfile) {
        val v = editing ?: return
        val w = weightsOf(p)
        store.saveWeights(p, w.copy(l = -v * kotlin.math.abs(w.t10) / 10.0))
        editing = null
        log.add("길 취향", "${p.name}: 좌회전 1번 피하기 ${fmt1(v)}분으로 고침")
        toast(ToastMsg("다음 추천부터 ${fmt1(v)}분으로 볼게요"))
        changed()
    }

    fun clearRecord(p: DriverProfile) {
        store.clearDriver(p)
        log.add("기록", "${p.name}의 기록을 지움")
        toast(ToastMsg("${p.name}의 기록을 지웠어요"))
        changed()
    }

    // ---- 시연 도구 ----
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
        override = Override()
        picked = null
        askedWho = false
        backStack.clear()
        log.add("앱", "처음 상태로 되돌림")
        go(Screen.WHO, push = false)
    }

    // ---- 차 신호가 바뀔 때 ----
    fun onGear(g: String) {
        log.add("Vehicle", "기어 $g")
        val t = trip
        if (g == "D") {
            beginDrive()
        } else if (g == "P" && t != null && t.arrived) {
            openReflect()
            return
        }
        changed()
    }

    fun onRestricted() {
        trip?.let { if (car.restricted) beginDrive() }
        changed()
    }

    fun onJudgment(j: Judgment) {
        val (a, b) = j.logParts()
        log.add("판정", "$a · $b")
        trip?.let {
            if (it.started && !it.arrived) it.judgments += j
            it.lastJudgment = j
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
            updateSpots(t)
            if (t.progress >= DemoTrip.DEVIATE_AT) deviate()
            if (t.progress >= 1.0) {
                arrive()
                return
            }
            changed()
        }
        main.postDelayed({ tick() }, TICK_MS)
    }

    private fun updateSpots(t: Trip) {
        val p = t.driver
        if (!p.record) return
        for (s in DemoTrip.spots) {
            if (!store.spotIds(p).contains(s.id)) continue
            val st = t.spotState[s.id] ?: 0
            if (st == 0 && t.progress >= s.pos - DemoTrip.LEAD && t.progress < s.pos) {
                if (store.spotPasses(p, s.id) >= 3) continue
                t.spotState[s.id] = 1
                t.spotNow = s
                log.add("주의 지점", "${s.name} 300m 앞")
                speaker?.say("${s.say}. ${s.say2}.")
            } else if (st == 1 && t.progress >= s.pos + 0.02) {
                t.spotState[s.id] = 2
                if (t.spotNow?.id == s.id) t.spotNow = null
                log.add("주의 지점", "${s.name} 지남")
            }
        }
    }

    /** 이번 주행에 남은 주의 지점 수(덧붙일 안내). */
    fun spotsLeft(t: Trip): Int {
        val p = t.driver
        if (!p.record) return 0
        return DemoTrip.spots.count { s ->
            store.spotIds(p).contains(s.id) && store.spotPasses(p, s.id) < 3 && (t.spotState[s.id] ?: 0) == 0
        }
    }

    fun nextSpot(t: Trip): Spot? {
        val p = t.driver
        if (!p.record) return null
        return DemoTrip.spots.firstOrNull { s ->
            store.spotIds(p).contains(s.id) && store.spotPasses(p, s.id) < 3 && (t.spotState[s.id] ?: 0) == 0
        }
    }

    /** 순정 내비가 보낸 남은 시간·거리 (시연 모드가 꺼져 있을 때 화면에 씀). */
    fun onRemain(sec: Int, meters: Int) {
        val t = trip ?: return
        t.remainSec = sec
        t.remainM = meters
        changed()
    }

    fun onNaviError(text: String) {
        val t = trip ?: return
        if (t.started) return
        toast(ToastMsg("경유지를 넣지 못했어요", "다시") { navi.guide(t.route) })
        log.add("NaviHelper", text)
    }
}

/** 기록. 어떤 신호와 SDK 호출이 오갔는지 남김(시연 도구에서 봄). */
class EventLog {
    data class Entry(val time: String, val src: String, val text: String)

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val entries = ArrayDeque<Entry>()

    fun add(src: String, text: String) {
        Log.d("Chacheum", "[$src] $text")
        App.ui {
            entries.addFirst(Entry(fmt.format(Date()), src, text))
            while (entries.size > 80) entries.removeLast()
        }
    }
}

/** 운전자별 가중치와 기록. 차 안(이 기기)에만 저장함. */
class Store(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("chacheum", Context.MODE_PRIVATE)

    fun weights(p: DriverProfile): Weights = Weights(
        t10 = d("${p.id}.t10", p.start.t10),
        l = d("${p.id}.l", p.start.l),
        f = d("${p.id}.f", p.start.f),
    )

    fun saveWeights(p: DriverProfile, w: Weights) {
        prefs.edit().putString("${p.id}.t10", w.t10.toString())
            .putString("${p.id}.l", w.l.toString())
            .putString("${p.id}.f", w.f.toString())
            .apply()
    }

    fun nudgeFamiliar(p: DriverProfile, by: Double) {
        val w = weights(p)
        saveWeights(p, w.copy(f = (w.f + by).coerceAtLeast(0.0)))
    }

    fun trips(p: DriverProfile) = prefs.getInt("${p.id}.trips", p.seedTrips)
    fun addTrip(p: DriverProfile) = prefs.edit().putInt("${p.id}.trips", trips(p) + 1)
        .putLong("${p.id}.lastAt", System.currentTimeMillis()).apply()

    fun choices(p: DriverProfile) = prefs.getInt("${p.id}.choices", p.seedChoices)
    fun addChoice(p: DriverProfile) = prefs.edit().putInt("${p.id}.choices", choices(p) + 1).apply()

    fun rainTrips(p: DriverProfile) = p.seedRainTrips

    /** 깜빡이 판정 기록(1 먼저 켬, 0 없이). 최근 20개만 둠. */
    fun signals(p: DriverProfile): String = prefs.getString("${p.id}.sig", p.seedSignals) ?: ""
    fun addSignals(p: DriverProfile, results: List<Boolean>) {
        if (results.isEmpty()) return
        val s = (signals(p) + results.joinToString("") { if (it) "1" else "0" }).takeLast(20)
        prefs.edit().putString("${p.id}.sig", s).apply()
    }

    /** 최근 12번 중 몇 번 먼저 켰는지. */
    fun recentSignals(p: DriverProfile): Pair<Int, Int> {
        val s = signals(p).takeLast(12)
        return s.length to s.count { it == '1' }
    }

    /** 이 운전자의 나만의 주의 지점 목록. */
    fun spotIds(p: DriverProfile): List<String> {
        val def = if (p.id == Profiles.me.id) "seongsu,ttukseom" else ""
        return (prefs.getString("${p.id}.spots", def) ?: def).split(',').filter { it.isNotBlank() }
    }

    fun addSpot(p: DriverProfile, id: String) {
        val ids = spotIds(p)
        if (id in ids) return
        prefs.edit().putString("${p.id}.spots", (ids + id).joinToString(",")).putInt("${p.id}.spot.$id", 0).apply()
    }

    /** 그 지점을 무사히 지난 횟수. 3번이면 알림을 거둠(졸업). */
    fun spotPasses(p: DriverProfile, id: String): Int {
        val def = if (p.id == Profiles.me.id && id == "seongsu") 1 else 0
        return prefs.getInt("${p.id}.spot.$id", def)
    }

    fun addSpotPass(p: DriverProfile, id: String) =
        prefs.edit().putInt("${p.id}.spot.$id", (spotPasses(p, id) + 1).coerceAtMost(3)).apply()

    fun road(p: DriverProfile): RoadPref =
        prefs.getString("${p.id}.road", null)?.let { runCatching { RoadPref.valueOf(it) }.getOrNull() } ?: p.road

    fun setRoad(p: DriverProfile, r: RoadPref) = prefs.edit().putString("${p.id}.road", r.name).apply()

    fun lastDriver(): String = prefs.getString("lastDriver", Profiles.me.id) ?: Profiles.me.id
    fun setLastDriver(p: DriverProfile) = prefs.edit().putString("lastDriver", p.id).apply()

    /** 마지막으로 운전한 때. 시연용으로 지수는 3일 전에 운전한 것으로 둠. */
    fun lastAt(p: DriverProfile): Long? {
        val v = prefs.getLong("${p.id}.lastAt", 0L)
        if (v > 0) return v
        return when (p.id) {
            Profiles.me.id -> System.currentTimeMillis() - 26 * 3_600_000L
            Profiles.jisu.id -> System.currentTimeMillis() - 3 * 86_400_000L
            else -> null
        }
    }

    fun saveNewDriver(fast: Boolean, turnsHard: Boolean, road: RoadPref) {
        prefs.edit().putBoolean("new.exists", true).putBoolean("new.fast", fast).putBoolean("new.turns", turnsHard)
            .putString("new.road", road.name)
            .remove("new.t10").remove("new.l").remove("new.f").remove("new.trips").remove("new.sig")
            .remove("new.choices").remove("new.spots").apply()
    }

    fun newDriver(): DriverProfile? {
        if (!prefs.getBoolean("new.exists", false)) return null
        val road = runCatching { RoadPref.valueOf(prefs.getString("new.road", "NATIONAL")!!) }.getOrDefault(RoadPref.NATIONAL)
        return Profiles.newDriver(prefs.getBoolean("new.fast", true), prefs.getBoolean("new.turns", false), road)
    }

    fun clearDriver(p: DriverProfile) {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("${p.id}.") }.forEach { e.remove(it) }
        // 기록을 지우면 시연용 지난 기록도 쓰지 않음
        e.putInt("${p.id}.trips", 0).putString("${p.id}.sig", "").putInt("${p.id}.choices", 0).putString("${p.id}.spots", "")
        e.putBoolean("${p.id}.cleared", true)
        e.apply()
    }

    fun cleared(p: DriverProfile) = prefs.getBoolean("${p.id}.cleared", false)

    fun clear() = prefs.edit().clear().apply()

    private fun d(key: String, def: Double) = prefs.getString(key, null)?.toDoubleOrNull() ?: def
}
