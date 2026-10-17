package com.trailify.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.feature.auth.LoginScreen
import com.trailify.feature.community.CommunityScreen
import com.trailify.feature.discover.DiscoverScreen
import com.trailify.feature.discover.RouteDetailsScreen
import com.trailify.feature.map.MapScreen
import com.trailify.feature.profile.ProfileScreen
import com.trailify.feature.recording.RecordingScreen
import com.trailify.feature.routes.RoutesScreen
import org.koin.compose.koinInject
import androidx.compose.material3.MaterialTheme

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DISCOVER, "Odkrywaj", Icons.Default.Search),
    TopLevelDestination(Routes.ROUTES, "Trasy", Icons.Default.Place),
    TopLevelDestination(Routes.MAP, "START", Icons.Default.PlayArrow),
    TopLevelDestination(Routes.COMMUNITY, "Społeczność", Icons.Default.Favorite),
    TopLevelDestination(Routes.PROFILE, "Profil", Icons.Default.Person),
)

@Composable
fun TrailifyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    // Wylogowanie przenosi na ekran logowania, zalogowanie z powrotem na mapę.
    val auth: AuthRepository = koinInject()
    val user by auth.currentUser.collectAsStateWithLifecycle()
    LaunchedEffect(user) {
        val current = navController.currentDestination?.route
        if (user == null && current != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        } else if (user != null && current == Routes.LOGIN) {
            navController.navigate(Routes.MAP) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.MAP) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.DISCOVER) {
                DiscoverScreen(onRouteClick = { navController.navigate(Routes.routeDetails(it)) })
            }
            composable(Routes.ROUTES) {
                RoutesScreen(onRouteClick = { navController.navigate(Routes.routeDetails(it)) })
            }
            composable(Routes.MAP) {
                MapScreen(onStartRecording = { navController.navigate(Routes.RECORDING) })
            }
            composable(Routes.COMMUNITY) { CommunityScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }

            composable(Routes.RECORDING) {
                RecordingScreen(onClose = { navController.popBackStack() })
            }
            composable(
                route = Routes.ROUTE_DETAILS,
                arguments = listOf(navArgument(Routes.ROUTE_ID_ARG) { type = NavType.LongType }),
            ) { entry ->
                RouteDetailsScreen(
                    routeId = entry.arguments?.getLong(Routes.ROUTE_ID_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LOGIN) { LoginScreen() }
        }
    }
}
