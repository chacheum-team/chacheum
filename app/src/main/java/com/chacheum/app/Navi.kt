package com.chacheum.app

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context

/**
 * 순정 내비와 잇는 곳 (NaviHelper).
 * - 고른 경로를 같은 목적지의 경로 옵션으로 요청함
 * - 경로 시작·재탐색·도착·남은 시간을 받아 주행 화면에 씀
 */
class Navi {
    private var helper: NaviHelper? = null
    var status = "연결 전"
        private set
    private var lastRemainLog = 0L

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = App.ui {
            App.log.add(SRC, "경로 시작 · ${routeStartInfo.duration / 60}분 · ${routeStartInfo.distance / 1000}km")
        }

        override fun onReRouteEnded() = App.ui {
            App.log.add(SRC, "재탐색")
            if (!App.demoMode) App.deviate()
        }

        override fun onRouteOptionChanged(routeOption: RouteOption) = App.ui {
            App.log.add(SRC, "경로 옵션 → ${optionName(routeOption)}")
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = App.ui {
            App.log.add(SRC, "목적지 도착 알림")
            if (!App.demoMode) App.arrive()
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = App.ui {
            val next = tbtInfoList.firstOrNull() ?: return@ui
            App.log.add(SRC, "회전 안내 · ${next.distance}m 앞")
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) = App.ui {
            val d = drivingInfo.destination
            App.onRemain(d.duration, d.distance)
            val now = System.currentTimeMillis()
            if (now - lastRemainLog > 15_000) {
                lastRemainLog = now
                App.log.add(SRC, "남은 시간 ${d.duration / 60}분 · ${d.distance / 1000.0}km")
            }
        }

        override fun onError(errorCode: NaviErrorCode) = App.ui {
            App.log.add(SRC, "오류 ${errorCode.name}")
        }
    }

    fun start(ctx: Context) {
        val h = NaviHelper(ctx)
        helper = h
        status = "연결 중"
        try {
            h.initialize(
                onConnected = { App.ui { status = "연결됨"; App.log.add(SRC, "순정 내비와 연결됨") } },
                onFailed = { App.ui { status = "연결 실패"; App.log.add(SRC, "순정 내비 연결 실패") } },
            )
            h.addListener(listener)
        } catch (e: Throwable) {
            status = "초기화 실패"
            App.log.add(SRC, "초기화 실패: ${e.javaClass.simpleName}")
        }
    }

    /** 같은 목적지를 고른 경로의 옵션으로 요청함. */
    fun guide(route: Route) {
        val h = helper
        if (h == null) {
            App.log.add(SRC, "순정 내비가 없어 안내를 요청하지 못함")
            return
        }
        try {
            h.requestRoute(DemoTrip.destination(route.option))
            App.log.add(SRC, "requestRoute(${DemoTrip.DEST_NAME}, ${route.optionName}) 보냄")
        } catch (e: Throwable) {
            App.log.add(SRC, "경로 요청 실패: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val SRC = "NaviHelper"

        fun optionName(o: RouteOption) = when (o) {
            RouteOption.SHORT_TIME -> "최단 시간"
            RouteOption.SHORT_DISTANCE -> "최단 거리"
            RouteOption.FREE -> "무료 도로"
            RouteOption.HIGHWAY -> "고속도로 우선"
            RouteOption.COMFORTABLE -> "편한 길"
            RouteOption.RECOMMENDED -> "추천"
            RouteOption.SCHOOL_ZONE -> "어린이 보호"
            else -> o.name
        }
    }
}
