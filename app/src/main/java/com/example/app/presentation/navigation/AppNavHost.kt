package com.example.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.app.ContextManagerEntry
import com.example.app.HomeDashboardData
import com.example.app.presentation.screens.AppBottomBar
import com.example.app.presentation.screens.BottomNavTab

/** FadeThrough에 가깝게 — 채팅 진입이 새 페이지처럼 밀리지 않도록 */
private const val FadeThroughMs = 260

@Composable
fun AppShell(
    navController: NavHostController,
    userName: String,
    avatarPath: String?,
    serviceOn: Boolean,
    dashboard: HomeDashboardData,
    homeRules: List<ContextManagerEntry>,
    rulesRefreshKey: Int,
    onToggleService: () -> Unit,
    onRefreshHome: () -> Unit,
    onRulesChanged: () -> Unit,
    onProfileUpdated: (name: String, avatarPath: String?) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = AppRoutes.showsBottomBar(currentRoute)

    Box(modifier = Modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            userName = userName,
            avatarPath = avatarPath,
            serviceOn = serviceOn,
            dashboard = dashboard,
            homeRules = homeRules,
            rulesRefreshKey = rulesRefreshKey,
            onToggleService = onToggleService,
            onRefreshHome = onRefreshHome,
            onRulesChanged = onRulesChanged,
            onProfileUpdated = onProfileUpdated,
            onLoggedOut = onLoggedOut,
            modifier = Modifier.fillMaxSize(),
        )

        if (showBottomBar) {
            AppBottomBar(
                selected = when (currentRoute) {
                    AppRoutes.RULES -> BottomNavTab.RULES
                    AppRoutes.ADD_RULE -> BottomNavTab.ADD_RULE
                    AppRoutes.PROFILE -> BottomNavTab.PROFILE
                    else -> BottomNavTab.HOME
                },
                onHome = { navController.navigateTab(AppRoutes.HOME) },
                onRules = { navController.navigateTab(AppRoutes.RULES) },
                onAddRule = { navController.navigateTab(AppRoutes.ADD_RULE) },
                onProfile = { navController.navigateTab(AppRoutes.PROFILE) },
                onChat = { navController.navigateChat() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    userName: String,
    avatarPath: String?,
    serviceOn: Boolean,
    dashboard: HomeDashboardData,
    homeRules: List<ContextManagerEntry>,
    rulesRefreshKey: Int,
    onToggleService: () -> Unit,
    onRefreshHome: () -> Unit,
    onRulesChanged: () -> Unit,
    onProfileUpdated: (name: String, avatarPath: String?) -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.START,
        modifier = modifier,
        enterTransition = {
            // 탭·채팅 모두 fadeThrough — Activity 재생성/슬라이드 페이지 전환 느낌 제거
            fadeIn(tween(FadeThroughMs))
        },
        exitTransition = {
            fadeOut(tween(FadeThroughMs))
        },
        popEnterTransition = {
            fadeIn(tween(FadeThroughMs))
        },
        popExitTransition = {
            fadeOut(tween(FadeThroughMs))
        },
    ) {
        composable(AppRoutes.HOME) {
            HomeRoute(
                userName = userName,
                avatarPath = avatarPath,
                serviceOn = serviceOn,
                dashboard = dashboard,
                rules = homeRules,
                onToggleService = onToggleService,
                onOpenRules = { navController.navigateTab(AppRoutes.RULES) },
                onOpenAddRule = { navController.navigateTab(AppRoutes.ADD_RULE) },
                onOpenProfile = { navController.navigateTab(AppRoutes.PROFILE) },
                onRefresh = onRefreshHome,
            )
        }
        composable(AppRoutes.RULES) {
            RulesRoute(
                refreshKey = rulesRefreshKey,
                onRulesChanged = {
                    onRulesChanged()
                    onRefreshHome()
                },
            )
        }
        composable(AppRoutes.ADD_RULE) {
            AddRuleRoute(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigateTab(AppRoutes.RULES)
                    }
                },
                onRegistered = {
                    onRulesChanged()
                    onRefreshHome()
                    navController.navigateTab(AppRoutes.RULES)
                },
            )
        }
        composable(AppRoutes.PROFILE) {
            ProfileRoute(
                onLoggedOut = onLoggedOut,
                onBack = { navController.navigateTab(AppRoutes.HOME) },
                onProfileUpdated = onProfileUpdated,
            )
        }
        composable(AppRoutes.CHAT) {
            ChatRoute(onBack = { navController.popBackStack() })
        }
    }
}
