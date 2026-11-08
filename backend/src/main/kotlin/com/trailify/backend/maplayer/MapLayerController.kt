package com.trailify.backend.maplayer

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Warstwy tematyczne mapy (ścieżki rowerowe, szlaki, ścieżki leśne) z OpenStreetMap. */
@RestController
@RequestMapping("/api/v1/map-layers")
class MapLayerController(private val service: MapLayerService) {

    @GetMapping("/{type}")
    fun lines(
        @PathVariable("type") type: MapLayerType,
        @RequestParam("minLat") minLat: Double,
        @RequestParam("minLng") minLng: Double,
        @RequestParam("maxLat") maxLat: Double,
        @RequestParam("maxLng") maxLng: Double,
    ): MapLayerResponse = service.lines(type, GeoBox(minLat, minLng, maxLat, maxLng))
}
