package com.trailify.backend.route

import com.trailify.backend.web.ApiException
import org.springframework.http.HttpStatus

const val CODE_OWN_ROUTE = "OWN_ROUTE"

/** Własnych tras nie zapisujemy gwiazdką: są już na liście "Własne". Zwraca wyjątek do rzucenia albo null. */
fun saveRejection(isOwner: Boolean): ApiException? =
    if (isOwner) ApiException(HttpStatus.CONFLICT, CODE_OWN_ROUTE, "Własnych tras nie trzeba zapisywać") else null
