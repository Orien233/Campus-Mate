package com.example.campusmate.domain.focus

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs

/** Debounced accelerometer detector for stable screen-down placement. */
class FaceDownDetector(
    context: Context,
    private val listener: Listener,
    private val stableMillis: Long = DEFAULT_STABLE_MILLIS
) : SensorEventListener {
    private val sensorManager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var registered = false
    private var faceDownCandidateSince = 0L
    private var currentlyStable = false

    val hasAccelerometer: Boolean
        get() = accelerometer != null

    fun start(): Boolean {
        val sensor = accelerometer ?: return false
        if (!registered) {
            registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        return registered
    }

    fun stop() {
        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }
        faceDownCandidateSince = 0L
        currentlyStable = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values.getOrNull(0) ?: return
        val y = event.values.getOrNull(1) ?: return
        val z = event.values.getOrNull(2) ?: return
        val now = SystemClock.elapsedRealtime()
        val faceDown = z < FACE_DOWN_Z_THRESHOLD && abs(x) < SIDE_AXIS_THRESHOLD && abs(y) < SIDE_AXIS_THRESHOLD

        if (faceDown) {
            if (faceDownCandidateSince == 0L) {
                faceDownCandidateSince = now
            }
            if (!currentlyStable && now - faceDownCandidateSince >= stableMillis) {
                currentlyStable = true
                listener.onFaceDownStable()
            }
        } else {
            faceDownCandidateSince = 0L
            if (currentlyStable) {
                currentlyStable = false
                listener.onPickedUp()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    interface Listener {
        fun onFaceDownStable()

        fun onPickedUp()
    }

    companion object {
        private const val FACE_DOWN_Z_THRESHOLD = -7.0f
        private const val SIDE_AXIS_THRESHOLD = 6.0f
        private const val DEFAULT_STABLE_MILLIS = 3_000L
    }
}
