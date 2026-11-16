package com.trailify.feature.discover

import com.trailify.core.domain.model.RouteSummary
import kotlin.random.Random

/** Dlaczego trasa trafiła do kafelków na górze Odkrywaj. */
enum class FeaturedReason { NEWEST, BEST_RATED, RECOMMENDED, RANDOM }

data class FeaturedRoute(val route: RouteSummary, val reason: FeaturedReason)

/**
 * Wybiera kafelki Odkrywaj: najnowsze, najwyżej oceniane, polecane (ranking rekomendacji z backendu,
 * czyli oceny i liczba opinii) i kilka całkiem losowych tras.
 * Trasa pojawia się raz, nawet gdy pasuje do kilku grup (pierwsza grupa wygrywa).
 * Kolejność jest przemieszana (po jednej z każdej grupy), żeby żadna grupa nie schodziła na koniec rzędu.
 * Do losowania służy [random]: przy tym samym ziarnie wynik jest ten sam, więc kafelki nie zmieniają
 * się przy każdym powrocie na ekran.
 */
fun pickFeatured(
    newest: List<RouteSummary>,
    bestRated: List<RouteSummary>,
    recommended: List<RouteSummary>,
    pool: List<RouteSummary>,
    random: Random,
    newestCount: Int = FEATURED_NEWEST,
    bestCount: Int = FEATURED_BEST,
    recommendedCount: Int = FEATURED_RECOMMENDED,
    randomCount: Int = FEATURED_RANDOM,
): List<FeaturedRoute> {
    val taken = mutableSetOf<Long>()

    fun pick(source: List<RouteSummary>, count: Int, reason: FeaturedReason): List<FeaturedRoute> {
        val picked = mutableListOf<FeaturedRoute>()
        for (route in source) {
            if (picked.size == count) break
            if (taken.add(route.id)) picked += FeaturedRoute(route, reason)
        }
        return picked
    }

    val newestPicked = pick(newest, newestCount, FeaturedReason.NEWEST)
    // tylko trasy, które mają jakąś ocenę (inaczej "najwyżej oceniane" byłoby przypadkowe)
    val bestPicked = pick(bestRated.filter { it.averageRating != null }, bestCount, FeaturedReason.BEST_RATED)
    val recommendedPicked = pick(recommended, recommendedCount, FeaturedReason.RECOMMENDED)
    val randomPicked = pick(pool.shuffled(random), randomCount, FeaturedReason.RANDOM)

    return interleave(listOf(newestPicked, bestPicked, recommendedPicked, randomPicked))
}

/** Po jednym elemencie z każdej listy po kolei: a1, b1, c1, a2, b2... */
internal fun <T> interleave(lists: List<List<T>>): List<T> {
    val longest = lists.maxOfOrNull { it.size } ?: return emptyList()
    return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
}

const val FEATURED_NEWEST = 4
const val FEATURED_BEST = 4
const val FEATURED_RECOMMENDED = 3
const val FEATURED_RANDOM = 3
