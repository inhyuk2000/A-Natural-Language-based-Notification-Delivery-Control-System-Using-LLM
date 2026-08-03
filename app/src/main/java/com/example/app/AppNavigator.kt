package com.example.app

import android.app.Activity
import android.content.Intent
import com.example.app.presentation.navigation.AppRoutes

/** Bridges legacy Activity callers into MainActivity NavHost routes. */
object AppNavigator {
    fun openHome(from: Activity) = openRoute(from, AppRoutes.HOME)

    fun openRules(from: Activity) = openRoute(from, AppRoutes.RULES)

    fun openAddRule(from: Activity) = openRoute(from, AppRoutes.ADD_RULE)

    fun openProfile(from: Activity) = openRoute(from, AppRoutes.PROFILE)

    fun openChat(from: Activity) = openRoute(from, AppRoutes.CHAT)

    private fun openRoute(from: Activity, route: String) {
        if (from is MainActivity) {
            from.navigateInternally(route)
            return
        }
        from.startActivity(
            Intent(from, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAV_ROUTE, route)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        from.finish()
    }
}
