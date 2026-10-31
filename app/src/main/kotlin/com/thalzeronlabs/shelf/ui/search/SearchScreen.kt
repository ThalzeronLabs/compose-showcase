package com.thalzeronlabs.shelf.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

    fun add(book: Book) = scope.launch {
        val status = sheets.choose(
            book.title,
            ReadingStatus.entries.map { SheetOption(it.label, it) },
        ) ?: return@launch
        viewModel.save(book, status)
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query, viewModel::onQueryChange)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                SearchState.Idle -> Hint("Search millions of books by title or author.")
                SearchState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SearchState.Empty -> Hint("Nothing found for \"${s.query}\".")
                is SearchState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::retry) { Text("Try again") }
                }
                is SearchState.Results -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.books, key = { it.id }) { book ->
                        BookRow(book, onClick = { add(book) }) {
                            Badge(book.status?.label ?: "+ Add", highlighted = book.status == null)
                        }
                    }
                }
            }
        }
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
