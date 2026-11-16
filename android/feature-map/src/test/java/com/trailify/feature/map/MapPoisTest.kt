package com.trailify.feature.map

import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPoisTest {

    private fun poi(id: Long, lat: Double, lng: Double) = Poi(id, "Punkt $id", null, lat, lng, null)

    // widok na Lublin: 0,1° na 0,1°
    private val viewport = BoundingBox(51.20, 22.50, 51.30, 22.60)

    @Test
    fun obszarDoPobraniaMaZapasIMiesciSieWLimicieSerwera() {
        val box = poiLoadBox(viewport)!!
        assertTrue(box.contains(viewport))
        assertTrue(box.maxLat - box.minLat <= 1.0)
        assertTrue(box.maxLng - box.minLng <= 1.0)
        assertTrue(box.maxLat > viewport.maxLat)
    }

    @Test
    fun zbytSzerokiWidokNiePobieraPunktow() {
        assertNull(poiLoadBox(BoundingBox(50.0, 21.0, 51.0, 23.0)))
        assertNull(poiLoadBox(BoundingBox(51.0, 22.0, 51.9, 22.1)))
    }

    @Test
    fun widokTuzPrzyGranicyDostajeZapasNieWiekszyNizLimitSerwera() {
        val wide = BoundingBox(51.0, 22.0, 51.79, 22.79)
        val box = poiLoadBox(wide)!!
        assertNotNull(box)
        assertTrue(box.maxLat - box.minLat < 1.0)
        assertTrue(box.maxLng - box.minLng < 1.0)
        assertTrue(box.contains(wide))
    }

    @Test
    fun zawieraSprawdzaObszarIPunkt() {
        val outer = BoundingBox(51.0, 22.0, 52.0, 23.0)
        assertTrue(outer.contains(viewport))
        assertFalse(viewport.contains(outer))
        assertTrue(outer.contains(51.5, 22.5))
        assertFalse(outer.contains(53.0, 22.5))
    }

    @Test
    fun swiezeDaneZastepujaStareWObszarzeAPunktySpozaZostaja() {
        val inside = poi(1, 51.25, 22.55)
        val removedOnServer = poi(2, 51.26, 22.56)
        val outside = poi(3, 52.5, 21.0)
        val merged = mergePois(listOf(inside, removedOnServer, outside), listOf(inside), viewport)
        assertEquals(listOf(1L, 3L), merged.map { it.id }.sorted())
    }

    @Test
    fun nowyPunktDochodziBezDuplikatow() {
        val a = poi(1, 51.25, 22.55)
        val b = poi(2, 51.27, 22.57)
        val merged = mergePois(listOf(a), listOf(a, b), viewport)
        assertEquals(listOf(1L, 2L), merged.map { it.id })
    }

    @Test
    fun pinezkaMaIkoneZalezdnaOdZebraniaIRozmiarZalezdnyOdZasiegu() {
        val pois = listOf(poi(1, 51.2, 22.5), poi(2, 51.3, 22.6), poi(3, 51.25, 22.55))
        val result = poisGeoJson(pois, collectedIds = setOf(2), selectedId = null, inRangeIds = setOf(3), zoom = 16.0)
        val json = result.json
        assertTrue(json.contains(""""id":1,"icon":"poi-new","size":1.0"""))
        assertTrue(json.contains(""""id":2,"icon":"poi-collected","size":1.0"""))
        assertTrue(json.contains(""""id":3,"icon":"poi-new","size":1.3"""))
        assertTrue(json.contains("[22.5,51.2]")) // GeoJSON: najpierw długość, potem szerokość
        assertTrue(result.clusterCounts.isEmpty())
    }

    @Test
    fun wybranyPunktJestWiekszy() {
        val json = poisGeoJson(listOf(poi(1, 51.2, 22.5)), emptySet(), selectedId = 1, inRangeIds = emptySet(), zoom = 16.0).json
        assertTrue(json.contains(""""id":1,"icon":"poi-new","size":1.3"""))
    }

    @Test
    fun bliskiePunktyPrzyOddaleniuLaczaSieWKolko() {
        val pois = listOf(poi(1, 51.2500, 22.5500), poi(2, 51.2501, 22.5501), poi(3, 51.60, 22.90))
        val result = poisGeoJson(pois, emptySet(), null, emptySet(), zoom = 10.0)
        assertEquals(setOf(2), result.clusterCounts)
        assertTrue(result.json.contains(""""kind":"cluster","icon":"poi-cluster-2""""))
        assertTrue(result.json.contains(""""id":3,"""))
        assertFalse(result.json.contains(""""id":1,"""))
    }

    @Test
    fun zBliskaPunktySaOsobno() {
        val pois = listOf(poi(1, 51.2500, 22.5500), poi(2, 51.2501, 22.5501))
        val result = poisGeoJson(pois, emptySet(), null, emptySet(), zoom = 15.0)
        assertTrue(result.clusterCounts.isEmpty())
        assertTrue(result.json.contains(""""id":1,"""))
        assertTrue(result.json.contains(""""id":2,"""))
    }

    @Test
    fun wybranyPunktNieWchodziDoKolka() {
        val pois = listOf(poi(1, 51.2500, 22.5500), poi(2, 51.2501, 22.5501))
        val result = poisGeoJson(pois, emptySet(), selectedId = 1, inRangeIds = emptySet(), zoom = 10.0)
        assertTrue(result.clusterCounts.isEmpty())
        assertTrue(result.json.contains(""""id":1,"icon":"poi-new","size":1.3"""))
        assertTrue(result.json.contains(""""id":2,"""))
    }

    @Test
    fun pustaListaDajePustaKolekcje() {
        val result = poisGeoJson(emptyList(), emptySet(), null, emptySet(), zoom = 10.0)
        assertEquals("""{"type":"FeatureCollection","features":[]}""", result.json)
        assertTrue(result.clusterCounts.isEmpty())
    }

    @Test
    fun zasiegObejmujeTylkoNiezebranePunktyWPromieniu50m() {
        val near = poi(1, 51.25000, 22.55000)
        val nearCollected = poi(2, 51.25001, 22.55001)
        val far = poi(3, 51.26, 22.56)
        val me = LatLng(51.25002, 22.55002)
        val ids = poiInRangeIds(me, listOf(near, nearCollected, far), collectedIds = setOf(2))
        assertEquals(setOf(1L), ids)
        assertTrue(poiInRangeIds(null, listOf(near), emptySet()).isEmpty())
    }

    @Test
    fun etykietaKolkaMaGornaGranice() {
        assertEquals("7", clusterLabel(7))
        assertEquals("99", clusterLabel(99))
        assertEquals("99+", clusterLabel(100))
        assertEquals("poi-cluster-7", clusterIconName(7))
        assertEquals(clusterIconName(100), clusterIconName(5000))
    }
}
