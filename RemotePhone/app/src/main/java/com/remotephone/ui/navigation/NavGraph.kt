package com.remotephone.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.remotephone.data.firebase.FirebaseCommandRepo
import com.remotephone.data.firebase.FirebasePairingRepo
import com.remotephone.data.firebase.FirebaseSignalingRepo
import com.remotephone.ui.screen.CommandResultScreen
import com.remotephone.ui.screen.CommandsScreen
import com.remotephone.ui.screen.HomeScreen
import com.remotephone.ui.screen.LiveMirrorScreen
import com.remotephone.ui.screen.PairingScreen
import com.remotephone.ui.screen.SettingsScreen
import com.remotephone.ui.screen.SetupWizardScreen

/**
 * Central Compose Navigation Graph for RemotePhone app.
 *
 * Routes:
 * - "home" -> HomeScreen (Dashboard)
 * - "pairing" -> PairingScreen (Generate & Enter code)
 * - "mirror" -> LiveMirrorScreen (Full-screen WebRTC stream + touch control)
 * - "commands" -> CommandsScreen (Grid of remote commands)
 * - "command_result/{commandId}" -> CommandResultScreen (Screenshot/Notifs/Text viewer)
 * - "setup" -> SetupWizardScreen (Permissions and accessibility config)
 * - "settings" -> SettingsScreen (Quality, auto-reconnect, unpair)
 *
 * Start Destination: "home"
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    pairingRepo: FirebasePairingRepo? = null,
    commandRepo: FirebaseCommandRepo? = null,
    signalingRepo: FirebaseSignalingRepo? = null
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // 1. Home Dashboard
        composable(route = "home") {
            HomeScreen(
                navController = navController
            )
        }

        // 2. Pairing Screen
        composable(route = "pairing") {
            PairingScreen(
                navController = navController,
                pairingRepo = pairingRepo
            )
        }

        // 3. Live Screen Mirroring (Edge-to-edge full-screen)
        composable(route = "mirror") {
            LiveMirrorScreen(
                navController = navController,
                signalingRepo = signalingRepo,
                commandRepo = commandRepo,
                pairingRepo = pairingRepo
            )
        }

        // 4. Remote Commands Screen
        composable(route = "commands") {
            CommandsScreen(
                navController = navController,
                commandRepo = commandRepo,
                pairingRepo = pairingRepo
            )
        }

        // 5. Command Execution Result Screen
        composable(
            route = "command_result/{commandId}",
            arguments = listOf(
                navArgument("commandId") {
                    type = NavType.StringType
                    defaultValue = "latest"
                }
            )
        ) { backStackEntry ->
            val commandId = backStackEntry.arguments?.getString("commandId") ?: "latest"
            CommandResultScreen(
                commandId = commandId,
                navController = navController,
                commandRepo = commandRepo,
                pairingRepo = pairingRepo
            )
        }

        // 6. Setup Wizard Screen
        composable(route = "setup") {
            SetupWizardScreen(
                navController = navController
            )
        }

        // 7. Settings Screen
        composable(route = "settings") {
            SettingsScreen(
                navController = navController,
                pairingRepo = pairingRepo
            )
        }
    }
}
