package com.secondbrain.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.viewmodel.InputViewModel
import kotlinx.coroutines.launch

/**
 * Layar awal aplikasi: pager dua halaman.
 *  - Halaman 1 (default) = Input catatan.
 *  - Halaman 0 (geser ke KANAN dari Input, atau tombol hamburger) = daftar semua catatan
 *    + pintasan Acara/Keuangan/Tanya AI/Pengaturan. Geser ke kiri untuk kembali menulis.
 */
@Composable
fun HomePagerScreen(
    repo: NoteRepository,
    inputVm: InputViewModel,
    onNoteClick: (Long) -> Unit,
    onOpenEvents: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenQa: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaved: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 1) { 2 }
    val scope = rememberCoroutineScope()

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            NotesHomePage(
                repo = repo,
                onNoteClick = onNoteClick,
                onOpenEvents = onOpenEvents,
                onOpenFinance = onOpenFinance,
                onOpenQa = onOpenQa,
                onOpenSettings = onOpenSettings,
                onBackToInput = { scope.launch { pagerState.animateScrollToPage(1) } }
            )
        } else {
            InputScreen(
                vm = inputVm,
                onOpenList = { scope.launch { pagerState.animateScrollToPage(0) } },
                onAsk = onOpenQa,
                onSaved = onSaved
            )
        }
    }
}
