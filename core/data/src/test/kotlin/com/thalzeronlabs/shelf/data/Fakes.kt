package com.thalzeronlabs.shelf.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.remote.SearchResponse
import com.thalzeronlabs.shelf.data.sync.SyncRequester
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** One request the app made, as Open Library would have seen it. */
internal data class ApiRequest(val query: String, val limit: Int, val offset: Int)

/**
 * A fake Open Library that pages like the real one: [catalogue] holds the full result list per query and
 * every request gets the `limit` documents from `offset`. Queries of the form `key:("/works/…" OR …)` (what
 * the sync sends) are answered from [works].
 */
internal class FakeApi : OpenLibraryApi {
    val catalogue = mutableMapOf<String, List<DocDto>>()
    val works = mutableMapOf<String, DocDto>()
    val requests = mutableListOf<ApiRequest>()

    /** Return an error to throw for this request instead of answering it; null answers normally. */
    var failWith: (ApiRequest) -> Throwable? = { null }

    /** Runs while a request is "in flight", before the answer: lets a test change the database meanwhile. */
    var whileInFlight: suspend (ApiRequest) -> Unit = {}

    /** Drops every request from now on. */
    fun goOffline(error: Throwable = offline()) {
        failWith = { error }
    }

    fun goOnline() {
        failWith = { null }
    }

    override suspend fun search(query: String, limit: Int, offset: Int, fields: String): SearchResponse {
        val request = ApiRequest(query, limit, offset)
        requests += request
        whileInFlight(request)
        failWith(request)?.let { throw it }
        val all = if (query.startsWith("key:(")) {
            WORK_ID.findAll(query).mapNotNull { works[it.groupValues[1]] }.toList()
        } else {
            catalogue[query].orEmpty()
        }
        return SearchResponse(numFound = all.size, docs = all.drop(offset).take(limit))
    }

    private companion object {
        val WORK_ID = Regex("/works/([A-Za-z0-9]+)")
    }
}

internal class FakeSyncRequester : SyncRequester {
    var requests = 0
    override fun requestSync() {
        requests++
    }
}

internal fun offline() = IOException("Unable to resolve host openlibrary.org")
internal fun httpError(code: Int) = HttpException(Response.error<SearchResponse>(code, "".toResponseBody()))

/** `count` distinct works, "OL{prefix}{n}W", titled "{title} {n}". */
internal fun docs(count: Int, prefix: String = "", title: String = "Dune") = (1..count).map { n ->
    DocDto("/works/OL$prefix${n}W", "$title $n", listOf("Frank Herbert"), 1965 + n, n.toLong())
}

internal fun inMemoryDatabase(): ShelfDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShelfDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** A clock the test moves by hand. */
internal class TestClock(var now: Long = 1_000L) : com.thalzeronlabs.shelf.model.Clock {
    override fun now() = now
}
