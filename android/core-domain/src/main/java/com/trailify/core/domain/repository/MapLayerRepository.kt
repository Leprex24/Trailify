package com.trailify.core.domain.repository

import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.MapLayerType

/** Dane warstw mapy (ścieżki rowerowe, szlaki piesze) z OpenStreetMap. */
interface MapLayerRepository {
    /** Zwraca linie (każda to lista punktów) leżące w podanym prostokącie. */
    suspend fun load(type: MapLayerType, box: BoundingBox): List<List<LatLng>>
}
