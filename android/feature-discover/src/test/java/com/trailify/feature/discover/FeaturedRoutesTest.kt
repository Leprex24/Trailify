package com.trailify.feature.discover

import com.trailify.core.domain.model.RouteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FeaturedRoutesTest {

    private fun route(id: Long, rating: Double? = null) = summary(id).copy(averageRating = rating, reviewCount = if (rating == null) 0 else 3)

    private fun ids(list: List<FeaturedRoute>) = list.map { it.route.id }

    @Test
    fun mieszaGrupyPoJednejTrasie() {
        val result = pickFeatured(
            newest = listOf(route(1), route(2)),
            bestRated = listOf(route(3, 5.0), route(4, 4.5)),
            recommended = listOf(route(7), route(8)),
            pool = listOf(route(5), route(6)),
            random = Random(1),
            newestCount = 2, bestCount = 2, recommendedCount = 2, randomCount = 2,
        )
        assertEquals(listOf(1L, 3L, 7L), ids(result).take(3))
        assertEquals(
            listOf(FeaturedReason.NEWEST, FeaturedReason.BEST_RATED, FeaturedReason.RECOMMENDED, FeaturedReason.RANDOM),
            result.take(4).map { it.reason },
        )
        assertEquals(8, result.size)
    }

    @Test
    fun trasaPojawiaSieRazPierwszaGrupaWygrywa() {
        val shared = route(1, rating = 4.9)
        val result = pickFeatured(
            newest = listOf(shared),
            bestRated = listOf(shared, route(2, 4.0)),
            recommended = listOf(shared),
            pool = listOf(shared, route(3)),
            random = Random(1),
            newestCount = 1, bestCount = 2, recommendedCount = 2, randomCount = 2,
        )
        assertEquals(ids(result).toSet().size, result.size)
        assertEquals(FeaturedReason.NEWEST, result.first { it.route.id == 1L }.reason)
        assertEquals(setOf(1L, 2L, 3L), ids(result).toSet())
    }

    @Test
    fun najwyzejOcenianePomijajaTrasyBezOcen() {
        val result = pickFeatured(
            newest = emptyList(),
            bestRated = listOf(route(1, null), route(2, 4.0)),
            recommended = emptyList(),
            pool = emptyList(),
            random = Random(1),
        )
        assertEquals(listOf(2L), ids(result))
    }

    @Test
    fun limityGrupSaPrzestrzegane() {
        val many = (1L..20L).map { route(it, rating = 4.0) }
        val result = pickFeatured(many, many, many, many, Random(1), newestCount = 4, bestCount = 4, recommendedCount = 3, randomCount = 3)
        assertEquals(14, result.size)
        assertEquals(3, result.count { it.reason == FeaturedReason.RECOMMENDED })
        assertEquals(4, result.count { it.reason == FeaturedReason.NEWEST })
        assertEquals(4, result.count { it.reason == FeaturedReason.BEST_RATED })
        assertEquals(3, result.count { it.reason == FeaturedReason.RANDOM })
    }

    @Test
    fun tenSamZiarnoDajeTenSamWynik() {
        val pool: List<RouteSummary> = (1L..30L).map { route(it) }
        val a = pickFeatured(emptyList(), emptyList(), emptyList(), pool, Random(42))
        val b = pickFeatured(emptyList(), emptyList(), emptyList(), pool, Random(42))
        assertEquals(ids(a), ids(b))
        assertEquals(FEATURED_RANDOM, a.size)
    }

    @Test
    fun pusteWejscieDajePustyWynik() {
        assertTrue(pickFeatured(emptyList(), emptyList(), emptyList(), emptyList(), Random(1)).isEmpty())
    }
}
