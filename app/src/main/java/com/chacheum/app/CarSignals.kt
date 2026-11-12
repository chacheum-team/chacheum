package com.chacheum.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import kotlin.math.abs

/**
 * 차 신호를 받아 App 상태로 넘김.
 * - Vehicle SDK: 깜빡이, 조향각, 좌석 착석(조수석·뒷좌석), 와이퍼, 전조등, 기어
 * - AAOS Car API: 기어(GEAR_SELECTION, Vehicle SDK 기어 API가 deprecated라 함께 씀), 주행 규제
 */
class CarSignals {
    private var vehicle: Vehicle? = null

    var gear = "?"
        private set
    var turn = Turn.OFF
        private set
    var steer = 0f
        private set
    private val seats = HashMap<String, Boolean>()
    val passenger get() = seats.values.any { it }
    var wiperOn = false
        private set
    var lightOn = false
        private set
    /** 주행 규제(주행 중 화면 제한)가 걸렸는지. */
    var restricted = false
        private set
    var connected = "연결 전"
        private set

    private val frontSeat = SeatArea(SeatValue.ROW_1_RIGHT)
    private val rearLeftSeat = SeatArea(SeatValue.ROW_2_LEFT)
    private val rearRightSeat = SeatArea(SeatValue.ROW_2_RIGHT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var lastSteerLog = 0L

    // ---- Vehicle SDK 리스너 ----
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = App.ui { onTurn(state) }
        override fun onFailed(e: Exception) = fail("깜빡이", e)
    }
    private val steerListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = App.ui { onSteer(angle) }
        override fun onFailed(e: Exception) = fail("조향각", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = App.ui { onGear(gearName(gear)) }
        override fun onFailed(e: Exception) = fail("기어", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = App.ui { onWiper(state == WiperState.ON) }
        override fun onFailed(e: Exception) = fail("와이퍼", e)
    }
    private val lightListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = App.ui { onLight(state == LightState.ON) }
        override fun onFailed(e: Exception) = fail("전조등", e)
    }
    private val frontSeatListener = seatListener("조수석")
    private val rearLeftListener = seatListener("뒷좌석 왼쪽")
    private val rearRightListener = seatListener("뒷좌석 오른쪽")

