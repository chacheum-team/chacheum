package com.chacheum.app

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * 창 하나에 화면을 바꿔 그림(디자인 시스템 "화면과 동작").
 * 정차 중: 누가 운전하나요? → 첫 화면 → 경로 고르기 / 주행 중: 카드 한 장 / 주차 후: 돌아보기 → 나의 운전
 * 위 줄·본문·아래 버튼 줄은 화면마다 새로 만들고, 토스트와 팝업은 그 위 층에 둠.
 */
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var toastLayer: FrameLayout
    private lateinit var popupLayer: FrameLayout
    private val main = Handler(Looper.getMainLooper())
    private val render: () -> Unit = { draw() }

    private var shownScreen: Screen? = null
    private var scrollY = 0
    private var bodyScroll: ScrollView? = null
    private var lastBest: String? = null
    private var lastPeople: People? = null
    private var toastHide: Runnable? = null
    private var popupBuilder: ((Ui) -> View)? = null
    private var insetTop = 0
    private var insetBottom = 0
    private var insetLeft = 0
    private var insetRight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.initLocal(applicationContext)
        root = FrameLayout(this)
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(page, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        toastLayer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        root.addView(toastLayer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        popupLayer = FrameLayout(this).apply { visibility = View.GONE }
        root.addView(popupLayer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                insetTop = b.top; insetBottom = b.bottom; insetLeft = b.left; insetRight = b.right
            } else {
                @Suppress("DEPRECATION")
                run {
                    insetTop = insets.systemWindowInsetTop; insetBottom = insets.systemWindowInsetBottom
                    insetLeft = insets.systemWindowInsetLeft; insetRight = insets.systemWindowInsetRight
                }
            }
            draw()
            insets
        }
        setContentView(root)
        App.toaster = { showToast(it) }
        App.observe(render)
        draw()
    }

    override fun onResume() {
        super.onResume()
        App.changed()
    }

    override fun onDestroy() {
        App.unobserve(render)
        if (App.toaster != null) App.toaster = null
        super.onDestroy()
    }

    /** 창 크기·방향·주야간이 바뀌어도 보던 화면과 고른 값은 App에 있어서 그대로 이어짐. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        draw()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) App.userTouched()
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (popupBuilder != null) {
            closePopup()
            return
        }
        if (!App.back()) {
            if (App.screen == Screen.DRIVE) moveTaskToBack(true) else finish()
        }
    }

    // -----------------------------------------------------------------------
    // 그리기
    // -----------------------------------------------------------------------

    /** 지금 상태로 화면을 다시 그림(미리보기 테스트도 이것을 부름). */
    fun draw() {
        val ui = Ui(this)
        window.setBackgroundDrawable(ColorDrawable(ui.bg))
        root.setBackgroundColor(ui.bg)
        val cfg = resources.configuration
        val wide = cfg.screenWidthDp >= Sz.TWO_COLUMN_MIN
        val screens = Screens(this, ui, wide, ::openPopup, ::openDevTools)
        val s = App.screen

        // 같은 화면을 다시 그릴 때는 스크롤 위치를 그대로 둠
        scrollY = if (s == shownScreen) bodyScroll?.scrollY ?: 0 else 0
        shownScreen = s

        val pg = screens.build(s)
        page.removeAllViews()
        page.setPadding(insetLeft, insetTop, insetRight, insetBottom)
        // 한 칸 화면은 넓은 창에서도 세로 창 폭(840)으로 가운데에 둠
        val maxW = ui.dp(Sz.WIN_PORTRAIT)
        val widthPx = resources.displayMetrics.widthPixels - insetLeft - insetRight
        val limit = !pg.twoColumn && widthPx > maxW
        fun sized(h: Int): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(if (limit) maxW else ViewGroup.LayoutParams.MATCH_PARENT, h).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }

        page.addView(pg.top, sized(ViewGroup.LayoutParams.WRAP_CONTENT))
        if (pg.scroll) {
            val sv = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                isFillViewport = true
                clipToPadding = false
            }
            sv.addView(pg.body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            page.addView(sv, sized(0).apply { weight = 1f })
            bodyScroll = sv
            val y = scrollY
            if (y > 0) sv.post { sv.scrollTo(0, y) }
        } else {
            page.addView(pg.body, sized(0).apply { weight = 1f })
            bodyScroll = null
        }
        pg.bottom?.let { page.addView(it, sized(ViewGroup.LayoutParams.WRAP_CONTENT)) }

        // 세로 창에서 상황 때문에 추천이 바뀌면 토스트로 알리고 되돌리기를 둠(가로 창은 왼쪽 카드가 알림)
        if (s == Screen.ROUTES && !App.driving) {
            val best = screens.routesBest
            val people = App.ctx().people
            val prevBest = lastBest
            val prevPeople = lastPeople
            if (!wide && best != null && prevBest != null && best.id != prevBest) {
                val c = App.ctx()
                val cause = when {
                    people != prevPeople && people == People.CHILD -> "아이가 타서"
                    people != prevPeople && people == People.ADULT -> "동승자가 있어서"
                    c.rain -> "비가 와서"
                    c.night -> "밤이라"
                    else -> null
                }
                if (cause != null) {
                    val undoTo = prevPeople
                    showToast(
                        ToastMsg("$cause ${best.name}로 바꿨어요", if (people != prevPeople) "되돌리기" else null) {
                            App.setPeopleOverride(undoTo)
                        },
                    )
                }
            }
            lastBest = best?.id
            lastPeople = people
        } else {
            lastBest = null
            lastPeople = null
        }

        popupBuilder?.let { showPopupView(it(ui)) }
        // 긴 화면이 아닐 때 토스트가 버튼 줄을 가리지 않게 띄움
        toastLayer.setPadding(0, 0, 0, insetBottom + (if (pg.bottom != null) ui.dp(Sz.BTN_M + Sp.S6) else 0))
    }

    // -----------------------------------------------------------------------
    // 토스트: 4초(동작 버튼이 있으면 6초), 한 번에 하나
    // -----------------------------------------------------------------------

    private fun showToast(msg: ToastMsg) {
        val ui = Ui(this)
        toastHide?.let { main.removeCallbacks(it) }
        toastLayer.removeAllViews()
        var hide: Runnable? = null
        val v = ui.toastView(msg.text, msg.action) {
            msg.onAction?.invoke()
            hide?.run()
        }
        toastLayer.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            setMargins(ui.dp(Sp.S6), 0, ui.dp(Sp.S6), ui.dp(Sp.S6))
        })
        v.post { if (v.width > ui.dp(Sz.CONTENT_MAX)) v.layoutParams = (v.layoutParams as FrameLayout.LayoutParams).also { it.width = ui.dp(Sz.CONTENT_MAX) } }
        hide = Runnable { toastLayer.removeView(v) }
        toastHide = hide
        main.postDelayed(hide, if (msg.action != null) 6_000 else 4_000)
    }

    // -----------------------------------------------------------------------
    // 팝업: 되돌릴 수 없는 일(기록 지우기)에만
    // -----------------------------------------------------------------------

    private fun openPopup(title: String, desc: String?, actions: List<Pair<String, Ui.Kind>>, onAction: (Int) -> Unit) {
        popupBuilder = { ui ->
            ui.popup(title, desc, actions.mapIndexed { i, (label, kind) ->
                ui.button(label, kind) {
                    closePopup()
                    onAction(i)
                }
            })
        }
        draw()
    }

    private fun openDevTools() {
        popupBuilder = { ui ->
            val log = ui.col(Sp.S1).apply {
                App.log.entries.take(10).forEach { e ->
                    addView(ui.text("${e.time}  ${e.src} · ${e.text}", Type.LABEL_M, ui.text3, oneLine = true))
                }
            }
            val car = App.car
            val state = "기어 ${car.gear} · ${App.peopleName(App.carCtx().people)} · 비 ${if (car.wiperOn) "켬" else "끔"} · 밤 ${if (car.lightOn) "켬" else "끔"} · 내비 ${App.navi.status}"
            ui.popup(
                "시연 도구",
                state,
                listOf(
                    ui.button(if (App.demoMode) "시연 모드 끄기" else "시연 모드 켜기", Ui.Kind.BASIC) {
                        App.toggleDemo()
                        closePopup()
                    },
                    ui.button("처음 상태로", Ui.Kind.BASIC) {
                        closePopup()
                        App.resetAll()
                    },
                    ui.button("닫기", Ui.Kind.FILLED) { closePopup() },
                ),
                extra = log,
            )
        }
        draw()
    }

    private fun showPopupView(card: View) {
        val ui = Ui(this)
        popupLayer.removeAllViews()
        popupLayer.setBackgroundColor(ui.scrim)
        popupLayer.visibility = View.VISIBLE
        popupLayer.isClickable = true
        popupLayer.setPadding(ui.dp(Sp.S6), ui.dp(Sp.S6), ui.dp(Sp.S6), ui.dp(Sp.S6))
        val w = minOf(resources.displayMetrics.widthPixels - ui.dp(Sp.S6 * 2), ui.dp(Sz.CONTENT_MAX))
        popupLayer.addView(card, FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun closePopup() {
        popupBuilder = null
        popupLayer.removeAllViews()
        popupLayer.visibility = View.GONE
    }
}
