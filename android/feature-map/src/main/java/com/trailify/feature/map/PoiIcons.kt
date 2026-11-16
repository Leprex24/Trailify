package com.trailify.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.DisplayMetrics
import org.maplibre.android.maps.Style
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Ikony ciekawych punktów rysujemy kodem (bez plików graficznych): pinezka w kształcie kropli i kółko z liczbą.
private const val AMBER = 0xFFF9A825.toInt()
private const val GREEN = 0xFF2E7D32.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private const val PIN_WIDTH_DP = 30f
private const val PIN_HEIGHT_DP = 38f
private const val CLUSTER_SIZE_DP = 38f
private const val OUTLINE_DP = 2f

/** Dodaje do stylu ikony pinezek (do wywołania przy każdym nowym stylu mapy). */
internal fun registerPoiImages(style: Style, density: Float) {
    style.addImage(POI_ICON_NEW, pinBitmap(density, AMBER, collected = false))
    style.addImage(POI_ICON_COLLECTED, pinBitmap(density, GREEN, collected = true))
}

/** Dodaje ikonę kółka z liczbą punktów, jeśli stylu jeszcze jej nie ma. */
internal fun ensureClusterImage(style: Style, count: Int, density: Float) {
    val name = clusterIconName(count)
    if (style.getImage(name) == null) style.addImage(name, clusterBitmap(density, clusterLabel(count)))
}

private fun newBitmap(widthDp: Float, heightDp: Float, density: Float): Bitmap =
    Bitmap.createBitmap((widthDp * density).roundToInt(), (heightDp * density).roundToInt(), Bitmap.Config.ARGB_8888).also {
        // gęstość bitmapy mówi MapLibre, ile pikseli to jeden dp
        it.density = (DisplayMetrics.DENSITY_DEFAULT * density).roundToInt()
    }

/** Pinezka: kropla z białą obwódką, a w środku biała gwiazdka (nowy punkt) albo ptaszek (zebrany). */
private fun pinBitmap(density: Float, color: Int, collected: Boolean): Bitmap {
    val bitmap = newBitmap(PIN_WIDTH_DP, PIN_HEIGHT_DP, density)
    val canvas = Canvas(bitmap)
    val width = bitmap.width.toFloat()
    val height = bitmap.height.toFloat()
    val outline = OUTLINE_DP * density
    val radius = width / 2f - outline
    val cx = width / 2f
    val cy = radius + outline
    val tipY = height - outline

    // kropla = koło + trójkąt od czubka stycznie do koła
    val alpha = acos((radius / (tipY - cy)).toDouble())
    val dx = (radius * sin(alpha)).toFloat()
    val dy = (radius * cos(alpha)).toFloat()
    val drop = Path().apply {
        addCircle(cx, cy, radius, Path.Direction.CW)
        val tail = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx - dx, cy + dy)
            lineTo(cx + dx, cy + dy)
            close()
        }
        op(tail, Path.Op.UNION)
    }
    // obwódka leży po zewnętrznej stronie krawędzi: rysujemy ją pod wypełnieniem
    canvas.drawPath(
        drop,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = outline * 2f
            strokeJoin = Paint.Join.ROUND
            this.color = WHITE
        },
    )
    canvas.drawPath(drop, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color })

    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = WHITE }
    if (collected) {
        white.style = Paint.Style.STROKE
        white.strokeWidth = 2.5f * density
        white.strokeCap = Paint.Cap.ROUND
        white.strokeJoin = Paint.Join.ROUND
        val tick = Path().apply {
            moveTo(cx - radius * 0.42f, cy + radius * 0.02f)
            lineTo(cx - radius * 0.10f, cy + radius * 0.34f)
            lineTo(cx + radius * 0.46f, cy - radius * 0.30f)
        }
        canvas.drawPath(tick, white)
    } else {
        white.style = Paint.Style.FILL
        val outer = radius * 0.62f
        val inner = outer * 0.45f
        val star = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        canvas.drawPath(star, white)
    }
    return bitmap
}

/** Kółko z liczbą punktów, widoczne przy oddaleniu mapy. */
private fun clusterBitmap(density: Float, label: String): Bitmap {
    val bitmap = newBitmap(CLUSTER_SIZE_DP, CLUSTER_SIZE_DP, density)
    val canvas = Canvas(bitmap)
    val outline = OUTLINE_DP * density
    val center = bitmap.width / 2f
    val radius = center - outline
    canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
    canvas.drawCircle(center, center, radius - outline, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER })
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(label, center, center - (text.descent() + text.ascent()) / 2f, text)
    return bitmap
}
