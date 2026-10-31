package com.omnituner.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.omnituner.android"

private const val WAIT_MS = 5_000L
private const val SETTLE_MS = 1_500L

/** Grants the microphone up front so the permission dialog never blocks the run. */
fun MacrobenchmarkScope.grantMicrophone() {
    device.executeShellCommand("pm grant $packageName android.permission.RECORD_AUDIO")
}

/** Waits for the bottom navigation, which means the first screen has drawn. */
fun MacrobenchmarkScope.waitForAppReady() {
    device.wait(Until.hasObject(By.text("Metronome")), WAIT_MS)
    device.waitForIdle()
}

/**
 * Visits every tab and uses its main action, so the code behind each one ends up
 * in the profile. Every step is skipped quietly if its button isn't found, so a
 * renamed label makes the profile thinner instead of failing the run.
 */
fun MacrobenchmarkScope.visitAllTabs() {
    // The Tuner opens first and starts listening.
    Thread.sleep(SETTLE_MS)

    openTab("Chords")
    clickText("Generate")
    Thread.sleep(SETTLE_MS)

    openTab("Scales")
    Thread.sleep(SETTLE_MS)

    openTab("Metronome")
    if (clickText("Start")) {
        Thread.sleep(SETTLE_MS * 2)
        clickText("Stop")
    }

    openTab("Tuner")
    Thread.sleep(SETTLE_MS)
}

private fun MacrobenchmarkScope.openTab(label: String) {
    clickText(label)
    device.waitForIdle()
}

private fun MacrobenchmarkScope.clickText(text: String): Boolean {
    val target = device.wait(Until.findObject(By.text(text)), WAIT_MS) ?: return false
    target.click()
    device.waitForIdle()
    return true
}
