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
import com.secondbrain.app.viewmodel.NoteDetailViewModel
import com.secondbrain.app.viewmodel.QaViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Input     : Screen("input")
    object Preview   : Screen("preview/{metadata}") {
        fun go(metadataJson: String) = "preview/${java.net.URLEncoder.encode(metadataJson, "UTF-8")}"
    }
    object Settings  : Screen("settings")
    object Search    : Screen("search")
    object Qa        : Screen("qa")
    object Debug     : Screen("debug")
    object AllNotes  : Screen("allnotes")
    object Archive   : Screen("archive")
    object ActionItems : Screen("actionitems")
    object Detail    : Screen("detail/{noteId}") {
        fun go(noteId: Long) = "detail/$noteId"
    }
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
    val inputVm = remember { InputViewModel(repo, prefs) }

    NavHost(navController, startDestination = Screen.Dashboard.route) {

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                vm = dashboardVm,
                repo = repo,
                onAddNote = { navController.navigate(Screen.Input.route) },
                onNoteClick = { id -> navController.navigate(Screen.Detail.go(id)) },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onAskClick = { navController.navigate(Screen.Qa.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onAllNotesClick = { navController.navigate(Screen.AllNotes.route) }
            )
        }

        composable(Screen.AllNotes.route) {
            AllNotesScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onNoteClick = { id -> navController.navigate(Screen.Detail.go(id)) }
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
            SettingsScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onOpenDebug = { navController.navigate(Screen.Debug.route) },
                onOpenArchive = { navController.navigate(Screen.Archive.route) },
                onOpenActionItems = { navController.navigate(Screen.ActionItems.route) }
            )
        }

        composable(Screen.Debug.route) {
            DebugScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Archive.route) {
            ArchiveScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onNoteClick = { id -> navController.navigate(Screen.Detail.go(id)) }
            )
        }

        composable(Screen.ActionItems.route) {
            ActionItemsScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onNoteClick = { id -> navController.navigate(Screen.Detail.go(id)) }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onNoteClick = { id -> navController.navigate(Screen.Detail.go(id)) }
            )
        }

        composable(Screen.Qa.route) {
            val qaVm = remember { QaViewModel(repo, prefs) }
            QaScreen(
                vm = qaVm,
                onBack = { navController.popBackStack() },
                onSourceClick = { id -> navController.navigate(Screen.Detail.go(id)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { back ->
            val noteId = back.arguments?.getLong("noteId") ?: -1L
            val detailVm = remember(noteId) { NoteDetailViewModel(repo, prefs) }
            NoteDetailScreen(
                vm = detailVm,
                noteId = noteId,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack(Screen.Dashboard.route, false) }
            )
        }
    }
}
