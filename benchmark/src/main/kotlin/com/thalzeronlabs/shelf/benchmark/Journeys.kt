package com.thalzeronlabs.shelf.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "com.thalzeronlabs.shelf"

/** Cold start to the first frame of the search screen. */
fun MacrobenchmarkScope.startApp() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.res("search_field")), 5_000)
}

/**
 * The journey users take most: search, scroll through the results (which pages more in), open the shelf.
 * The search needs the network; without it the journey still covers the search and shelf screens.
 */
fun MacrobenchmarkScope.searchScrollAndOpenShelf() {
    device.findObject(By.res("search_field"))?.text = "dune"
    val results = device.wait(Until.findObject(By.res("search_results")), 15_000)
    if (results != null) {
        results.setGestureMargin(device.displayWidth / 5)
        repeat(3) { results.fling(Direction.DOWN) }
        device.waitForIdle()
    }
    device.findObject(By.text("My shelf"))?.click()
    device.waitForIdle()
    device.findObject(By.text("Search"))?.click()
    device.waitForIdle()
}
