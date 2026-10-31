package com.omnituner.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile (written to app/src/release/generated/baselineProfiles).
 * Needs a connected emulator or phone on API 33+, or a rooted one on API 28-32.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        grantMicrophone()
        pressHome()
        startActivityAndWait()
        waitForAppReady()
        visitAllTabs()
    }
}
