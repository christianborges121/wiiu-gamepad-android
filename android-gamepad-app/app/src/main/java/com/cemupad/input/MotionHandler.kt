package com.cemupad.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.cemupad.dsu.DSUServer

/**
 * Captures accelerometer and gyroscope data from Android sensors,
 * remaps them for landscape orientation, converts units to DSU standards
 * (acceleration in g's, gyroscope in deg/s), and synchronizes timestamps.
 */
class MotionHandler(
    context: Context,
    private val dsuServer: DSUServer
) : SensorEventListener {

    companion object {
        const val RAD_TO_DEG = 57.29577951308232f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var displayRotation: Int = Surface.ROTATION_90

    // Cached latest gyro readings (in deg/s)
    @Volatile
    private var gyroPitch = 0.0f
    @Volatile
    private var gyroYaw = 0.0f
    @Volatile
    private var gyroRoll = 0.0f

    private var isRunning = false

    fun start(): Boolean {
        if (isRunning) return true

        val accelRegistered = accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: false

        val gyroRegistered = gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: false

        isRunning = accelRegistered || gyroRegistered
        return isRunning
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                // Gyroscope angular velocity in rad/s -> convert to deg/s and remap
                val rawX = event.values[0] * RAD_TO_DEG
                val rawY = event.values[1] * RAD_TO_DEG
                val rawZ = event.values[2] * RAD_TO_DEG

                val (remapX, remapY, remapZ) = remapSensorValues(rawX, rawY, rawZ, displayRotation)
                // Cache latest gyro values without advancing motion timestamp
                gyroPitch = remapX
                gyroYaw = remapY
                gyroRoll = remapZ
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // Accelerometer in m/s^2 -> convert to g's (1g ≈ 9.80665 m/s^2) and remap
                val rawX = event.values[0] / SensorManager.GRAVITY_EARTH
                val rawY = event.values[1] / SensorManager.GRAVITY_EARTH
                val rawZ = event.values[2] / SensorManager.GRAVITY_EARTH

                val (remapX, remapY, remapZ) = remapSensorValues(rawX, rawY, rawZ, displayRotation)

                // Timestamp strictly on accelerometer event (nanoseconds -> microseconds)
                val timestampUs = event.timestamp / 1000L

                // Update DSU state with synchronized accel + latest gyro
                dsuServer.updateState { state ->
                    state.motionTimestampUs = timestampUs
                    state.accelX = remapX
                    state.accelY = remapY
                    state.accelZ = remapZ
                    state.gyroPitch = gyroPitch
                    state.gyroYaw = gyroYaw
                    state.gyroRoll = gyroRoll
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Remaps device coordinates to screen coordinates depending on active landscape rotation.
     */
    private fun remapSensorValues(x: Float, y: Float, z: Float, rotation: Int): Triple<Float, Float, Float> {
        return when (rotation) {
            Surface.ROTATION_90 -> {
                // Landscape (home button on the right / top of phone to the left)
                Triple(-y, x, z)
            }
            Surface.ROTATION_270 -> {
                // Reverse Landscape (home button on the left / top of phone to the right)
                Triple(y, -x, z)
            }
            Surface.ROTATION_180 -> {
                // Reverse Portrait
                Triple(-x, -y, z)
            }
            else -> {
                // Default Portrait (ROTATION_0)
                Triple(x, y, z)
            }
        }
    }
}
