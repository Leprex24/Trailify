package com.trailify.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Surface
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
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.feature.auth.ConsentGate
import com.trailify.feature.auth.LoginScreen
import com.trailify.feature.auth.PrivacyPolicyScreen
import com.trailify.feature.auth.RegisterScreen
import com.trailify.feature.community.CommunityScreen
import com.trailify.feature.community.publish.PublishRouteScreen
import com.trailify.feature.discover.DiscoverScreen
import com.trailify.feature.discover.RouteDetailsScreen
import com.trailify.feature.map.ActivitySummaryScreen
import com.trailify.feature.map.MapScreen
import com.trailify.feature.profile.AccountScreen
import com.trailify.feature.profile.ProfileScreen
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

    // Wylogowanie przenosi na ekran logowania, zalogowanie (lub rejestracja) z powrotem na mapę.
    val auth: AuthRepository = koinInject()
    val user by auth.currentUser.collectAsStateWithLifecycle()
    LaunchedEffect(user) {
        val current = navController.currentDestination?.route
        if (user == null && current !in Routes.LOGGED_OUT) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        } else if (user != null && current in Routes.SIGN_IN) {
            navController.navigate(Routes.MAP) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Zalogowany bez zgody RODO (np. nowe konto Google) najpierw widzi ekran zgody.
    ConsentGate {
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
                // Bez animacji przejść: domyślne zanikanie pokazywało mapę przez ekran profilu.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(Routes.DISCOVER) {
                    ScreenSurface { DiscoverScreen(onRouteClick = { navController.navigate(Routes.routeDetails(it)) }) }
                }
                composable(Routes.ROUTES) {
                    ScreenSurface { RoutesScreen(onRouteClick = { navController.navigate(Routes.routeDetails(it)) }) }
                }
                composable(Routes.MAP) {
                    MapScreen(onOpenSummary = { navController.navigate(Routes.ACTIVITY_SUMMARY) })
                }
                composable(Routes.ACTIVITY_SUMMARY) {
                    ScreenSurface {
                        ActivitySummaryScreen(
                            onDone = { navController.popBackStack() },
                            onPublish = { saved ->
                                navController.navigate(Routes.publishRoute(saved.id, saved.type.name)) {
                                    // podsumowanie zrobiło swoje, "Wróć" z formularza prowadzi na mapę
                                    popUpTo(Routes.ACTIVITY_SUMMARY) { inclusive = true }
                                }
                            },
                        )
                    }
                }
                composable(
                    route = Routes.PUBLISH_ROUTE,
                    arguments = listOf(
                        navArgument(Routes.ACTIVITY_ID_ARG) { type = NavType.LongType },
                        navArgument(Routes.ACTIVITY_TYPE_ARG) { type = NavType.StringType },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    ScreenSurface {
                        PublishRouteScreen(
                            activityId = args?.getLong(Routes.ACTIVITY_ID_ARG) ?: 0L,
                            activityType = ActivityType.valueOf(
                                args?.getString(Routes.ACTIVITY_TYPE_ARG) ?: ActivityType.WALKING.name,
                            ),
                            onPublished = { routeId ->
                                navController.navigate(Routes.routeDetails(routeId)) {
                                    popUpTo(Routes.PUBLISH_ROUTE) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.COMMUNITY) { ScreenSurface { CommunityScreen() } }
                composable(Routes.PROFILE) {
                    ScreenSurface { ProfileScreen(onOpenAccount = { navController.navigate(Routes.ACCOUNT) }) }
                }

                composable(
                    route = Routes.ROUTE_DETAILS,
                    arguments = listOf(navArgument(Routes.ROUTE_ID_ARG) { type = NavType.LongType }),
                ) { entry ->
                    ScreenSurface {
                        RouteDetailsScreen(
                            routeId = entry.arguments?.getLong(Routes.ROUTE_ID_ARG) ?: 0L,
                            onBack = { navController.popBackStack() },
                            onFollowRoute = {
                                // wracamy na START (pierwszy ekran stosu), mapa pokaże wybraną trasę
                                navController.navigate(Routes.MAP) {
                                    popUpTo(Routes.MAP)
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
                composable(Routes.LOGIN) {
                    ScreenSurface { LoginScreen(onGoToRegister = { navController.navigate(Routes.REGISTER) }) }
                }
                composable(Routes.REGISTER) {
                    ScreenSurface {
                        RegisterScreen(
                            onOpenPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.PRIVACY_POLICY) {
                    ScreenSurface { PrivacyPolicyScreen(onBack = { navController.popBackStack() }) }
                }
                composable(Routes.ACCOUNT) {
                    ScreenSurface {
                        AccountScreen(
                            onOpenPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Nieprzezroczyste tło dla każdego ekranu poza mapą. Bez niego w trakcie przejścia
 * przez ekran widać mapę, bo ekrany same nie rysują tła.
 */
@Composable
private fun ScreenSurface(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        content()
    }
}
