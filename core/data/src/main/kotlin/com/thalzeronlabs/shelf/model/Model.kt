package com.thalzeronlabs.shelf.model

/** Where a book sits on the user's shelf. */
enum class ReadingStatus(val label: String) {
    WANT("Want to read"),
    READING("Reading"),
    DONE("Finished"),
}

/** A book as the UI sees it, whether it came from the search API or from the local shelf. */
data class Book(
    val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val coverUrl: String?,
    val status: ReadingStatus? = null,
) {
    /** "Author · year", as the lists show it under the title. */
    val byline: String get() = listOfNotNull(authors, year?.toString()).joinToString(" · ")
}

enum class ThemeMode(val label: String) { SYSTEM("Follow the system"), LIGHT("Light"), DARK("Dark") }

/** The time source of the data layer, injected so tests can control it. */
fun interface Clock {
    fun now(): Long
}
