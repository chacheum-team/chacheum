package com.chacheum.app

import android.content.Context
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** 화면 한 장: 위 줄, 본문, 아래 버튼 줄. 두 칸이면 창 폭을 다 씀. */
class Page(val top: View, val body: View, val bottom: View? = null, val scroll: Boolean = true, val twoColumn: Boolean = false)

/**
 * 화면 여덟 장(디자인 시스템 Screens). 상태는 App에서 읽고, 눌린 것은 App으로 넘김.
 * wide: 가로 창(두 칸), popup: 확인을 받는 팝업을 띄우는 곳.
 */
class Screens(
    private val ctx: Context,
    private val ui: Ui,
    private val wide: Boolean,
    private val popup: (title: String, desc: String?, actions: List<Pair<String, Ui.Kind>>, onAction: (Int) -> Unit) -> Unit,
    private val devTools: () -> Unit,
) {
    /** 경로 고르기에서 지금 추천한 길(토스트용). */
    var routesBest: Route? = null
        private set

    private fun clock(ms: Long): String = DateFormat.getTimeFormat(ctx).format(Date(ms))

    fun build(s: Screen): Page {
        val parkedOnly = s != Screen.DRIVE
        if (parkedOnly && App.driving) return locked(s)
        return when (s) {
            Screen.WHO -> who()
            Screen.ONBOARD -> onboard()
            Screen.HOME -> home()
            Screen.ROUTES -> routes()
            Screen.DRIVE -> drive()
            Screen.REFLECT -> reflect()
            Screen.PROFILE -> profile()
        }
    }

    private fun body(): LinearLayout = ui.col(Sp.S8).apply {
        setPadding(ui.dp(Sp.S6), 0, ui.dp(Sp.S6), ui.dp(Sp.S6))
        clipToPadding = false
        clipChildren = false
    }

    private fun closeButton(onClick: () -> Unit) = ui.iconButton(Ic.CLOSE, "닫기", plain = true, onClick = onClick)

    private fun bottomBar(vararg buttons: TextView): LinearLayout = ui.row(Sp.S4).apply {
        setPadding(ui.dp(Sp.S6), 0, ui.dp(Sp.S6), ui.dp(Sp.S6))
        buttons.forEach { addView(it, ui.weight()) }
    }

    // -----------------------------------------------------------------------
    // 주행 중에 가리는 화면
    // -----------------------------------------------------------------------

    private fun locked(s: Screen): Page {
        val title = when (s) {
            Screen.PROFILE -> "나의 운전"
            Screen.REFLECT -> "오늘 주행"
            Screen.ROUTES -> "${DemoTrip.DEST_NAME}까지"
            else -> null
        }
        val top = ui.top().apply {
            if (title != null) addView(ui.topTitle(title, null), ui.weight()) else {
                addView(ui.logo(devTools))
                addView(ui.space(), ui.weight(1f, 0))
            }
            addView(ui.drivePill("주행 중"))
        }
        val body = body().apply { addView(ui.locked("정차 중에 볼 수 있어요")) }
        return Page(top, body, scroll = false)
    }

    // -----------------------------------------------------------------------
    // 1. 누가 운전하나요?
    // -----------------------------------------------------------------------

    private fun who(): Page {
        App.onWhoShown()
        val store = App.store
        val top = ui.top().apply {
            addView(ui.logo(devTools))
            addView(ui.space(), ui.weight(1f, 0))
            addView(closeButton { App.chooseDriver(App.profile(store.lastDriver())) })
        }
        val lastId = store.lastDriver()
        val family = buildList {
            addAll(Profiles.family)
            store.newDriver()?.let { add(it) }
        }.sortedBy { if (it.id == lastId) 0 else 1 }
        data class Card(val name: String, val meta: String, val avatar: String?, val ic: Ic?, val last: Boolean, val go: () -> Unit)
        val cards = family.map { p ->
            Card(p.name, if (p.id == lastId) "지난번에 운전했어요" else ago(store.lastAt(p)), p.name.take(1), null, p.id == lastId) { App.chooseDriver(p) }
        } + Card("게스트", "기록하지 않아요", null, Ic.GUEST, false) { App.chooseDriver(Profiles.guest) } +
            (if (store.newDriver() == null) listOf(Card("새 운전자", "질문 3개로 시작", null, Ic.PLUS, false) { App.startOnboard() }) else emptyList())

        val grid = ui.col(Sp.S4)
        cards.chunked(2).forEach { pair ->
            grid.addView(ui.row(Sp.S4, center = false).apply {
                pair.forEach { c -> addView(ui.driverCard(c.name, c.meta, c.avatar, c.ic, c.last, c.go), ui.weight(1f, LinearLayout.LayoutParams.MATCH_PARENT)) }
                if (pair.size == 1) addView(ui.space(), ui.weight(1f, 0))
            })
        }
        val body = body().apply {
            addView(ui.headline("누가 운전하나요?"))
            addView(grid)
            if (App.speaker?.speaking == true) addView(ui.voice(Ic.SPEAKER, "말하는 중", null, true), ui.wrap())
        }
        return Page(top, body)
    }

    private fun ago(t: Long?): String {
        if (t == null) return "처음 타요"
        val days = ((System.currentTimeMillis() - t) / 86_400_000L).toInt()
        return when {
            days <= 0 -> "오늘"
            days == 1 -> "어제"
            days < 7 -> "${days}일 전"
            else -> "${days / 7}주 전"
        }
    }

    // -----------------------------------------------------------------------
    // 처음 질문 3개
    // -----------------------------------------------------------------------

    private fun onboard(): Page {
        val step = App.onboardStep
        val (q, a, b) = when (step) {
            0 -> Triple("빨리 가기와 편하게 가기 중 뭐가 먼저예요?", "빨리 가기", "편하게 가기")
            1 -> Triple("좌회전이나 유턴이 부담돼요?", "부담돼요", "괜찮아요")
            else -> Triple("고속도로와 큰 국도 중 어디가 편해요?", "고속도로", "큰 국도")
        }
        val top = ui.top().apply {
            addView(ui.topTitle("새 운전자", "질문 ${step + 1}/3"), ui.weight())
            addView(closeButton { App.leave() })
        }
        val body = body().apply {
            addView(ui.col(Sp.S4).apply {
                addView(ui.headline(q))
                addView(ui.text("답으로 첫 추천을 잡고, 운전할수록 고쳐 가요", Type.BODY_M, ui.text2))
            })
            addView(ui.answers(
                ui.button(a, Ui.Kind.BASIC, Ui.Size.L) { App.answerOnboard(true) },
                ui.button(b, Ui.Kind.BASIC, Ui.Size.L) { App.answerOnboard(false) },
            ))
        }
        return Page(top, body)
    }

    // -----------------------------------------------------------------------
    // 2. 첫 화면
    // -----------------------------------------------------------------------

    private fun home(): Page {
        val p = App.driver ?: Profiles.me
        val store = App.store
        val top = ui.top().apply {
            addView(ui.logo(devTools))
            addView(ui.space(), ui.weight(1f, 0))
            addView(ui.button(p.name, Ui.Kind.BASIC, Ui.Size.S, if (p.record) Ic.PERSON else Ic.GUEST) { App.openWho() }.apply {
                contentDescription = "지금 운전자 ${p.name}, 바꾸기"
            })
        }
        val body = body().apply {
            addView(ui.search("어디로 갈까요?") { App.openRoutes() })
            if (!p.record) {
                addView(ui.empty(Ic.GUEST, "게스트로 타면 기록하지 않아요"))
                return@apply
            }
            val learned = ui.sec("요즘 배운 것", ui.button("모두 보기", Ui.Kind.TEXT) { App.openProfile() })
            val traits = homeTraits(p)
            if (traits.isEmpty()) {
                learned.addView(ui.rows().apply { addView(ui.empty(Ic.NOTE, "몇 번 운전하면 여기에 배운 게 쌓여요")) })
            } else {
                learned.addView(ui.rows().apply { traits.forEach { addView(it) } })
            }
            addView(learned)
            val spots = spotRows(p)
            if (spots.isNotEmpty()) {
                addView(ui.sec("나만의 주의 지점").apply { addView(ui.rows().apply { spots.forEach { addView(it) } }) })
            }
        }
        return Page(top, body)
    }

    private fun homeTraits(p: DriverProfile): List<View> {
        val store = App.store
        val w = App.weightsOf(p)
        val out = mutableListOf<View>()
        if (store.trips(p) == 0 && store.choices(p) == 0) return out
        val choices = store.choices(p)
        out += ui.trait(
            Ic.ROUTE,
            "좌회전 1번을 피하려고 ${fmt1(Choice.leftMinutes(w))}분까지 더 써요",
            if (choices >= 3) "최근 고른 길 ${choices}번에서" else "아직 배우는 중",
        )
        signalTrait(p)?.let { out += it }
        if (p.rainF > 0 && store.rainTrips(p) > 0) {
            out += ui.trait(Ic.RAIN, "비가 오면 익숙한 길을 더 골라요", "비 온 날 주행 ${store.rainTrips(p)}번에서")
        }
        return out
    }

    private fun signalTrait(p: DriverProfile): View? {
        val (n, f) = App.store.recentSignals(p)
        if (n == 0) return null
        if (n < 5) return ui.trait(Ic.SIGNAL, "깜빡이 습관은 아직 배우는 중이에요", "최근 ${n}번 중 ${f}번 먼저 켬")
        val r = f.toDouble() / n
        val s = when {
            r >= 0.75 -> "차로를 바꾸기 전 깜빡이를 먼저 켜는 편이에요"
            r >= 0.5 -> "깜빡이를 먼저 켤 때가 많아요"
            else -> "깜빡이를 조향과 함께 켤 때가 많아요"
        }
        return ui.trait(Ic.SIGNAL, s, "최근 ${n}번 중 ${f}번")
    }

    private fun spotRows(p: DriverProfile): List<View> {
        val store = App.store
        return store.spotIds(p).mapNotNull { id ->
            val s = DemoTrip.spots.firstOrNull { it.id == id } ?: DemoTrip.missedSpot.takeIf { it.id == id } ?: return@mapNotNull null
            ui.spot(s.name, s.meta, store.spotPasses(p, s.id))
        }
    }

    // -----------------------------------------------------------------------
    // 3. 경로 고르기
    // -----------------------------------------------------------------------

    private fun routes(): Page {
        val p = App.driver ?: Profiles.me
        val c = App.ctx()
        val base = App.weightsOf(p)
        val eff = Choice.effective(base, p, c)
        val scored = Choice.score(eff)
        val best = scored.best
        routesBest = best
        val picked = DemoTrip.route(App.picked) ?: best
        val fastest = DemoTrip.fastest
        val now = System.currentTimeMillis()

        val top = ui.top().apply {
            addView(ui.topTitle("${DemoTrip.DEST_NAME}까지", "처음 가는 곳 · ${clock(now)} 출발"), ui.weight())
            addView(closeButton { App.goHome() })
        }

        val chips = ui.row(Sp.S3).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, ui.dp(Sp.S1))
            val peopleIc = when (c.people) {
                People.CHILD -> Ic.CHILD
                else -> Ic.PERSON
            }
            addView(ui.chip(App.peopleName(c.people), peopleIc, true) { App.togglePeople() })
            addView(ui.chip("비", Ic.RAIN, c.rain) { App.toggleRain() })
            addView(ui.chip("밤", Ic.NIGHT, c.night) { App.toggleNight() })
        }

        val cards = ui.col(Sp.S4).apply {
            DemoTrip.routes.forEachIndexed { i, r ->
                val rec = r.id == best.id
                addView(ui.routeOption(
                    index = i, name = r.name, minutes = r.minutes, arrive = clock(now + r.minutes * 60_000L),
                    why = if (rec) Choice.reason(r, p, base, c) else null,
                    facts = Choice.facts(r, c), rec = rec, checked = r.id == picked.id,
                ) { App.pick(r) })
            }
        }

        val go = ui.button("${picked.name}로 안내", Ui.Kind.FILLED) { App.startGuide(picked) }
        val fast = if (picked.id != fastest.id) ui.button("빠른 길로", Ui.Kind.BASIC) { App.startGuide(fastest) } else null

        if (!wide) {
            val body = body().apply {
                addView(chips)
                addView(cards)
            }
            val bottom = if (fast != null) bottomBar(go, fast) else bottomBar(go)
            return Page(top, body, bottom)
        }

        // 가로 창: 왼쪽은 상황과 까닭, 오른쪽은 후보와 버튼
        val left = ui.col(Sp.S8).apply {
            addView(ui.sec("차가 알아챈 상황").apply { addView(chips) })
            addView(contextCard(p, base, c, best))
        }
        val right = ui.col(Sp.S8).apply {
            addView(cards)
            addView(ui.row(Sp.S4).apply {
                addView(go, ui.weight())
                if (fast != null) addView(fast, ui.weight())
            })
        }
        return Page(top, twoColumns(left, right), twoColumn = true)
    }

    /** 가로 창 왼쪽 카드: 상황 때문에 무엇이 바뀌었는지. */
    private fun contextCard(p: DriverProfile, base: Weights, c: Ctx, best: Route): View {
        val plain = Choice.score(Choice.effective(base, p, Ctx.PLAIN)).best
        val solo = Choice.leftMinutes(base)
        val items = mutableListOf<Pair<String, String>>()
        when (c.people) {
            People.CHILD -> {
                items += "좌회전 1번 피하기" to "${fmt1(solo)}분 → ${fmt1(Choice.leftMinutes(Choice.effective(base, p, c)))}분"
                items += "어린이보호구역" to "지나지 않기"
            }
            People.ADULT -> items += "좌회전 1번 피하기" to "${fmt1(solo)}분 → ${fmt1(Choice.leftMinutes(Choice.effective(base, p, c)))}분"
            People.SOLO -> items += "좌회전 1번 피하기" to "${fmt1(solo)}분까지"
        }
        if (c.rain) {
            val m = Choice.famMinutes(Weights(base.t10, 0.0, p.rainF))
            items += "비 오는 날 익숙한 길" to if (m >= 0.05) "+${fmt1(m)}분까지" else "아직 배우는 중"
        }
        if (c.night) {
            val m = Choice.famMinutes(Weights(base.t10, 0.0, p.nightF))
            items += "밤에 익숙한 길" to if (m >= 0.05) "+${fmt1(m)}분까지" else "아직 배우는 중"
        }
        val cause = when {
            c.people == People.CHILD -> "아이가 타서"
            c.people == People.ADULT -> "동승자가 있어서"
            c.rain -> "비가 와서"
            c.night -> "밤이라"
            else -> null
        }
        val say = if (best.id != plain.id && cause != null) "$cause 추천을 바꿨어요" else if (cause != null) "$cause 다시 봤지만 그대로예요" else "평소 취향으로 골랐어요"
        return ui.card().apply {
            addView(ui.text(say, Type.TITLE_M))
            addView(ui.changes(items))
        }
    }

    private fun twoColumns(left: View, right: View): LinearLayout = ui.row(Sp.S12, center = false).apply {
        setPadding(ui.dp(Sp.S6), 0, ui.dp(Sp.S6), ui.dp(Sp.S6))
        clipToPadding = false
        clipChildren = false
        addView(left, ui.weight())
        addView(right, ui.weight())
    }

    // -----------------------------------------------------------------------
    // 4·5. 주행 중: 주의 지점 알림 / 익숙한 길
    // -----------------------------------------------------------------------

    private fun drive(): Page {
        val t = App.trip ?: return home()
        val r = t.route
        val driving = App.driving
        val eta = etaOf(t)
        val top = ui.top().apply {
            addView(ui.topTitle("${DemoTrip.DEST_NAME}까지", "${r.name} · ${clock(eta)} 도착"), ui.weight())
            when {
                driving -> addView(ui.drivePill("주행 중"))
                !t.started -> addView(closeButton { App.goHomeCancel() })
            }
        }
        val body = body().apply {
            when {
                !t.started -> {
                    val next = App.nextSpot(t)
                    addView(ui.driveCard(
                        "출발하면 조용히 따라갈게요",
                        if (next != null) "${next.name} 앞에서만 알려 줄게요" else "도착까지 더 알릴 곳이 없어요",
                        listOf(
                            "도착" to ui.valueText(listOf(clock(eta) to false)),
                            "거리" to ui.num(fmt1(r.km), "km"),
                            "덧붙일 안내" to ui.num("${App.spotsLeft(t)}", "번"),
                        ),
                    ))
                }
                t.arrived -> {
                    addView(ui.driveCard(
                        "도착했어요",
                        if (App.manualDrive) "잠시 뒤 오늘 주행을 돌아봐요" else "P로 바꾸면 오늘 주행을 돌아봐요",
                        listOf("도착" to ui.valueText(listOf(clock(t.arrivedAt) to false))),
                    ))
                }
                t.spotNow != null -> {
                    val s = t.spotNow!!
                    addView(ui.caution(cautionDistance(t, s), s.name, s.say, s.say2, s.lanes, s.goLane).apply {
                        // 다시 듣기는 카드를 누를 때만
                        setOnClickListener { App.speaker?.say("${s.say}. ${s.say2}.") }
                    })
                    if (App.speaker?.speaking == true) addView(ui.voice(Ic.SPEAKER, "말하는 중", null, true), ui.wrap())
                }
                else -> {
                    val next = App.nextSpot(t)
                    addView(ui.driveCard(
                        if (r.fam >= 0.5 && !t.deviated) "익숙한 길이에요" else "조용히 따라갈게요",
                        if (next != null) "${next.name} 앞에서만 알려 줄게요" else "도착까지 더 알릴 곳이 없어요",
                        listOf(
                            "도착" to ui.valueText(listOf(clock(eta) to false)),
                            "남은 거리" to ui.num(remainKm(t), "km"),
                            "덧붙일 안내" to ui.num("${App.spotsLeft(t)}", "번"),
                        ),
                    ))
                }
            }
            if (App.demoMode && t.started && !t.arrived) {
                addView(View(ctx), LinearLayout.LayoutParams(1, 0, 1f))
                val j = t.lastJudgment
                if (j != null) {
                    val (lead, rest) = j.logParts()
                    addView(ui.demoLog(lead, rest))
                } else {
                    addView(ui.demoLog(null, "깜빡이·조향 신호를 기다리는 중"))
                }
            }
        }
        val bottom = if (!t.started && !driving && App.demoMode) bottomBar(ui.button("기어 없이 출발", Ui.Kind.BASIC) { App.startWithoutGear() }) else null
        return Page(top, body, bottom, scroll = false)
    }

    private fun etaOf(t: Trip): Long {
        val now = System.currentTimeMillis()
        t.remainSec?.let { if (!App.demoMode) return now + it * 1000L }
        return if (t.started) t.startedAt + t.route.minutes * 60_000L else now + t.route.minutes * 60_000L
    }

    private fun remainKm(t: Trip): String {
        if (!App.demoMode) t.remainM?.let { return fmt1(it / 1000.0) }
        return fmt1(t.route.km * (1 - t.progress))
    }

    /** 시연 모드에서는 알림 구간(약 5초) 동안 300m에서 줄어드는 것으로 보임. */
    private fun cautionDistance(t: Trip, s: Spot): String {
        val left = ((s.pos - t.progress) / DemoTrip.LEAD).coerceIn(0.0, 1.0)
        val m = (300 * left / 10).roundToInt() * 10
        return "${m.coerceAtLeast(0)}"
    }

    // -----------------------------------------------------------------------
    // 6. 돌아보기
    // -----------------------------------------------------------------------

    private fun reflect(): Page {
        val t = App.lastTrip
        val top = ui.top().apply {
            val sub = t?.let { "${DemoTrip.DEST_NAME} 도착 · ${clock(if (it.arrivedAt > 0) it.arrivedAt else System.currentTimeMillis())}" }
            addView(ui.topTitle("오늘 주행", sub), ui.weight())
            addView(closeButton { App.goHome() })
        }
        if (t == null) {
            return Page(top, body().apply { addView(ui.empty(Ic.NOTE, "돌아볼 주행이 없어요")) })
        }
        val p = t.driver
        val body = body().apply {
            if (!p.record) {
                addView(ui.reflectCard().apply { addView(ui.text("게스트로 타서 기록하지 않았어요", Type.TITLE_M)) })
                return@apply
            }
            // 오늘 배운 것
            val n = t.judgments.size
            val first = t.judgments.count { it.signaled }
            val learned = t.learned
            val routeLine = learned?.let { l ->
                val wasRec = Choice.score(Choice.effective(l.before, p, t.ctx)).best.id == l.route.id
                if (wasRec) "${l.route.noun}을 또 골라서 다음에도 그렇게 추천할게요" else "${l.route.noun}을 골라서 다음 추천에 넣었어요"
            }
            val signalLine = when {
                n == 0 -> null
                first == n -> "오늘은 깜빡이를 모두 먼저 켰어요"
                first == 0 -> "오늘은 깜빡이보다 조향이 먼저였어요"
                else -> "오늘은 깜빡이를 ${n}번 중 ${first}번 먼저 켰어요"
            }
            val say = signalLine ?: routeLine ?: "오늘 주행을 기록했어요"
            val say2 = if (signalLine != null) routeLine else null
            val items = mutableListOf<Pair<String, String>>()
            learned?.let { l ->
                val a = Choice.leftMinutes(l.before)
                val b = Choice.leftMinutes(App.weightsOf(p))
                items += "좌회전 1번 피하기" to if (abs(b - a) < 0.05) "${fmt1(a)}분 그대로" else "${fmt1(a)}분 → ${fmt1(b)}분"
            }
            val (sn, sf) = App.store.recentSignals(p)
            if (sn > 0) items += "깜빡이 먼저" to "최근 ${sn}번 중 ${sf}번"
            DemoTrip.spots.filter { (t.spotState[it.id] ?: 0) == 2 }.forEach { s ->
                items += s.name to when (val k = App.store.spotPasses(p, s.id)) {
                    3 -> "이제 익숙해졌어요"
                    2 -> "한 번 더 지나면 졸업"
                    else -> "${3 - k}번 더 지나면 졸업"
                }
            }
            addView(ui.reflectCard().apply {
                addView(ui.text(say, Type.TITLE_M))
                if (say2 != null) addView(ui.text(say2, Type.BODY_L, ui.text2))
                if (items.isNotEmpty()) addView(ui.changes(items))
            })

            // 일부러 벗어난 길이 있으면 묻기(큰 버튼)
            if (t.deviated) {
                addView(ui.reflectCard().apply {
                    addView(ui.text("${DemoTrip.DEVIATE_ROAD}로 간 건 일부러였나요?", Type.TITLE_S))
                    when (App.deviationAnswer) {
                        null -> addView(ui.answers(
                            ui.button("일부러예요", Ui.Kind.FILLED, Ui.Size.L) { App.answerDeviation(true) },
                            ui.button("아니에요", Ui.Kind.BASIC, Ui.Size.L) { App.answerDeviation(false) },
                        ))
                        true -> addView(ui.done("길 취향에 넣었어요"))
                        false -> addView(ui.done("다음엔 그 앞에서 알려 줄게요"))
                    }
                })
            }

            // 주행 후 한마디(고르지 않아도 됨)
            addView(ui.reflectCard().apply {
                addView(ui.text("오늘 길 어땠어요?", Type.TITLE_S))
                when (App.feelAnswer) {
                    null -> addView(ui.answers(
                        ui.button("편했어요", Ui.Kind.BASIC) { App.answerFeel(true) },
                        ui.button("별로였어요", Ui.Kind.BASIC) { App.answerFeel(false) },
                    ))
                    else -> addView(ui.done("다음 추천에 넣을게요"))
                }
            })
        }
        val bottom = if (p.record) bottomBar(ui.button("나의 운전 보기", Ui.Kind.BASIC) { App.openProfile(p.id) })
        else bottomBar(ui.button("처음 화면으로", Ui.Kind.BASIC) { App.goHome() })
        return Page(top, body, bottom)
    }

    // -----------------------------------------------------------------------
    // 7. 나의 운전
    // -----------------------------------------------------------------------

    private fun profile(): Page {
        val p = App.profile(App.profileOf)
        val store = App.store
        val w = App.weightsOf(p)
        val tabs = buildList {
            Profiles.family.forEach { add(it.id to it.name) }
            store.newDriver()?.let { add(it.id to it.name) }
        }
        val trips = store.trips(p)
        val top = ui.top().apply {
            addView(ui.topTitle("나의 운전", if (trips > 0) "주행 ${trips}번에서 배웠어요" else "아직 배우는 중"), ui.weight())
            if (tabs.size > 1) {
                addView(ui.tabs(tabs, p.id) { App.showProfileOf(it) }, LinearLayout.LayoutParams(ui.dp(if (wide) 400 else 112 * tabs.size), LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            addView(closeButton { App.leave() })
        }

        // 왼쪽: 운전 성향, 상황별 차이
        val traits = ui.sec("운전 성향").apply {
            val rows = mutableListOf<View>()
            signalTrait(p)?.let { rows += it }
            if (trips > 0) p.traits.forEach { rows += ui.trait(it.icon, it.text, it.why) }
            addView(ui.rows().apply {
                if (rows.isEmpty()) addView(ui.empty(Ic.NOTE, "몇 번 운전하면 여기에 배운 게 쌓여요")) else rows.forEach { addView(it) }
            })
        }
        val solo = Choice.leftMinutes(w)
        val child = Choice.leftMinutes(Choice.effective(w, p, Ctx(People.CHILD, false, false)))
        val rainMin = Choice.famMinutes(Weights(w.t10, 0.0, p.rainF))
        val situ = ui.sec("상황별 차이").apply {
            addView(ui.rows().apply {
                addView(ui.situ(Ic.CHILD, "아이가 타면", ui.change("좌회전 피하기", "${fmt1(solo)} → ${fmt1(child)}분")))
                addView(ui.situ(Ic.RAIN, "비가 오면", if (rainMin >= 0.05) ui.change("익숙한 길에", "+${fmt1(rainMin)}분") else learning()))
                addView(ui.situ(Ic.NIGHT, "밤에는", learning()))
            })
        }

        // 오른쪽: 길 취향(분 단위, 고치기), 나만의 주의 지점
        val editing = App.editing
        val prefs = ui.sec("길 취향").apply {
            addView(ui.rows().apply {
                if (editing != null) {
                    addView(ui.pref(
                        "좌회전 1번 피하기",
                        ui.stepper(fmt1(editing), { App.stepEdit(-0.5) }, { App.stepEdit(+0.5) }),
                        ui.button("저장", Ui.Kind.TEXT) { App.saveEdit(p) },
                    ))
                } else {
                    addView(ui.pref("좌회전 1번 피하기", ui.num(fmt1(solo), "분까지"), ui.button("고치기", Ui.Kind.TEXT) { App.startEdit(p) }))
                }
                val fm = Choice.famMinutes(w)
                addView(ui.pref("익숙한 길로 가기", if (fm >= 0.05) ui.num(fmt1(fm), "분까지") else learning(), null))
                val road = store.road(p)
                addView(ui.pref(
                    if (road == RoadPref.NATIONAL) "고속도로보다 큰 국도" else "큰 국도보다 고속도로",
                    ui.text("더 좋아해요", Type.BODY_M_STRONG, ui.text, oneLine = true),
                    null,
                ))
            })
        }
        val spots = spotRows(p)
        val spotSec = ui.sec("나만의 주의 지점").apply {
            addView(ui.rows().apply {
                if (spots.isEmpty()) addView(ui.empty(Ic.SPOT, "아직 없어요. 헷갈린 곳이 생기면 여기에 남겨요")) else spots.forEach { addView(it) }
            })
        }
        val actions = ui.row(Sp.S4).apply {
            addView(ui.button("기록 지우기", Ui.Kind.TEXT) {
                popup(
                    if (p.id == Profiles.me.id) "나의 기록을 지울까요?" else "${p.name}의 기록을 지울까요?",
                    "성향, 길 취향, 주의 지점이 모두 지워져요",
                    listOf("취소" to Ui.Kind.BASIC, "지우기" to Ui.Kind.FILLED),
                ) { i -> if (i == 1) App.clearRecord(p) }
            })
            addView(ui.button("질문 3개 다시 하기", Ui.Kind.TEXT) { App.startOnboard(p.id) })
        }

        if (!wide) {
            val body = body().apply {
                addView(traits)
                addView(situ)
                addView(prefs)
                addView(spotSec)
                addView(actions)
            }
            return Page(top, body)
        }
        val left = ui.col(Sp.S8).apply {
            addView(traits)
            addView(situ)
        }
        val right = ui.col(Sp.S8).apply {
            addView(prefs)
            addView(spotSec)
            addView(actions)
        }
        return Page(top, twoColumns(left, right), twoColumn = true)
    }

    private fun learning(): TextView = ui.text("아직 배우는 중", Type.BODY_M, ui.text3, oneLine = true).apply { gravity = Gravity.END }
}
