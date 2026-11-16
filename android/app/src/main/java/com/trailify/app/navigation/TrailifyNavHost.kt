package com.trailify.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.PoiFocusController
import com.trailify.feature.auth.ConsentGate
import com.trailify.feature.auth.LoginScreen
import com.trailify.feature.auth.PrivacyPolicyScreen
import com.trailify.feature.auth.RegisterScreen
import com.trailify.feature.community.CommunityScreen
import com.trailify.feature.community.admin.AdminScreen
import com.trailify.feature.community.feed.UserSearchScreen
import com.trailify.feature.community.poi.PoiFormScreen
import com.trailify.feature.community.publish.PublishRouteScreen
import com.trailify.feature.discover.DiscoverScreen
import com.trailify.feature.discover.RouteDetailsScreen
import com.trailify.feature.map.ActivitySummaryScreen
import com.trailify.feature.map.MapScreen
import com.trailify.feature.profile.AccountScreen
import com.trailify.feature.profile.ActivityDetailsScreen
import com.trailify.feature.profile.OtherProfileScreen
import com.trailify.feature.profile.ProfileScreen
import com.trailify.feature.profile.UserListKind
import com.trailify.feature.profile.UserListScreen
import com.trailify.feature.routes.RoutesScreen
import org.koin.compose.koinInject
import androidx.compose.material3.MaterialTheme

