package com.trailify.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pozycja dolnego paska. Środkowa (wyróżniona) to przycisk START, rysowany osobno nad paskiem. */
internal data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val emphasized: Boolean = false,
)

internal val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DISCOVER, "Odkrywaj", Icons.Outlined.Map, Icons.Filled.Map),
    TopLevelDestination(Routes.ROUTES, "Trasy", Icons.Outlined.Route, Icons.Filled.Route),
    TopLevelDestination(Routes.MAP, "START", Icons.Filled.PlayArrow, Icons.Filled.PlayArrow, emphasized = true),
    TopLevelDestination(Routes.COMMUNITY, "Społeczność", Icons.Outlined.Groups, Icons.Filled.Groups),
    TopLevelDestination(Routes.PROFILE, "Profil", Icons.Outlined.Person, Icons.Filled.Person),
)

private val BarHeight = 64.dp
private val StartRingSize = 80.dp
private val StartButtonSize = 68.dp

// Ile przycisk START wystaje nad pasek; dolna krawędź pierścienia leży (StartRingSize - wystawanie) pod górą paska.
private val StartProtrusion = 28.dp

/** Pasek z czterema pozycjami i pustym miejscem pośrodku na przycisk START. Kolory z motywu (jasny i ciemny). */
@Composable
internal fun TrailifyBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    // Bez cienia i w tym samym kolorze co panel nad nim i przycisk START: tworzą jedną całość.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .height(BarHeight),
        ) {
            topLevelDestinations.forEach { item ->
                if (item.emphasized) {
                    Spacer(Modifier.weight(1f))
                } else {
                    BarItem(item, selected = currentRoute == item.route, onClick = { onSelect(item.route) })
                }
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(item: TopLevelDestination, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = item.label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Okrągły przycisk START wystający nad pasek, z pierścieniem w kolorze paska (efekt wcięcia).
 * Rysowany jako osobna warstwa nad Scaffoldem, bo elementy poza granicami rodzica nie przyjmują dotyku.
 * Modifier ustawia wywołujący (położenie), rozmiar i wygląd są tutaj.
 */
@Composable
internal fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(StartRingSize)
            .shadow(2.dp, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .size(StartButtonSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                // trójkąt ma środek ciężkości na lewo od środka obrysu, stąd drobne przesunięcie dla wizualnego wyśrodkowania
                modifier = Modifier.size(28.dp).offset(x = 2.dp),
            )
            Text(
                text = "START",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Dolny odstęp przycisku START od krawędzi ekranu (bez paska nawigacji systemu), żeby wystawał o [StartProtrusion]. */
internal val StartBottomPadding = BarHeight - (StartRingSize - StartProtrusion)
