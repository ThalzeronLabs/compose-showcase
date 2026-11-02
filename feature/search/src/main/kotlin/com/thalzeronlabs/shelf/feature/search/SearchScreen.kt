package com.thalzeronlabs.shelf.feature.search

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.sheets.SheetOption
import com.thalzeronlabs.shelf.model.Book
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.ui.Badge
import com.thalzeronlabs.shelf.ui.BookRow
import kotlinx.coroutines.launch

/** Test tags, also exposed as resource ids so the Macrobenchmark journeys can find them. */
object SearchTags {
    const val FIELD = "search_field"
    const val RESULTS = "search_results"
}

@Composable
fun SearchScreen(viewModel: SearchViewModel, sheets: SheetHostState) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val activeQuery by viewModel.activeQuery.collectAsStateWithLifecycle()
    val items = viewModel.results.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()

    SearchContent(
        query = query,
        status = searchStatus(activeQuery, items.loadState, items.itemCount),
        items = items,
        onQueryChange = viewModel::onQueryChange,
        onRetry = items::retry,
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

/**
 * The stateless search screen: the status and the paged rows in, user intents out. Previews and screenshot
 * tests use this with a fixed `PagingData`.
 */
@Composable
fun SearchContent(
    query: String,
    status: SearchStatus,
    items: LazyPagingItems<Book>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onBookClick: (Book) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SearchField(query, onQueryChange)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (status) {
                SearchStatus.Idle -> Hint("Search millions of books by title or author.")
                SearchStatus.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).semantics { contentDescription = "Searching" },
                )
                is SearchStatus.Empty -> Hint("Nothing found for \"${status.query}\".", announce = true)
                is SearchStatus.Error -> Column(
                    // Announced when it appears, so a screen reader user knows the search failed.
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(status.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
                is SearchStatus.Results -> Column {
                    when {
                        status.refreshFailed -> SavedResultsBanner(onRetry)
                        status.refreshing -> LinearProgressIndicator(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .semantics { contentDescription = "Refreshing results" },
                        )
                    }
                    ResultList(items, onBookClick, onRetry)
                }
            }
        }
    }
}

@Composable
private fun ResultList(items: LazyPagingItems<Book>, onBookClick: (Book) -> Unit, onRetry: () -> Unit) {
    LazyColumn(
        Modifier.testTag(SearchTags.RESULTS),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            count = items.itemCount,
            // Ids are unique per query (the mediator drops repeats), so rows keep their state while pages load.
            key = items.itemKey { it.id },
            contentType = items.itemContentType { "book" },
        ) { index ->
            val book = items[index] ?: return@items
            BookRow(
                book.title,
                book.byline,
                book.coverUrl,
                onClick = { onBookClick(book) },
                onClickLabel = if (book.status == null) "add to your shelf" else "change its list",
                stateDescription = book.status?.label ?: "Not on your shelf",
                subtitleDescription = book.spokenByline,
            ) {
                // The row's state already says this; the reader would otherwise read "plus Add".
                Badge(
                    book.status?.label ?: "+ Add",
                    highlighted = book.status == null,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        // The next page: a spinner while it loads, a retry if the connection dropped on the way.
        when (items.loadState.append) {
            is LoadState.Loading -> item(contentType = "footer") {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        Modifier.size(28.dp).semantics { contentDescription = "Loading more results" },
                    )
                }
            }
            is LoadState.Error -> item(contentType = "footer") { LoadMoreError(onRetry) }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun LoadMoreError(onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Couldn't load more results.",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/** Shown above the results when they could not be refreshed, so these are the ones saved on this device. */
@Composable
private fun SavedResultsBanner(onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Couldn't refresh. Showing results saved on this device.",
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
            .testTag(SearchTags.FIELD)
            // The placeholder is drawn, not announced: this is what a screen reader calls the field.
            .semantics { contentDescription = "Search books" }
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
private fun Hint(text: String, announce: Boolean = false) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp)
            .semantics(mergeDescendants = announce) { if (announce) liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))
    }
}
