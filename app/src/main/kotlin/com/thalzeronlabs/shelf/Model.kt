package com.thalzeronlabs.shelf

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
)

enum class ThemeMode(val label: String) { SYSTEM("Follow the system"), LIGHT("Light"), DARK("Dark") }
