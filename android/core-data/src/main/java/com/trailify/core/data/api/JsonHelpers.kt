package com.trailify.core.data.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Pomocnicze odczyty pól JSON: brak pola i null dają null (JsonNull jest też JsonPrimitive).

internal fun JsonObject.strOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.doubleOrNull(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.intOrNull(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.longOrNull(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
