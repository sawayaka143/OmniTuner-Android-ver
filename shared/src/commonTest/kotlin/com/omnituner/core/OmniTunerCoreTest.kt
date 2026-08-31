package com.omnituner.core

import kotlin.test.Test
import kotlin.test.assertEquals

class OmniTunerCoreTest {
    @Test
    fun versionIsDefined() {
        assertEquals("0.1.0", OmniTunerCore.VERSION)
    }
}
