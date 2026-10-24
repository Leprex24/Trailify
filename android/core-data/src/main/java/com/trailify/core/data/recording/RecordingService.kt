package com.trailify.core.data.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.trailify.core.domain.model.RecordingError
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Locale

/**
 * Usługa pierwszoplanowa: działa, dopóki trwa nagrywanie, także przy zablokowanym ekranie.
 * Dzięki powiadomieniu system jej nie zabija. Prosi [FusedLocationSource] o pozycje GPS (tylko gdy status = RECORDING,
 * na pauzie GPS z nagrania jest wyłączony, żeby oszczędzać baterię) i przekazuje je do [RecordingStore].
 */
class RecordingService : Service() {

    private val store: RecordingStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val source: FusedLocationSource by inject()
    private val stepCounter by lazy { StepCounter(this) }
    private var observeJob: Job? = null
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Nagrywanie aktywności", NotificationManager.IMPORTANCE_LOW)
        notificationManager().createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Musi się wydarzyć od razu po starcie usługi, inaczej system zakończy aplikację błędem.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(store.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Brak zgody na lokalizację, nie można uruchomić usługi", e)
            store.onError(RecordingError.LOCATION_PERMISSION)
            stopSelf()
            return START_NOT_STICKY
        }

        if (observeJob == null) {
            observeJob = scope.launch { observeStatus() }
            tickJob = scope.launch { runTicker() }
        }
        return START_NOT_STICKY
    }

    /** Włącza GPS przy nagrywaniu, wyłącza na pauzie, a po zakończeniu zatrzymuje usługę. */
    private suspend fun observeStatus() {
        var last: RecordingStatus? = null
        store.state.collect { state ->
            if (state.status == last) return@collect
            last = state.status
            when (state.status) {
                RecordingStatus.RECORDING -> {
                    startLocationUpdates()
                    startStepCounting()
                }
                RecordingStatus.PAUSED -> {
                    stopLocationUpdates()
                    stepCounter.stop()
                }
                RecordingStatus.IDLE, RecordingStatus.FINISHED -> {
                    stopLocationUpdates()
                    stepCounter.stop()
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
            }
            notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private suspend fun runTicker() {
        var ticks = 0
        while (scope.isActive) {
            delay(1000)
            store.tick()
            if (++ticks % NOTIFICATION_EVERY_TICKS == 0) {
                notificationManager().notify(NOTIFICATION_ID, buildNotification(store.state.value))
            }
        }
    }

    private fun startLocationUpdates() {
        if (!source.startRecording(store::onLocation)) {
            Log.e(TAG, "Nie udało się włączyć GPS (brak zgody na lokalizację?)")
            store.onError(RecordingError.LOCATION_PERMISSION)
        }
    }

    private fun stopLocationUpdates() = source.stopRecording()

    /** Kroki są opcjonalne: bez czujnika lub zgody po prostu ich nie pokazujemy. */
    private fun startStepCounting() {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        if (permitted && stepCounter.start(store::onSteps)) store.enableSteps()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        stepCounter.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun buildNotification(state: RecordingState): Notification {
        val title = if (state.status == RecordingStatus.PAUSED) "Nagrywanie wstrzymane" else "Nagrywanie trasy"
        val text = "${clock(state.elapsedSeconds)} · ${distance(state.distanceMeters)}"
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    private fun clock(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun distance(meters: Double): String =
        String.format(Locale.forLanguageTag("pl-PL"), "%.2f km", meters / 1000)

    private companion object {
        const val TAG = "RecordingService"
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_EVERY_TICKS = 5
    }
}
