package com.thalzeronlabs.shelf.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.thalzeronlabs.shelf.model.Book
import retrofit2.http.GET
import retrofit2.http.Query

/** The public Open Library search API (no key needed). */
interface OpenLibraryApi {
    /** One page of results: [limit] documents starting at [offset] (0-based) of the full result list. */
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = FIELDS,
    ): SearchResponse

    companion object {
        const val BASE_URL = "https://openlibrary.org/"
        const val PAGE_SIZE = 20
        const val FIELDS = "key,title,author_name,first_publish_year,cover_i"
        fun coverUrl(coverId: Long) = "https://covers.openlibrary.org/b/id/$coverId-M.jpg"

        /** A query matching exactly these works, so the sync can refresh many shelf books in one request. */
        fun worksQuery(ids: List<String>) = ids.joinToString(" OR ", prefix = "key:(", postfix = ")") { "\"/works/$it\"" }
    }
}

@JsonClass(generateAdapter = true)
data class SearchResponse(
    @param:Json(name = "numFound") val numFound: Int = 0,
    val docs: List<DocDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DocDto(
    val key: String,
    val title: String? = null,
    @param:Json(name = "author_name") val authorName: List<String>? = null,
    @param:Json(name = "first_publish_year") val firstPublishYear: Int? = null,
    @param:Json(name = "cover_i") val coverId: Long? = null,
)

/** Maps an API document to a [Book]; documents without a usable title are dropped (null). */
fun DocDto.toBook(): Book? {
    val title = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return Book(
        id = key.removePrefix("/works/"),
        title = title,
        authors = authorName?.take(2)?.joinToString(", ") ?: "Unknown author",
        year = firstPublishYear,
        coverUrl = coverId?.let(OpenLibraryApi::coverUrl),
    )
}