    private fun seatListener(name: String) = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupancy: Boolean?) = App.ui { onSeat(name, occupancy == true) }
        override fun onFailed(e: Exception) = fail("$name 착석", e)
    }

    fun start(ctx: Context) {
        startVehicle(ctx)
        startCarApi(ctx)
    }

    private fun startVehicle(ctx: Context) {
        val v = Vehicle(ctx)
        vehicle = v
        try {
            v.initialize()
        } catch (e: Throwable) {
            connected = "Vehicle 초기화 실패"
            App.log.add("Vehicle", "초기화 실패: ${e.javaClass.simpleName}")
            return
        }
        connected = "Vehicle 연결 요청"
        guard { v.getTurnSignal().registerTurnSignalState(turnListener) }
        guard { v.getSteeringWheel().registerSteeringWheelAngle(steerListener) }
        guard { v.getGear().registerGearSelection(gearListener) }
        guard { v.getSeat().registerSeatOccupancy(frontSeat, frontSeatListener) }
        guard { v.getSeat().registerSeatOccupancy(rearLeftSeat, rearLeftListener) }
        guard { v.getSeat().registerSeatOccupancy(rearRightSeat, rearRightListener) }
        guard { v.getWiper().registerWiperState(windshield, wiperListener) }
        guard { v.getLight().registerLowBeamLightState(lightListener) }
        App.log.add("Vehicle", "깜빡이·조향·기어·좌석·와이퍼·전조등 받기 시작")

        // 처음 값을 한 번 읽음
        guard {
            v.getGear().getCurrentGearSelectionState({ g -> App.ui { setGearQuietly(gearName(g)) } }, { })
        }
        guard {
            v.getWiper().getCurrentWiperState(windshield, { s -> App.ui { wiperOn = s == WiperState.ON; App.changed() } }, { })
        }
        guard {
            v.getLight().getCurrentLowBeamLightState({ s -> App.ui { lightOn = s == LightState.ON; App.changed() } }, { })
        }
        readSeat(v, "조수석", frontSeat)
        readSeat(v, "뒷좌석 왼쪽", rearLeftSeat)
        readSeat(v, "뒷좌석 오른쪽", rearRightSeat)
        guard {
            v.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                { _, bounds ->
                    App.ui {
                        val maxAbs = maxOf(abs(bounds.minValue), abs(bounds.maxValue))
                        // 범위가 좁은 값(바퀴 각도 등)이면 판정 기준도 낮춤
                        if (maxAbs in 1f..59f) App.judge.threshold = maxAbs / 3f
                    }
                },
                { },
            )
        }
    }

    private fun readSeat(v: Vehicle, name: String, area: SeatArea) = guard {
        v.getSeat().getCurrentSeatOccupancy(area, { s -> App.ui { seats[name] = s == true; App.changed() } }, { })
    }

    // ---- AAOS Car API ----
    private var car: Car? = null
    private var gearCallback: CarPropertyManager.CarPropertyEventCallback? = null

    private fun startCarApi(ctx: Context) {
        try {
            Car.createCar(ctx, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                App.ui { if (ready) onCarReady(c) }
            }
        } catch (e: Throwable) {
            // 기기에 android.car가 없으면 Vehicle SDK 신호만 씀
            App.log.add("AAOS", "Car API 사용 불가")
        }
    }

    @Suppress("DEPRECATION")
    private fun onCarReady(c: Car) {
        car = c
        try {
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            if (ux != null) {
                onUx(ux.currentCarUxRestrictions)
                ux.registerListener { r -> App.ui { onUx(r) } }
            }
        } catch (_: Throwable) {
        }
        try {
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val g = value.value as? Int
                    App.ui { onGear(aaosGearName(g)) }
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) {}
            }
            gearCallback = cb
            pm.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (_: Throwable) {
        }
    }

    private fun onUx(r: CarUxRestrictions?) {
        val next = r?.isRequiresDistractionOptimization == true
        if (next != restricted) {
            restricted = next
            App.log.add("AAOS", if (next) "주행 규제 켜짐 · 카드 한 장만 보임" else "주행 규제 꺼짐")
        }
    }

    // ---- 값이 바뀔 때 ----
    private fun onTurn(state: TurnSignalValue?) {
        val t = when (state) {
            TurnSignalValue.LEFT -> Turn.LEFT
            TurnSignalValue.RIGHT -> Turn.RIGHT
            else -> Turn.OFF
        }
        if (t == turn) return
        turn = t
        App.judge.onTurn(t)
        App.log.add("Vehicle", "깜빡이 ${turnName(t)}")
    }

    private fun onSteer(angle: Float?) {
        val a = angle ?: return
        steer = a
        val now = System.currentTimeMillis()
        if (now - lastSteerLog > 1500 && abs(a) >= 5f) {
            lastSteerLog = now
            App.log.add("Vehicle", "조향각 ${fmt1(a.toDouble())}°")
        }
        App.judge.onSteer(a)?.let { App.onJudgment(it) }
    }

    private fun onGear(g: String) {
        if (g == gear || g == "?") return
        gear = g
        App.onGear(g)
    }

    private fun setGearQuietly(g: String) {
        if (g != "?") gear = g
        App.changed()
    }

    private fun onSeat(name: String, occupied: Boolean) {
        if (seats[name] == occupied) return
        seats[name] = occupied
        App.log.add("Vehicle", "$name ${if (occupied) "앉음" else "비어 있음"}")
    }

    private fun onWiper(on: Boolean) {
        if (on == wiperOn) return
        wiperOn = on
        App.log.add("Vehicle", "와이퍼 ${if (on) "켜짐" else "꺼짐"}")
    }

    private fun onLight(on: Boolean) {
        if (on == lightOn) return
        lightOn = on
        App.log.add("Vehicle", "전조등 ${if (on) "켜짐" else "꺼짐"}")
    }

    private fun fail(what: String, e: Exception) {
        App.log.add("Vehicle", "$what 오류: ${e.javaClass.simpleName}")
    }

    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            App.log.add("Vehicle", "등록 실패: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        fun turnName(t: Turn) = when (t) {
            Turn.LEFT -> "왼쪽"
            Turn.RIGHT -> "오른쪽"
            Turn.OFF -> "꺼짐"
        }

        fun gearName(g: VehicleGear?) = when (g) {
            VehicleGear.GEAR_PARK -> "P"
            VehicleGear.GEAR_REVERSE -> "R"
            VehicleGear.GEAR_NEUTRAL -> "N"
            VehicleGear.GEAR_DRIVE -> "D"
            else -> "?"
        }

        /** android.car.VehicleGear 값 (NEUTRAL 1, REVERSE 2, PARK 4, DRIVE 8, 그 밖의 전진 단수 16 이상). */
        fun aaosGearName(g: Int?) = when (g) {
            1 -> "N"
            2 -> "R"
            4 -> "P"
            null -> "?"
            else -> if (g >= 8) "D" else "?"
        }
    }
}
