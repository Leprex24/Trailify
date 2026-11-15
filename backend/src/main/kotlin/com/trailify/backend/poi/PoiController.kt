package com.trailify.backend.poi

import com.trailify.backend.security.currentProfile
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Ciekawe punkty dla użytkowników: GET /pois, GET /pois/{id}, POST /pois, POST /pois/{id}/collect, GET /me/poi-collection. */
@RestController
@RequestMapping("/api/v1")
class PoiController(private val service: PoiService) {

    /** Publiczne (bez tokena): zatwierdzone punkty w obszarze mapy. */
    @GetMapping("/pois")
    fun list(@RequestParam("bbox", required = false) bbox: String?): List<PoiResponse> = service.inBox(bbox)

    @GetMapping("/pois/{poiId}")
    fun get(@PathVariable poiId: Long): PoiResponse = service.get(poiId)

    @PostMapping("/pois")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(@Valid @RequestBody body: PoiCreateRequest, authentication: Authentication): PoiAdminResponse =
        service.create(authentication.currentProfile(), body, STATUS_PENDING)

    @PostMapping("/pois/{poiId}/collect")
    @ResponseStatus(HttpStatus.CREATED)
    fun collect(
        @PathVariable poiId: Long,
        @Valid @RequestBody body: CollectRequest,
        authentication: Authentication,
    ): PoiCollectionEntryResponse = service.collect(authentication.currentProfile(), poiId, body)

    @GetMapping("/me/poi-collection")
    fun myCollection(authentication: Authentication): List<PoiCollectionEntryResponse> =
        service.myCollection(authentication.currentProfile())
}