/** Czas zsuwania ekranu przy cofaniu. */
private const val BACK_ANIMATION_MS = 220

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

    val openTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Routes.MAP) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // "Zobacz na mapie" przy ciekawym punkcie (profil, oś czasu): mapa START przechodzi do tego punktu.
    val poiFocus: PoiFocusController = koinInject()
    val showPoiOnMap: (Poi) -> Unit = { poi ->
        poiFocus.focus(poi)
        openTopLevel(Routes.MAP)
    }

    // Dymek nad paskiem na mapie: przycisk START w pasku go pokazuje i chowa.
    var startPanelVisible by rememberSaveable { mutableStateOf(true) }

    // Zalogowany bez zgody RODO (np. nowe konto Google) najpierw widzi ekran zgody.
    ConsentGate {
        // Box: przycisk START leży nad Scaffoldem, bo wystaje poza pasek i musi przyjmować dotyk
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) TrailifyBottomBar(currentRoute, openTopLevel)
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.MAP,
                modifier = Modifier.padding(innerPadding),
                // Wejście bez animacji: domyślne zanikanie pokazywało mapę przez ekran profilu.
                // Cofanie (przyciskiem i gestem) zsuwa bieżący ekran w prawo, bez zmniejszania i bez zanikania,
                // a ekran pod spodem jest już na swoim miejscu.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutHorizontally(animationSpec = tween(BACK_ANIMATION_MS)) { it } },
                predictivePopEnterTransition = { EnterTransition.None },
                predictivePopExitTransition = { slideOutHorizontally(animationSpec = tween(BACK_ANIMATION_MS)) { it } },
            ) {
                composable(Routes.DISCOVER) {
                    ScreenSurface { DiscoverScreen(onRouteClick = { navController.navigate(Routes.routeDetails(it)) }) }
                }
                composable(Routes.ROUTES) {
                    ScreenSurface {
                        RoutesScreen(
                            onRouteClick = { navController.navigate(Routes.routeDetails(it)) },
                            // "+" prowadzi na mapę, gdzie można zaplanować trasę (dymek START jest wtedy pokazany)
                            onAddRoute = {
                                startPanelVisible = true
                                openTopLevel(Routes.MAP)
                            },
                        )
                    }
                }
                composable(Routes.MAP) {
                    MapScreen(
                        onOpenSummary = { navController.navigate(Routes.ACTIVITY_SUMMARY) },
                        onProposePoi = { lat, lng -> navController.navigate(Routes.proposePoiAt(lat, lng)) },
                        panelVisible = startPanelVisible,
                    )
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
                composable(Routes.COMMUNITY) {
                    ScreenSurface {
                        CommunityScreen(
                            onSearchUsers = { navController.navigate(Routes.USER_SEARCH) },
                            onOpenUser = { navController.navigate(Routes.userProfile(it)) },
                            onOpenRoute = { navController.navigate(Routes.routeDetails(it)) },
                            onOpenActivity = { navController.navigate(Routes.otherActivityDetails(it)) },
                            onShowPoiOnMap = showPoiOnMap,
                        )
                    }
                }
                composable(Routes.USER_SEARCH) {
                    ScreenSurface {
                        UserSearchScreen(
                            onBack = { navController.popBackStack() },
                            onOpenUser = { navController.navigate(Routes.userProfile(it)) },
                        )
                    }
                }
                composable(
                    route = Routes.PROPOSE_POI_ROUTE,
                    arguments = listOf(
                        navArgument(Routes.POI_LAT_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(Routes.POI_LNG_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val lat = entry.arguments?.getString(Routes.POI_LAT_ARG)?.toDoubleOrNull()
                    val lng = entry.arguments?.getString(Routes.POI_LNG_ARG)?.toDoubleOrNull()
                    ScreenSurface {
                        PoiFormScreen(
                            asAdmin = false,
                            initialPosition = if (lat != null && lng != null) LatLng(lat, lng) else null,
                            onDone = { navController.popBackStack() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.PROFILE) {
                    ScreenSurface {
                        ProfileScreen(
                            onOpenSettings = { navController.navigate(Routes.ACCOUNT) },
                            onOpenActivity = { navController.navigate(Routes.activityDetails(it)) },
                            onProposePoi = { navController.navigate(Routes.PROPOSE_POI) },
                            onOpenFollowers = { navController.navigate(Routes.userList(it, UserListKind.FOLLOWERS.route)) },
                            onOpenFollowing = { navController.navigate(Routes.userList(it, UserListKind.FOLLOWING.route)) },
                            onShowPoiOnMap = showPoiOnMap,
                        )
                    }
                }
                composable(
                    route = Routes.USER_PROFILE,
                    arguments = listOf(navArgument(Routes.USER_ID_ARG) { type = NavType.StringType }),
                ) { entry ->
                    val userId = entry.arguments?.getString(Routes.USER_ID_ARG).orEmpty()
                    ScreenSurface {
                        OtherProfileScreen(
                            userId = userId,
                            onBack = { navController.popBackStack() },
                            onOpenFollowers = { navController.navigate(Routes.userList(it, UserListKind.FOLLOWERS.route)) },
                            onOpenFollowing = { navController.navigate(Routes.userList(it, UserListKind.FOLLOWING.route)) },
                            onOpenRoute = { navController.navigate(Routes.routeDetails(it)) },
                            onOpenActivity = { navController.navigate(Routes.otherActivityDetails(it)) },
                            onShowPoiOnMap = showPoiOnMap,
                        )
                    }
                }
                composable(
                    route = Routes.USER_LIST,
                    arguments = listOf(
                        navArgument(Routes.USER_ID_ARG) { type = NavType.StringType },
                        navArgument(Routes.USER_LIST_KIND_ARG) { type = NavType.StringType },
                    ),
                ) { entry ->
                    ScreenSurface {
                        UserListScreen(
                            userId = entry.arguments?.getString(Routes.USER_ID_ARG).orEmpty(),
                            kind = UserListKind.fromRoute(entry.arguments?.getString(Routes.USER_LIST_KIND_ARG)),
                            onBack = { navController.popBackStack() },
                            onOpenUser = { navController.navigate(Routes.userProfile(it)) },
                        )
                    }
                }
                composable(
                    route = Routes.ACTIVITY_DETAILS,
                    arguments = listOf(
                        navArgument(Routes.ACTIVITY_ID_ARG) { type = NavType.LongType },
                        navArgument(Routes.ACTIVITY_OWN_ARG) { type = NavType.BoolType; defaultValue = true },
                    ),
                ) { entry ->
                    ScreenSurface {
                        ActivityDetailsScreen(
                            activityId = entry.arguments?.getLong(Routes.ACTIVITY_ID_ARG) ?: 0L,
                            isOwn = entry.arguments?.getBoolean(Routes.ACTIVITY_OWN_ARG) ?: true,
                            onPublish = { navController.navigate(Routes.publishRoute(it.id, it.type.name)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.ADMIN) {
                    ScreenSurface {
                        AdminScreen(onAddPoi = { navController.navigate(Routes.ADMIN_ADD_POI) }, onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.ADMIN_ADD_POI) {
                    ScreenSurface {
                        PoiFormScreen(asAdmin = true, onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() })
                    }
                }

                composable(
                    route = Routes.ROUTE_DETAILS,
                    arguments = listOf(navArgument(Routes.ROUTE_ID_ARG) { type = NavType.LongType }),
                ) { entry ->
                    ScreenSurface {
                        RouteDetailsScreen(
                            routeId = entry.arguments?.getLong(Routes.ROUTE_ID_ARG) ?: 0L,
                            onBack = { navController.popBackStack() },
                            onOpenUser = { navController.navigate(Routes.userProfile(it)) },
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
                            onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
        if (showBottomBar) {
            StartButton(
                onClick = {
                    if (currentRoute == Routes.MAP) {
                        startPanelVisible = !startPanelVisible
                    } else {
                        startPanelVisible = true
                        openTopLevel(Routes.MAP)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = StartBottomPadding),
            )
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
