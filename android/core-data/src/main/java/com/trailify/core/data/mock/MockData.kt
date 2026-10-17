package com.trailify.core.data.mock

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.model.Route
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.ProfileStats
import java.time.Instant

/** Dane testowe (okolice Lublina), z których korzystają wszystkie atrapy repozytoriów. */
object MockData {

    val me = UserSummary(id = "00000000-0000-0000-0000-000000000001", displayName = "Kacper")
    val anna = UserSummary(id = "00000000-0000-0000-0000-000000000002", displayName = "Anna")
    val marek = UserSummary(id = "00000000-0000-0000-0000-000000000003", displayName = "Marek")

    val myProfile = Profile(
        id = me.id,
        email = "kacper@example.com",
        displayName = me.displayName,
        avatarUrl = null,
        role = Role.USER,
        createdAt = Instant.parse("2026-10-01T10:00:00Z"),
        stats = ProfileStats(totalDistance = 42_500.0, activityCount = 7, routeCount = 3, collectedPoiCount = 2),
    )

    private fun line(vararg p: Pair<Double, Double>) = p.map { LatLng(it.first, it.second) }

    private fun summary(
        id: Long,
        name: String,
        type: ActivityType,
        distance: Double,
        durationSeconds: Int,
        rating: Double?,
        reviews: Int,
        score: Double?,
        start: LatLng,
        owner: UserSummary,
        tourist: Boolean = false,
        scenic: Boolean = false,
        isPublic: Boolean = true,
    ) = RouteSummary(
        id = id,
        name = name,
        activityType = type,
        isTourist = tourist,
        isScenic = scenic,
        isPublic = isPublic,
        totalDistance = distance,
        durationSeconds = durationSeconds,
        averageRating = rating,
        reviewCount = reviews,
        recommendationScore = score,
        startPoint = start,
        thumbnailUrl = null,
        distanceFromQuery = null,
        owner = owner,
        createdAt = Instant.parse("2026-10-0${(id % 9 + 1).toInt()}T12:00:00Z"),
    )

    val routes: List<Route> = listOf(
        Route(
            summary = summary(1, "Wokół Zalewu Zemborzyckiego", ActivityType.CYCLING, 26_400.0, 5_400, 4.7, 31, 4.5,
                LatLng(51.2181, 22.6120), anna, tourist = true, scenic = true),
            description = "Płaska, asfaltowa pętla wokół zalewu. Dużo widoków na wodę.",
            geometry = line(51.2181 to 22.6120, 51.2150 to 22.6250, 51.2060 to 22.6300, 51.2000 to 22.6150, 51.2181 to 22.6120),
            photos = emptyList(),
        ),
        Route(
            summary = summary(2, "Park Saski - Ogród Botaniczny", ActivityType.WALKING, 4_800.0, 3_600, 4.2, 12, 4.0,
                LatLng(51.2465, 22.5530), marek, tourist = true),
            description = "Spokojny spacer przez centrum Lublina.",
            geometry = line(51.2465 to 22.5530, 51.2440 to 22.5600, 51.2400 to 22.5680, 51.2370 to 22.5700),
            photos = emptyList(),
        ),
        Route(
            summary = summary(3, "Bieg w Dolinie Bystrzycy", ActivityType.RUNNING, 8_200.0, 2_700, 3.9, 5, 3.6,
                LatLng(51.2350, 22.5100), anna, scenic = true),
            description = "Trasa biegowa przez pola i las, nawierzchnia mieszana.",
            geometry = line(51.2350 to 22.5100, 51.2400 to 22.5200, 51.2450 to 22.5250, 51.2480 to 22.5150),
            photos = emptyList(),
        ),
        Route(
            summary = summary(4, "Lublin - Nałęczów rowerem", ActivityType.CYCLING, 38_000.0, 8_100, null, 0, null,
                LatLng(51.2465, 22.5684), marek, tourist = true),
            description = "Szlak rowerowy do Nałęczowa, ścieżka rowerowa przez większość trasy.",
            geometry = line(51.2465 to 22.5684, 51.2600 to 22.4900, 51.2700 to 22.4200, 51.2830 to 22.2160),
            photos = emptyList(),
        ),
        Route(
            summary = summary(5, "Moja poranna pętla", ActivityType.RUNNING, 5_000.0, 1_650, null, 0, null,
                LatLng(51.2500, 22.5600), me, isPublic = false),
            description = null,
            geometry = line(51.2500 to 22.5600, 51.2520 to 22.5650, 51.2490 to 22.5700, 51.2500 to 22.5600),
            photos = emptyList(),
        ),
    )

    val pois: List<Poi> = listOf(
        Poi(1, "Brama Krakowska", "Symbol Lublina, brama miejska z XIV wieku.", 51.2477, 22.5675, null),
        Poi(2, "Zamek Lubelski", "Średniowieczny zamek z kaplicą Trójcy Świętej.", 51.2492, 22.5815, null),
        Poi(3, "Wieża Trynitarska", "Punkt widokowy na Stare Miasto.", 51.2471, 22.5658, null),
    )
}
