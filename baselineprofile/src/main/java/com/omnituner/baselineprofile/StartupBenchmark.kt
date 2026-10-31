package com.omnituner.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold start and tab switching with and without the baseline profile,
 * so the gain shows up as numbers. Run from the gutter icons in Android Studio
 * against the "benchmarkRelease" variant; results land in the Run window.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun tabsWithoutProfile() = tabs(CompilationMode.None())

    @Test
    fun tabsWithProfile() = tabs(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            grantMicrophone()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForAppReady()
    }

    // No startupMode here: a cold start kills the app after setupBlock opens it,
    // which leaves the tab walk with no app to measure.
    private fun tabs(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = null,
        iterations = 5,
        setupBlock = {
            grantMicrophone()
            pressHome()
            startActivityAndWait()
            waitForAppReady()
        },
    ) {
        visitAllTabs()
    }
}
