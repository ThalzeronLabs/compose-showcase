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
    /**
     * "Author · year", as the lists show it under the title. The dot is held to the author by a no-break space,
     * so a large font size wraps before the year, never leaving the dot at the start of a line.
     */
    val byline: String get() = listOfNotNull(authors, year?.toString()).joinToString("\u00A0· ")

    /** The byline as a screen reader should say it: a pause instead of the middle dot. */
    val spokenByline: String get() = listOfNotNull(authors, year?.toString()).joinToString(", ")
}

enum class ThemeMode(val label: String) { SYSTEM("Follow the system"), LIGHT("Light"), DARK("Dark") }

/** The time source of the data layer, injected so tests can control it. */
fun interface Clock {
    fun now(): Long
}
