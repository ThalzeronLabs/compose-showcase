package com.thalzeronlabs.shelf.ui.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.sheets.SheetOption
import com.thalzeronlabs.shelf.Book
import com.thalzeronlabs.shelf.ReadingStatus
import com.thalzeronlabs.shelf.ui.Badge
import com.thalzeronlabs.shelf.ui.BookRow
import kotlinx.coroutines.launch

/** What a tap on a shelf book can do. */
private sealed interface BookAction {
    data class Move(val status: ReadingStatus) : BookAction
    data object Remove : BookAction
}

@Composable
fun ShelfScreen(viewModel: ShelfViewModel, sheets: SheetHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    fun manage(book: Book) = scope.launch {
        val options = ReadingStatus.entries.filter { it != book.status }.map { SheetOption<BookAction>("Move to ${it.label}", BookAction.Move(it)) } +
            SheetOption<BookAction>("Remove from shelf", BookAction.Remove, destructive = true)
        when (val action = sheets.choose<BookAction>(book.title, options)) {
            is BookAction.Move -> viewModel.setStatus(book, action.status)
            BookAction.Remove -> if (sheets.confirm("Remove \"${book.title}\"?", "It leaves your shelf on this device.", "Remove", destructive = true)) {
                viewModel.remove(book)
            }
            null -> Unit
        }
    }

    if (!state.loading && state.total == 0) {
        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Text(
                "Your shelf is empty.\nFind a book in Search and add it here.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.sections.forEach { (status, books) ->
            item(key = status.name) {
                Text(
                    "${status.label} · ${books.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(books, key = { it.id }) { book ->
                BookRow(book, onClick = { manage(book) }) {
                    Badge(status.label, highlighted = status == ReadingStatus.READING)
                }
            }
        }
    }
}
