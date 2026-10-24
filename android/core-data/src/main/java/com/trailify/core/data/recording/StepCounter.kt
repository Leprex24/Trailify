package com.trailify.core.data.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Krokomierz sprzętowy telefonu (czujnik STEP_COUNTER). Czujnik podaje sumę kroków od restartu telefonu,
 * więc liczymy różnice między kolejnymi odczytami. Wymaga zgody ACTIVITY_RECOGNITION (Android 10+).
 * Wszystko na głównym wątku.
 */
class StepCounter(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var lastTotal: Float? = null
    private var onSteps: ((Int) -> Unit)? = null

    val isAvailable: Boolean get() = sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val total = event.values[0]
            val previous = lastTotal
            lastTotal = total
            // pierwszy odczyt to tylko punkt odniesienia; spadek sumy = restart telefonu, pomijamy
            if (previous != null && total > previous) onSteps?.invoke((total - previous).toInt())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Zwraca false, gdy telefon nie ma czujnika albo system odmówił (brak zgody). */
    fun start(callback: (Int) -> Unit): Boolean {
        val s = sensor ?: return false
        lastTotal = null
        onSteps = callback
        val ok = try {
            sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (e: SecurityException) {
            false
        }
        if (!ok) onSteps = null
        return ok
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        onSteps = null
        lastTotal = null
    }
}
