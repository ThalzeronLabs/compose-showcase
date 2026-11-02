package com.thalzeronlabs.shelf.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.thalzeronlabs.shelf.data.local.CachedBookEntity
import com.thalzeronlabs.shelf.data.local.SearchRemoteKey
import com.thalzeronlabs.shelf.data.local.SearchRow
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.remote.toBook
import com.thalzeronlabs.shelf.model.Clock
import retrofit2.HttpException
import java.io.IOException

/** The key a query's rows are stored under: trimmed and lowercase, so "Dune " and "dune" share results. */
fun searchKey(query: String) = query.trim().lowercase()

/**
 * Fills Room with Open Library results for one query, a page at a time. The UI never reads the network:
 * it pages through `search_cache`, and this mediator only writes to it.
 *
 * - **Refresh** fetches the first page and swaps it in for the old rows *in one transaction*, so a failed
 *   refresh leaves the cached results exactly as they were (that is what the user sees offline).
 * - **Append** fetches from the offset saved in `search_remote_keys`. The rows and the new offset are written
 *   together, so a connection that drops between pages loses nothing: the next attempt asks for the same page.
 * - A book Open Library returns twice (results can shift between pages) is kept once.
 */
@OptIn(ExperimentalPagingApi::class)
class SearchRemoteMediator(
    query: String,
    private val db: ShelfDatabase,
    private val api: OpenLibraryApi,
    private val clock: Clock,
) : RemoteMediator<Int, SearchRow>() {

    private val apiQuery = query.trim()
    private val key = searchKey(query)
    private val dao = db.searchCache()

    /** Show what is cached straight away and refresh it in the background. */
    override suspend fun initialize() = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(loadType: LoadType, state: PagingState<Int, SearchRow>): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            // Results only grow at the end.
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            // No key means no refresh has succeeded for this query yet (or the end was reached): nothing to append.
            LoadType.APPEND -> dao.remoteKey(key)?.nextOffset ?: return MediatorResult.Success(endOfPaginationReached = true)
        }
        val limit = state.config.pageSize

        val response = try {
            api.search(apiQuery, limit = limit, offset = offset)
        } catch (e: IOException) {
            return MediatorResult.Error(e)
        } catch (e: HttpException) {
            return MediatorResult.Error(e)
        }

        val nextOffset = offset + response.docs.size
        val end = response.docs.size < limit || nextOffset >= response.numFound
        val now = clock.now()
        db.withTransaction {
            if (loadType == LoadType.REFRESH) dao.clear(key)
            val seen = dao.ids(key).toHashSet()
            var position = dao.nextPosition(key)
            val rows = response.docs.mapNotNull { it.toBook() }
                .filter { seen.add(it.id) }
                .map { CachedBookEntity(key, position++, it.id, it.title, it.authors, it.year, it.coverUrl, now) }
            dao.insertAll(rows)
            dao.upsertRemoteKey(SearchRemoteKey(key, nextOffset = if (end) null else nextOffset))
        }
        return MediatorResult.Success(endOfPaginationReached = end)
    }
}
