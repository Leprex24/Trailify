package com.trailify.backend.routing

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/routing")
class RoutingController(private val service: RoutingService) {

    /** Wyznacza trasę po drogach i ścieżkach przez podane punkty (planowanie trasy na mapie). */
    @PostMapping("/plan")
    fun plan(@Valid @RequestBody body: RoutePlanRequest): RoutePlanResponse = service.plan(body)
}
