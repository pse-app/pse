package com.pse_app.client.ui

import android.content.Context
import android.content.Intent
import androidx.navigation.NavController
import com.pse_app.client.annotations.KoverIgnore
import com.pse_app.client.ui.view.Login
import com.pse_app.client.ui.view.MainMenu

/**
 * Describes a navigation event.
 */
sealed interface NavigationEvent {
    data object DefaultBack: NavigationEvent
    data class SimpleNav(val route: NavigationRoute): NavigationEvent
    data class BackSkipNav(val route: NavigationRoute): NavigationEvent
    data class ViaPop(val destination: NavigationRoute, val via: NavigationRoute): NavigationEvent
    data class CloseThenNav(val here: NavigationRoute, val destination: NavigationRoute):
        NavigationEvent
    data object ResetNav: NavigationEvent
    data object DefaultRaiseLogin: NavigationEvent
    data object ReturnToMainMenu: NavigationEvent
    data object CloseApp: NavigationEvent

    @KoverIgnore("UI code")
    fun handle(
        navController: NavController,
        context: Context,
    ) = when (this) {
        is ResetNav -> navController.navigate(Login) {
            popUpTo(0)
            launchSingleTop = true
        }
        is BackSkipNav -> navController.navigate(route) {
            popUpTo(route) {
                inclusive = true
            }
        }
        is DefaultBack -> {
            if (!navController.popBackStack()) navController.navigate(MainMenu) {
                popUpTo(0)
                launchSingleTop = true
            }
            Unit
        }
        is SimpleNav -> navController.navigate(route)
        is ViaPop -> navController.navigate(destination) {
            popUpTo(via) {
                inclusive = false
            }
        }
        is DefaultRaiseLogin -> navController.navigate(Login) {
            launchSingleTop = true
            restoreState = true
        }
        is CloseThenNav -> navController.navigate(destination) {
            popUpTo(here) {
                inclusive = true
            }
        }
        is ReturnToMainMenu -> navController.navigate(MainMenu) {
            popUpTo<MainMenu> {
                inclusive = true
            }
            launchSingleTop = true
        }
        is CloseApp -> Intent(Intent.ACTION_MAIN)
            .apply { addCategory(Intent.CATEGORY_HOME) }
            .let(context::startActivity)
    }
}

/**
 * Represents a route that can be navigated to.
 */
interface NavigationRoute
