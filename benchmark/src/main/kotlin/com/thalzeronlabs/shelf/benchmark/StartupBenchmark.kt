package com.thalzeronlabs.shelf.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start-up time of the release build (R8, signed), with and without the Baseline Profile.
 * [StartupTimingMetric] reports `timeToInitialDisplayMs` (the first frame) for every iteration.
 *
 *   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
 *
 * Numbers only mean something on a real device with a stable temperature and nothing else running;
 * emulator results are useful to compare the two modes with each other, not as absolute times.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Everything interpreted or JIT-compiled: what a sideloaded APK gets without a profile. */
    @Test
    fun startupWithoutBaselineProfile() = startup(CompilationMode.None())

    /** The Baseline Profile compiled ahead of time, as profileinstaller or Play would install it. */
    @Test
    fun startupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startApp()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
