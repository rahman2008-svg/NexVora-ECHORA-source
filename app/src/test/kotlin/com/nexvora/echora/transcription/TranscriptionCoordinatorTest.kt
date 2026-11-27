package com.nexvora.echora.transcription

import com.nexvora.echora.settings.AiMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

private class Fake(override val id: String, override val isOnline: Boolean, var a: Availability) : TranscriptionProvider {
    override suspend fun availability() = a
    override suspend fun transcribe(request: TranscribeRequest, onProgress: (Int) -> Unit): TranscriptResult = error("not used")
}

class TranscriptionCoordinatorTest {
    private val ready = Availability.Ready
    private fun coord(off: Availability, on: Availability) = Fake("offline", false, off).let { o -> Fake("online", true, on).let { n -> Triple(TranscriptionCoordinator(o, n), o, n) } }

    @Test fun offlineModeNeverFallsBackToOnline() = runBlocking {
        val (c, _, _) = coord(Availability.Unavailable("no model"), ready)
        assertTrue(c.resolve(AiMode.OFFLINE, consentGranted = true) is Resolution.NeedModel)
    }
    @Test fun offlineReadyUsesOffline() = runBlocking {
        val (c, o, _) = coord(ready, ready)
        assertSame(o, (c.resolve(AiMode.OFFLINE, false) as Resolution.Use).provider)
    }
    @Test fun onlineRequiresConsent() = runBlocking {
        val (c, _, _) = coord(ready, ready)
        assertEquals(Resolution.NeedConsent, c.resolve(AiMode.ONLINE, consentGranted = false))
    }
    @Test fun onlineWithConsentAndConfig() = runBlocking {
        val (c, _, n) = coord(ready, ready)
        assertSame(n, (c.resolve(AiMode.ONLINE, true) as Resolution.Use).provider)
    }
    @Test fun onlineUnconfiguredIsUnavailable() = runBlocking {
        val (c, _, _) = coord(ready, Availability.Unavailable("no key"))
        assertTrue(c.resolve(AiMode.ONLINE, true) is Resolution.Unavailable)
    }
    @Test fun autoPrefersOffline() = runBlocking {
        val (c, o, _) = coord(ready, ready)
        assertSame(o, (c.resolve(AiMode.AUTO, true) as Resolution.Use).provider)
    }
    @Test fun autoNeverSilentlyUploads() = runBlocking {
        val (c, _, _) = coord(Availability.Unavailable("no model"), ready)
        val r = c.resolve(AiMode.AUTO, consentGranted = true)          // even WITH consent: must ask first
        assertTrue(r is Resolution.OfferOnline); assertTrue((r as Resolution.OfferOnline).onlineUsable)
    }
    @Test fun autoUsesOnlineOnlyAfterExplicitConfirmation() = runBlocking {
        val (c, _, n) = coord(Availability.Unavailable("no model"), ready)
        assertSame(n, (c.resolve(AiMode.AUTO, true, onlineConfirmedForThisRun = true) as Resolution.Use).provider)
        assertEquals(Resolution.NeedConsent, c.resolve(AiMode.AUTO, false, onlineConfirmedForThisRun = true))
    }
}
