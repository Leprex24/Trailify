package com.trailify.core.domain.poi

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.recording.distanceMeters

/** Promień zbierania punktu, ten sam co sprawdza serwer (POST /pois/{id}/collect). */
const val COLLECT_RADIUS_METERS = 50.0

/** Punkt razem z odległością od użytkownika. */
data class PoiDistance(val poi: Poi, val meters: Double) {
    val inRange: Boolean get() = meters <= COLLECT_RADIUS_METERS
}

/**
 * Funkcja nearbyPois z opisu POST /pois/{id}/collect: telefon sam wykrywa, które punkty są blisko.
 * Zwraca wszystkie punkty posortowane od najbliższego; te w promieniu [COLLECT_RADIUS_METERS]
 * mają [PoiDistance.inRange] = true i można je zebrać.
 */
fun nearbyPois(position: LatLng, pois: List<Poi>): List<PoiDistance> =
    pois.map { PoiDistance(it, distanceMeters(position, LatLng(it.latitude, it.longitude))) }
        .sortedBy { it.meters }

/** Punkty do automatycznego zebrania: w zasięgu i jeszcze niezebrane. */
fun poisToCollect(position: LatLng, pois: List<Poi>, collectedIds: Set<Long>): List<Poi> =
    nearbyPois(position, pois).filter { it.inRange && it.poi.id !in collectedIds }.map { it.poi }
