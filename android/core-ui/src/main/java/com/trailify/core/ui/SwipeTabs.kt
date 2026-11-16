package com.trailify.core.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Jak daleko trzeba przesunąć palcem w bok, żeby zmienić zakładkę. */
private val SWIPE_DISTANCE = 72.dp

/**
 * Przesunięcie palcem w lewo otwiera następną zakładkę, w prawo poprzednią. Pionowe przewijanie listy
 * i poziome przewijanie elementów w środku (np. rzędu chipów) działają jak dotąd, bo one przechwytują ruch pierwsze.
 */
@Composable
fun Modifier.swipeBetweenTabs(selected: Int, count: Int, onSelect: (Int) -> Unit): Modifier {
    val currentOnSelect by rememberUpdatedState(onSelect)
    return pointerInput(selected, count) {
        val threshold = SWIPE_DISTANCE.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = {
                val target = tabAfterSwipe(selected, count, total, threshold)
                if (target != selected) currentOnSelect(target)
            },
            onHorizontalDrag = { _, delta -> total += delta },
        )
    }
}

/** Zakładka po przesunięciu o [dragX] pikseli (ujemne = w lewo). Krótki ruch i brzegi zostawiają [selected]. */
fun tabAfterSwipe(selected: Int, count: Int, dragX: Float, threshold: Float): Int = when {
    dragX <= -threshold && selected < count - 1 -> selected + 1
    dragX >= threshold && selected > 0 -> selected - 1
    else -> selected
}
