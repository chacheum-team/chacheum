package com.chacheum.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** 차 신호·내비 없이 화면만 그리는 앱(미리보기용). */
class PreviewApp : Application()

/**
 * 화면 점검표(s6)의 창 크기로 시연 흐름을 따라가며 화면을 PNG로 남김.
 * 결과: app/build/screens/<창>/<순서>_<화면>.png
 */
abstract class ShotFlow(private val name: String) {
    private val dir by lazy { File(System.getProperty("chacheum.shots") ?: "build/screens", name).apply { mkdirs() } }

    private fun idle(ms: Long = 120) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun shot(a: MainActivity, file: String) {
        a.draw()
        idle()
        val v = a.window.decorView
        val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(dir, "$file.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // 스크롤되는 화면은 아래쪽도 한 장 더
        findScroll(v)?.let { sv ->
            val child = sv.getChildAt(0) ?: return
            if (child.height > sv.height + 8) {
                sv.scrollTo(0, child.height - sv.height)
                val b2 = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(b2))
                File(dir, "${file}_bottom.png").outputStream().use { b2.compress(Bitmap.CompressFormat.PNG, 100, it) }
                sv.scrollTo(0, 0)
            }
        }
    }

    private fun dump(v: View, depth: Int) {
        if (depth > 6) return
        println("  ".repeat(depth) + v.javaClass.simpleName + " " + v.left + "," + v.top + " " + v.width + "x" + v.height + " pad=" + v.paddingTop + "/" + v.paddingBottom)
        if (v is ViewGroup) for (i in 0 until v.childCount) dump(v.getChildAt(i), depth + 1)
    }

    private fun findScroll(v: View): ScrollView? {
        if (v is ScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findScroll(v.getChildAt(i))?.let { return it }
        return null
    }

    fun runFlow() {
        val ctrl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctrl.get()
        App.resetAll()
        idle()
        shot(a, "01_who")
        App.chooseDriver(Profiles.me)
        idle(4_500) // 자동 선택 토스트가 사라지게
        shot(a, "02_home")
        App.openRoutes()
        idle()
        shot(a, "03_routes")
        App.togglePeople() // 혼자 → 동승자
        idle()
        App.togglePeople() // 동승자 → 아이 탐
        idle()
        shot(a, "04_routes_child")
        App.togglePeople() // 아이 탐 → 혼자
        idle(6_500)
        App.startGuide(DemoTrip.route("B")!!)
        idle(4_500)
        shot(a, "05_drive_parked")
        App.startWithoutGear()
        idle(10_000)
        shot(a, "06_drive_quiet")
        idle(19_000) // 진행률 약 0.39: 성수사거리 300m 앞
        shot(a, "07_drive_caution")
        idle(50_000) // 도착 + 2.5초 뒤 돌아보기
        idle(3_000)
        shot(a, "08_reflect")
        App.answerDeviation(true)
        App.answerFeel(true)
        idle()
        shot(a, "09_reflect_answered")
        App.openProfile()
        idle()
        shot(a, "10_profile")
        App.startEdit(Profiles.me)
        App.stepEdit(0.5)
        idle()
        shot(a, "11_profile_edit")
        App.goHome()
        idle()
        shot(a, "12_home_after")
        // 새 운전자: 질문 3개
        App.openWho()
        idle()
        App.startOnboard()
        idle()
        shot(a, "13_onboard")
        App.answerOnboard(false)
        App.answerOnboard(true)
        App.answerOnboard(false)
        idle(4_500)
        shot(a, "14_home_new")
        App.openProfile()
        idle()
        shot(a, "15_profile_new")
        // 게스트
        App.chooseDriver(Profiles.guest)
        idle()
        shot(a, "16_home_guest")
        ctrl.pause().stop().destroy()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = PreviewApp::class, qualifiers = "w840dp-h1184dp-port-mdpi")
class ShotsPortraitDay : ShotFlow("portrait_day") {
    @Test fun flow() = runFlow()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = PreviewApp::class, qualifiers = "w840dp-h1184dp-port-night-mdpi")
class ShotsPortraitNight : ShotFlow("portrait_night") {
    @Test fun flow() = runFlow()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = PreviewApp::class, qualifiers = "w1700dp-h1184dp-land-mdpi")
class ShotsLandscapeDay : ShotFlow("landscape_day") {
    @Test fun flow() = runFlow()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = PreviewApp::class, qualifiers = "w628dp-h855dp-port-mdpi")
class ShotsNarrow : ShotFlow("narrow") {
    @Test fun flow() = runFlow()
}
