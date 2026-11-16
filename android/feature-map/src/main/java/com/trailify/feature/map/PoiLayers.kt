package com.trailify.feature.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

internal const val POI_SOURCE = "trailify-poi-source"
internal const val POI_LAYER = "trailify-poi-layer"
internal const val POI_CLUSTER_LAYER = "trailify-poi-cluster-layer"
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

/**
 * Ciekawe punkty: pinezki z ikonami (gwiazdka albo ptaszek) i kółka z liczbą przy oddaleniu.
 * Same ikony są rysowane kodem (PoiIcons.kt). Wywołać przed warstwami planu i nagrywania, żeby ślad był na wierzchu.
 */
internal fun addPoiLayers(style: Style, density: Float) {
    registerPoiImages(style, density)
    style.addSource(GeoJsonSource(POI_SOURCE).also { it.setGeoJson(EMPTY) })
    // czubek pinezki wskazuje miejsce punktu
    style.addLayer(
        SymbolLayer(POI_LAYER, POI_SOURCE)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("poi")))
            .withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(Expression.get("size")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
    )
    style.addLayer(
        SymbolLayer(POI_CLUSTER_LAYER, POI_SOURCE)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("cluster")))
            .withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
    )
}
