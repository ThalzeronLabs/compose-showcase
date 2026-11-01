package com.thalzeronlabs.shelf.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.sheets.SheetOption
import com.thalzeronlabs.shelf.Book
import com.thalzeronlabs.shelf.ReadingStatus
import com.thalzeronlabs.shelf.ui.Badge
import com.thalzeronlabs.shelf.ui.BookRow
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(viewModel: SearchViewModel, sheets: SheetHostState) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    SearchContent(
        query = query,
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onBookClick = { book ->
            scope.launch {
                val status = sheets.choose(
                    book.title,
                    ReadingStatus.entries.map { SheetOption(it.label, it) },
                ) ?: return@launch
                viewModel.save(book, status)
            }
        },
    )
}

/** The stateless search screen: what the ViewModel exposes in, user intents out. Previews and screenshot tests use this. */
@Composable
fun SearchContent(
    query: String,
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onBookClick: (Book) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SearchField(query, onQueryChange)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                SearchState.Idle -> Hint("Search millions of books by title or author.")
                SearchState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SearchState.Empty -> Hint("Nothing found for \"${state.query}\".")
                is SearchState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
                is SearchState.Results -> Column {
                    if (state.savedAt != null) OfflineBanner(onRetry)
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.books, key = { it.id }) { book ->
                            BookRow(book, onClick = { onBookClick(book) }) {
                                Badge(book.status?.label ?: "+ Add", highlighted = book.status == null)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shown above cached results when Open Library could not be reached. */
@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "You're offline. Showing results saved on this device.",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TextButton(onClick = onRetry) { Text("Retry", color = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text("Search books", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            inner()
        },
    )
}

@Composable
private fun Hint(text: String) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center) {
        Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
    }
}
