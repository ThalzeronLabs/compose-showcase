package com.thalzeronlabs.shelf.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the app uses on start-up and in its main journey, so they are compiled
 * ahead of time on install instead of being interpreted and JIT-compiled on first use.
 *
 *   ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        startApp()
        searchScrollAndOpenShelf()
    }
}
