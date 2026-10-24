package com.trailify.core.data.recording

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.repository.UserLocationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Jedyne miejsce, które pyta system o GPS (przez Usługi Google Play).
 *
 * Tryby (wybierany automatycznie, najdokładniejszy z potrzebnych):
 *  - nagrywanie: wysoka dokładność, co 2 s, każdy punkt trafia też do nagrania,
 *  - podgląd na mapie: oszczędniej, co 3 s, tylko do kropki na mapie,
 *  - wyłączony: nikt nie potrzebuje pozycji.
 * Wszystko na głównym wątku.
 */
class FusedLocationSource(context: Context) : UserLocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<LatLng?>(null)
    override val location: StateFlow<LatLng?> = _location.asStateFlow()

    private enum class Mode { OFF, WATCHING, RECORDING }

    private var watching = false
    private var recordingListener: ((Location) -> Unit)? = null
    private var mode = Mode.OFF

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                _location.value = LatLng(loc.latitude, loc.longitude)
                recordingListener?.invoke(loc)
            }
        }
    }

    override fun setWatching(watching: Boolean) {
        this.watching = watching
        refresh()
    }

    /** Wołane przez usługę nagrywania. Zwraca false, gdy nie udało się włączyć GPS (brak zgody). */
    fun startRecording(listener: (Location) -> Unit): Boolean {
        recordingListener = listener
        refresh()
        val ok = mode == Mode.RECORDING
        if (!ok) recordingListener = null
        return ok
    }

    fun stopRecording() {
        recordingListener = null
        refresh()
    }

    @SuppressLint("MissingPermission") // SecurityException łapiemy niżej
    private fun refresh() {
        val wanted = when {
            recordingListener != null -> Mode.RECORDING
            watching -> Mode.WATCHING
            else -> Mode.OFF
        }
        // Ten sam tryb jest już włączony (albo nic nie jest potrzebne) - nic do zrobienia.
        // Gdy poprzednia próba nie wyszła (brak zgody), mode == OFF, więc kolejne wywołanie spróbuje znowu.
        if (wanted == mode) return
        if (mode != Mode.OFF) client.removeLocationUpdates(callback)
        mode = Mode.OFF
        if (wanted == Mode.OFF) return

        val request = when (wanted) {
            Mode.RECORDING -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(2f)
                .build()
            else -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3_000L)
                .setMinUpdateIntervalMillis(2_000L)
                .build()
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            mode = wanted
            if (_location.value == null) {
                // kropka od razu, zanim przyjdzie pierwszy świeży odczyt
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && _location.value == null) _location.value = LatLng(loc.latitude, loc.longitude)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Brak zgody na lokalizację", e)
        }
    }

    private companion object {
        const val TAG = "FusedLocationSource"
    }
}
