package com.example.app.presentation.navigation

object AppRoutes {
    const val HOME = "home"
    const val RULES = "rules"
    const val ADD_RULE = "addRule"
    const val PROFILE = "profile"
    const val CHAT = "chat"

    const val START = HOME

    fun isTab(route: String?): Boolean = when (route) {
        HOME, RULES, ADD_RULE, PROFILE -> true
        else -> false
    }

    fun showsBottomBar(route: String?): Boolean = isTab(route)
}
