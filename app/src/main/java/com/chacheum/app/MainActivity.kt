package com.chacheum.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.math.ceil

/**
 * 화면 하나에 상태별 내용을 바꿔 그림.
 * 정차 중: 운전자 고르기 → 프로필 → 길 고르기 / 주행 중: 카드 한 장 / 정차 후: 돌아보기
 */
class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var chips: LinearLayout
    private lateinit var headerRight: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var logBox: LinearLayout
    private var logOpen = false
    private val render: () -> Unit = { draw() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        window.decorView.setBackgroundColor(C.BG)

        val root = ui.vbox().apply { setPadding(ui.dp(28), ui.dp(20), ui.dp(28), ui.dp(16)) }

        // 머리: 앱 이름 + 운전자·시연 모드
        val header = ui.hbox()
        val title = ui.text("차츰", T.TITLE, C.TEXT, bold = true).apply {
            // 길게 누르면 처음 상태로 (시연을 다시 찍을 때)
            setOnLongClickListener {
                App.resetAll()
                Toast.makeText(this@MainActivity, "처음 상태로 되돌렸어요", Toast.LENGTH_SHORT).show()
                true
            }
        }
        header.addView(title, ui.weight())
        headerRight = ui.hbox()
        header.addView(headerRight)
        root.addView(header, ui.full())

        // 차 신호 칩
        chips = ui.hbox()
        root.addView(chips, ui.fullWithTop(12))

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = ui.dp(16)
        })

        logBox = ui.vbox()
        root.addView(logBox, ui.fullWithTop(12))

        setContentView(root)
        App.observe(render)
        draw()
    }

    override fun onResume() {
        super.onResume()
        App.changed()
    }

    override fun onDestroy() {
        App.unobserve(render)
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // 그리기
    // -----------------------------------------------------------------------

    private fun draw() {
        drawHeader()
        drawChips()
        content.removeAllViews()
        val drivingNow = App.driving || App.car.restricted
        val view = when {
            App.screen == Screen.DRIVE -> driveView()
            drivingNow -> blockedView()
            else -> when (App.screen) {
                Screen.WHO -> whoView()
                Screen.PROFILE -> profileView()
                Screen.RECOMMEND -> recommendView()
                Screen.REVIEW -> reviewView()
                Screen.DRIVE -> driveView()
            }
        }
        val scrollable = App.screen != Screen.DRIVE && !drivingNow
        if (scrollable) {
            val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
            sv.addView(view)
            content.addView(sv)
        } else {
            content.addView(view)
        }
        drawLog()
    }

    private fun drawHeader() {
        headerRight.removeAllViews()
        App.driver?.let { d ->
            headerRight.addView(ui.chip("운전자 ${d.name}", true))
        }
        headerRight.addView(
            ui.chip(if (App.demoMode) "시연 모드" else "실제 내비", App.demoMode) { App.toggleDemo() },
            ui.wrapStart(10),
        )
    }

    private fun drawChips() {
        chips.removeAllViews()
        val car = App.car
        val gear = if (App.manualDrive && car.gear != "D") "D(시연)" else car.gear
        chips.addView(ui.chip("기어 $gear", App.driving))
        chips.addView(ui.chip("동승자", car.passenger), ui.wrapStart(10))
        chips.addView(ui.chip("비", car.wiperOn), ui.wrapStart(10))
        chips.addView(ui.chip("밤", car.lightOn), ui.wrapStart(10))
        val turn = when (car.turn) {
            Turn.LEFT -> "◀ 깜빡이"
            Turn.RIGHT -> "깜빡이 ▶"
            Turn.OFF -> "깜빡이"
        }
        chips.addView(ui.chip(turn, car.turn != Turn.OFF), ui.wrapStart(10))
        if (car.restricted) chips.addView(ui.chip("주행 규제", true), ui.wrapStart(10))
    }

    private fun drawLog() {
        logBox.removeAllViews()
        val head = ui.hbox()
        head.addView(ui.text("기록", T.SMALL, C.TEXT3, bold = true), ui.weight())
        head.addView(ui.text(if (logOpen) "접기" else "펼치기", T.SMALL, C.ACCENT).apply {
            setPadding(ui.dp(16), ui.dp(6), 0, ui.dp(6))
            setOnClickListener { logOpen = !logOpen; draw() }
        })
        logBox.addView(head, ui.full())
        val n = if (logOpen) 14 else 3
        App.log.entries.take(n).forEach { e ->
            logBox.addView(
                ui.text("${e.time}  ${e.src} · ${e.text}", 15f, if (e.src == "판정" || e.src == "모델") C.TEXT2 else C.TEXT3).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
            )
        }
    }

    // -----------------------------------------------------------------------
    // 정차 중 화면
    // -----------------------------------------------------------------------

    private fun whoView(): View = ui.vbox().apply {
        addView(ui.text("누가 운전해요?", T.TITLE, bold = true))
        addView(ui.text("운전자마다 길 취향을 따로 배워요.", T.BODY, C.TEXT2), ui.fullWithTop(6))
        Profiles.all.forEach { p ->
            val row = ui.card().apply {
                addView(ui.text(p.name, T.H2, bold = true))
                addView(ui.text(p.sub, T.LABEL, C.TEXT2))
                isClickable = true
                setOnClickListener { App.chooseDriver(p) }
            }
            addView(row, ui.fullWithTop(16))
        }
        addView(
            ui.text("실차에서는 차량 사용자 프로필이나 음성(\"나야\")으로 골라요.", T.SMALL, C.TEXT3),
            ui.fullWithTop(16),
        )
    }

    private fun profileView(): View = ui.vbox().apply {
        val p = App.driver ?: Profiles.me
        val store = App.store
        val w = App.weightsOf(p)
        addView(ui.text(if (p.record) "${p.name}의 운전 프로필" else "게스트로 운전해요", T.TITLE, bold = true))
        val sub = if (p.record) "${p.sub} · 지금까지 ${store.trips(p)}번 운전" else "이번 주행은 기록하지 않아요"
        addView(ui.text(sub, T.LABEL, C.TEXT2), ui.fullWithTop(4))

        addView(ui.card().apply {
            addView(ui.text("운전 성향", T.LABEL, C.TEXT3, bold = true))
            p.traits.forEach { addView(ui.text("· $it", T.BODY), ui.fullWithTop(6)) }
            val (n, first) = store.signals(p)
            if (p.record && n > 0) addView(ui.text("· 깜빡이를 먼저 켠 횟수 $first/${n}번", T.BODY, C.TEXT2), ui.fullWithTop(6))
        }, ui.fullWithTop(18))

        addView(ui.card().apply {
            addView(ui.text("길 취향", T.LABEL, C.TEXT3, bold = true))
            val solo = Choice.leftMinutes(w)
            val withP = Choice.leftMinutes(Choice.effective(w, p, Ctx(passenger = true, rain = false, night = false)))
            addView(ui.text("좌회전 1번을 피하려고 ${fmt1(solo)}분까지 더 써요", T.BODY, bold = true), ui.fullWithTop(6))
            addView(ui.text("동승자가 있으면 ${fmt1(withP)}분까지 늘어요", T.BODY), ui.fullWithTop(4))
            addView(ui.text(p.road, T.BODY), ui.fullWithTop(4))
            addView(ui.text("비 오는 날과 밤에는 익숙한 길을 더 골라요", T.BODY), ui.fullWithTop(4))
        }, ui.fullWithTop(14))

        addView(ui.card().apply {
            addView(ui.text("지금 상황", T.LABEL, C.TEXT3, bold = true))
            addView(ui.text(App.ctx().text(), T.BODY, bold = true), ui.fullWithTop(6))
            addView(ui.text(contextEffect(), T.LABEL, C.TEXT2), ui.fullWithTop(4))
        }, ui.fullWithTop(14))

        addView(ui.primary("길 고르기") { App.go(Screen.RECOMMEND) }, ui.fullWithTop(20))
        addView(ui.secondary("운전자 바꾸기") { App.go(Screen.WHO) }, ui.fullWithTop(10))
    }

    private fun contextEffect(): String {
        val c = App.ctx()
        val parts = mutableListOf<String>()
        if (c.passenger) parts += "동승자가 있어 좌회전을 더 피해요"
        if (c.rain) parts += "비가 와서 익숙한 길을 더 쳐 줘요"
        if (c.night) parts += "밤이라 익숙한 길을 더 쳐 줘요"
        return if (parts.isEmpty()) "좌석·와이퍼·전조등 신호로 상황을 알아요" else parts.joinToString(" · ")
    }

    private fun recommendView(): View = ui.vbox().apply {
        val p = App.driver ?: Profiles.me
        val base = App.weightsOf(p)
        val eff = App.effectiveWeights(p)
        val scored = Choice.score(eff)
        val plain = Choice.score(base).best

        addView(ui.text("어디로 갈까요?", T.TITLE, bold = true))
        addView(ui.card().apply {
            addView(ui.text(DemoTrip.DEST_NAME, T.H2, bold = true))
            addView(ui.text("${DemoTrip.DEST_AREA} · 처음 가는 곳 · 출발은 지금 위치", T.LABEL, C.TEXT2))
        }, ui.fullWithTop(14))

        val why = contextReason(App.ctx(), plain, scored.best)
        addView(ui.text("지금 ${App.ctx().text()}", T.LABEL, C.TEXT2, bold = true), ui.fullWithTop(14))
        if (why != null) addView(ui.text(why, T.BODY, C.ACCENT, bold = true), ui.fullWithTop(4))

        DemoTrip.routes.forEach { r -> addView(routeCard(r, scored), ui.fullWithTop(14)) }

        if (App.demoMode) {
            addView(
                ui.text("Virtual Console에서 뒷좌석·와이퍼·전조등을 바꾸면 추천이 바로 바뀌어요.", T.SMALL, C.TEXT3),
                ui.fullWithTop(14),
            )
        }
        addView(ui.secondary("프로필로") { App.go(Screen.PROFILE) }, ui.fullWithTop(12))
    }

    private fun contextReason(c: Ctx, plain: Route, best: Route): String? {
        if (plain.id == best.id) return null
        val why = mutableListOf<String>()
        if (c.passenger) why += "동승자가 있어서"
        if (c.rain) why += "비가 와서"
        if (c.night) why += "밤이라서"
        if (why.isEmpty()) return null
        return "${why.joinToString(", ")} ${best.short}로 바꿨어요"
    }

    private fun routeCard(r: Route, s: Scored): View {
        val isBest = r.id == s.best.id
        return ui.card(stroke = if (isBest) C.ACCENT else null).apply {
            val top = ui.hbox()
            top.addView(ui.text(r.name, T.H2, bold = true), ui.weight())
            if (isBest) top.addView(ui.chip("내 길", true))
            top.addView(ui.text("${r.minutes}분", T.H2, bold = true), ui.wrapStart(14))
            addView(top, ui.full())
            addView(ui.text("좌회전 ${r.lefts}번 · 익숙한 길 ${pct(r.fam)}% · ${fmt1(r.km)}km", T.LABEL, C.TEXT2), ui.fullWithTop(4))
            val prob = s.probs[r.id] ?: 0.0
            val barRow = ui.hbox()
            barRow.addView(ui.bar(prob, if (isBest) C.ACCENT else C.TEXT3), ui.weight())
            barRow.addView(ui.text("${pct(prob)}%", T.LABEL, if (isBest) C.ACCENT else C.TEXT3, bold = true), ui.wrapStart(12))
            addView(barRow, ui.fullWithTop(10))
            if (isBest) addView(ui.text(Choice.reason(r, s.w), T.BODY, C.ACCENT, bold = true), ui.fullWithTop(8))
            val go = if (isBest) ui.primary("이 길로 안내") { App.startGuide(r) } else ui.secondary("이 길로 안내") { App.startGuide(r) }
            addView(go, ui.fullWithTop(12))
        }
    }

    // -----------------------------------------------------------------------
    // 주행 중 화면 (카드 한 장, 한 문장)
    // -----------------------------------------------------------------------

    private fun blockedView(): View = ui.vbox().apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ui.card().apply {
            addView(ui.text("주행 중이에요", T.TITLE, bold = true))
            addView(ui.text("정차하면 프로필을 보고 길을 고를 수 있어요.", T.BODY, C.TEXT2), ui.fullWithTop(8))
        }, ui.full())
    }

    private fun driveView(): View = ui.vbox().apply {
        val t = App.trip
        if (t == null) {
            addView(blockedView())
            return@apply
        }
        val r = t.route
        when {
            !t.started -> {
                addView(ui.card().apply {
                    addView(ui.text("${r.name}로 안내를 시작했어요", T.H2, bold = true))
                    addView(ui.text("순정 내비에 '${r.optionName}' 경로로 넣었어요.", T.BODY, C.TEXT2), ui.fullWithTop(8))
                }, ui.full())
                addView(ui.text("기어를 D로 바꾸면 출발해요.", T.TITLE, bold = true), ui.fullWithTop(28))
                if (App.demoMode) {
                    addView(ui.secondary("기어 없이 출발 (시연)") {
                        App.manualDrive = true
                        App.beginDrive()
                    }, ui.fullWithTop(20))
                }
            }

            t.arrived -> {
                addView(ui.card().apply {
                    addView(ui.text("도착했어요", T.TITLE, bold = true))
                    addView(ui.text("기어를 P로 바꾸면 오늘 주행을 돌아봐요.", T.BODY, C.TEXT2), ui.fullWithTop(8))
                }, ui.full())
                if (App.demoMode || App.car.gear == "P") {
                    addView(ui.primary("돌아보기") { App.openReview() }, ui.fullWithTop(20))
                }
            }

            else -> {
                addView(ui.card().apply {
                    addView(ui.text("${r.name}로 가는 중", T.H2, bold = true))
                    addView(ui.text(remainText(t), T.TITLE, bold = true), ui.fullWithTop(6))
                    if (App.demoMode) addView(ui.bar(t.progress, C.ACCENT), ui.fullWithTop(12))
                    if (t.deviated) addView(ui.text("안내 경로를 벗어나 다시 찾았어요", T.LABEL, C.WARN), ui.fullWithTop(10))
                }, ui.full())

                val cautionNow = t.cautionShown && t.progress < DemoTrip.CAUTION_AT + 0.03
                if (cautionNow) {
                    addView(ui.card(C.WARN_BG, C.WARN).apply {
                        addView(ui.text("300m 앞 · ${DemoTrip.CAUTION_SPOT}", T.LABEL, C.WARN, bold = true))
                        addView(ui.text(DemoTrip.CAUTION_TEXT, T.H2, C.TEXT, bold = true), ui.fullWithTop(6))
                    }, ui.fullWithTop(16))
                }

                val j = t.lastJudgment
                if (j != null && System.currentTimeMillis() - t.lastJudgmentAt < 6_000) {
                    addView(
                        ui.text((if (j.signaled) "✓ " else "· ") + j.sentence(), T.BODY, if (j.signaled) C.OK else C.WARN, bold = true),
                        ui.fullWithTop(16),
                    )
                }

                if (App.demoMode) {
                    val row = ui.hbox()
                    row.addView(ui.secondary("다른 길로 가 보기") { App.deviate() }, ui.weight())
                    row.addView(ui.secondary("도착") { App.arrive() }, ui.weight(1f, 12))
                    addView(row, ui.fullWithTop(24))
                    addView(ui.text("시연용 버튼", T.SMALL, C.TEXT3).apply { gravity = Gravity.CENTER }, ui.fullWithTop(6))
                }
            }
        }
    }

    private fun remainText(t: Trip): String {
        if (!App.demoMode) {
            val s = t.remainSec ?: return "순정 내비 안내를 따라가요"
            val km = (t.remainM ?: 0) / 1000.0
            return "${ceil(s / 60.0).toInt()}분 남음 · ${fmt1(km)}km"
        }
        val left = ceil(t.route.minutes * (1 - t.progress)).toInt().coerceAtLeast(1)
        return "${left}분 남음"
    }

    // -----------------------------------------------------------------------
    // 정차 후 돌아보기
    // -----------------------------------------------------------------------

    private fun reviewView(): View = ui.vbox().apply {
        val t = App.lastTrip
        if (t == null) {
            addView(ui.text("돌아볼 주행이 없어요", T.TITLE, bold = true))
            addView(ui.primary("처음으로") { App.go(Screen.PROFILE) }, ui.fullWithTop(20))
            return@apply
        }
        val p = t.driver
        addView(ui.text("오늘 주행 돌아보기", T.TITLE, bold = true))
        addView(ui.text("${p.name} · ${t.route.name} · ${t.ctx.text()}", T.LABEL, C.TEXT2), ui.fullWithTop(4))

        if (!p.record) {
            addView(ui.card().apply {
                addView(ui.text("게스트로 타서 기록하지 않았어요.", T.BODY))
            }, ui.fullWithTop(16))
        } else {
            val n = App.judge.count - t.signalCount0
            val first = App.judge.first - t.signalFirst0
            addView(ui.card().apply {
                addView(ui.text("깜빡이", T.LABEL, C.TEXT3, bold = true))
                val line = when {
                    n == 0 -> "오늘은 차로를 바꾸거나 꺾은 기록이 없어요."
                    first == n -> "깜빡이를 ${n}번 모두 먼저 켰어요."
                    else -> "깜빡이를 ${n}번 중 ${first}번 먼저 켰어요. ${n - first}번은 깜빡이 없이 움직였어요."
                }
                addView(ui.text(line, T.BODY, bold = true), ui.fullWithTop(6))
            }, ui.fullWithTop(16))

            t.learned?.let { l ->
                addView(ui.card().apply {
                    addView(ui.text("길 취향", T.LABEL, C.TEXT3, bold = true))
                    addView(
                        ui.text(
                            "${l.route.short}을 고르셔서 좌회전 1번을 ${fmt1(Choice.leftMinutes(l.before))}분 → ${fmt1(Choice.leftMinutes(l.after))}분으로 배웠어요.",
                            T.BODY,
                            bold = true,
                        ),
                        ui.fullWithTop(6),
                    )
                    addView(ui.text("다음에도 비슷한 길을 먼저 추천할게요.", T.BODY, C.TEXT2), ui.fullWithTop(4))
                }, ui.fullWithTop(14))
            }

            if (t.cautionShown && !t.deviated) {
                addView(ui.card().apply {
                    addView(ui.text("나만의 주의 지점", T.LABEL, C.TEXT3, bold = true))
                    val k = App.store.cautionPasses(p).coerceAtMost(3)
                    addView(
                        ui.text("${DemoTrip.CAUTION_SPOT}을 이번엔 잘 지났어요. 3번 연속 잘 지나면 알림을 거둘게요. ($k/3)", T.BODY),
                        ui.fullWithTop(6),
                    )
                }, ui.fullWithTop(14))
            }

            if (t.deviated) {
                addView(ui.card(stroke = C.ACCENT).apply {
                    addView(ui.text("중간에 다른 길로 가신 건 일부러였나요?", T.H2, bold = true))
                    val answer = App.reviewAnswer
                    if (answer == null) {
                        val row = ui.hbox()
                        row.addView(ui.primary("응, 일부러") { App.answerDeviation(true) }, ui.weight())
                        row.addView(ui.secondary("아니, 놓쳤어") { App.answerDeviation(false) }, ui.weight(1f, 12))
                        addView(row, ui.fullWithTop(12))
                    } else {
                        addView(ui.text(answer, T.BODY, C.ACCENT, bold = true), ui.fullWithTop(8))
                    }
                }, ui.fullWithTop(14))
            }
        }
        addView(ui.primary("처음으로") { App.go(Screen.PROFILE) }, ui.fullWithTop(20))
    }
}

