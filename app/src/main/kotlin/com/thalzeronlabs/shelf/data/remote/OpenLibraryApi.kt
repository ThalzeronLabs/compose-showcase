package com.thalzeronlabs.shelf.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/** The public Open Library search API (no key needed). */
interface OpenLibraryApi {
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i",
    ): SearchResponse

    companion object {
        const val BASE_URL = "https://openlibrary.org/"
        fun coverUrl(coverId: Long) = "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
    }
}

@JsonClass(generateAdapter = true)
data class SearchResponse(val docs: List<DocDto> = emptyList())

@JsonClass(generateAdapter = true)
data class DocDto(
    val key: String,
    val title: String? = null,
    @Json(name = "author_name") val authorName: List<String>? = null,
    @Json(name = "first_publish_year") val firstPublishYear: Int? = null,
    @Json(name = "cover_i") val coverId: Long? = null,
)
