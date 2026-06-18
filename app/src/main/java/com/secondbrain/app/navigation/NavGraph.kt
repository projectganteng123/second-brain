package com.secondbrain.app.navigation

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.google.gson.Gson
import com.secondbrain.app.data.model.Metadata
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.screens.*
import com.secondbrain.app.util.PrefsManager
import com.secondbrain.app.viewmodel.DashboardViewModel
import com.secondbrain.app.viewmodel.InputViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Input     : Screen("input")
    object Preview   : Screen("preview/{metadata}") {
        fun go(metadataJson: String) = "preview/${java.net.URLEncoder.encode(metadataJson, "UTF-8")}"
    }
    object Settings  : Screen("settings")
    object Search    : Screen("search")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    repo: NoteRepository,
    prefs: PrefsManager
) {
    val gson = remember { Gson() }
    val dashboardVm = remember { DashboardViewModel(repo) }
    // Shared across Input -> Preview so rawText & manual fields survive navigation
    val inputVm = remember { InputViewModel(repo, prefs.getApiKey()) }

    NavHost(navController, startDestination = Screen.Dashboard.route) {

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                vm = dashboardVm,
                repo = repo,
                onAddNote = { navController.navigate(Screen.Input.route) },
                onNoteClick = { /* TODO: detail screen */ },
                onSearchClick = { navController.navigate(Screen.Search.route) }
            )
        }

        composable(Screen.Input.route) {
            val uiState by inputVm.uiState.collectAsState()

            LaunchedEffect(uiState) {
                val state = uiState
                if (state is com.secondbrain.app.viewmodel.InputUiState.Preview) {
                    val json = java.net.URLEncoder.encode(gson.toJson(state.metadata), "UTF-8")
                    navController.navigate("preview/$json")
                }
            }

            InputScreen(
                vm = inputVm,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack(Screen.Dashboard.route, false) }
            )
        }

        composable(
            route = Screen.Preview.route,
            arguments = listOf(navArgument("metadata") { type = NavType.StringType })
        ) { back ->
            val encoded = back.arguments?.getString("metadata") ?: ""
            val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
            val metadata = runCatching { gson.fromJson(decoded, Metadata::class.java) }
                .getOrDefault(Metadata())

            PreviewScreen(
                vm = inputVm,
                metadata = metadata,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack(Screen.Dashboard.route, false) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Search.route) {
            SearchScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onNoteClick = { /* TODO */ }
            )
        }
    }
}
