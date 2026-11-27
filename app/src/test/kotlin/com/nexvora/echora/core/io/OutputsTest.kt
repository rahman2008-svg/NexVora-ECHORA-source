package com.nexvora.echora.core.io

import com.nexvora.echora.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputsTest {
    @Test fun destinationDisplayUsesTheSameFolderAsPublication() {
        assertEquals("Music/${Outputs.SUBDIR}", Outputs.musicDirectoryDisplay)
    }

    @Test fun destinationDisplayPreservesProductionAndDevtestIsolation() {
        val expected = if (BuildConfig.IS_DEVTEST) "Music/ECHORA DEV TEST" else "Music/ECHORA"
        assertEquals(expected, Outputs.musicDirectoryDisplay)
    }
}
