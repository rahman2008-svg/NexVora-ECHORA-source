package com.nexvora.echora.transcription

import com.nexvora.echora.settings.AiMode

/** Outcome of choosing a provider. Pure logic so it can be unit-tested; it never uploads anything itself. */
sealed interface Resolution {
    data class Use(val provider: TranscriptionProvider) : Resolution
    /** Offline selected but unusable (no native engine / no model). UI should send the user to Model Manager. */
    data class NeedModel(val reason: String) : Resolution
    /** Online selected but the user has not accepted the external-upload notice. */
    data object NeedConsent : Resolution
    /** AUTO mode, offline unusable: ask the user explicitly whether to use online instead. Nothing is uploaded yet. */
    data class OfferOnline(val offlineReason: String, val onlineUsable: Boolean, val onlineReason: String?) : Resolution
    data class Unavailable(val reason: String) : Resolution
}

class TranscriptionCoordinator(
    private val offline: TranscriptionProvider,
    private val online: TranscriptionProvider,
) {
    /**
     * @param onlineConfirmedForThisRun true only after the user tapped "Use online this time" for an [Resolution.OfferOnline].
     */
    suspend fun resolve(mode: AiMode, consentGranted: Boolean, onlineConfirmedForThisRun: Boolean = false): Resolution = when (mode) {
        AiMode.OFFLINE -> when (val a = offline.availability()) {
            Availability.Ready -> Resolution.Use(offline)
            is Availability.Unavailable -> Resolution.NeedModel(a.reason)
        }
        AiMode.ONLINE -> onlineResolution(consentGranted)
        AiMode.AUTO -> when (val a = offline.availability()) {
            Availability.Ready -> Resolution.Use(offline)
            is Availability.Unavailable -> if (onlineConfirmedForThisRun) onlineResolution(consentGranted) else {
                val o = online.availability()
                Resolution.OfferOnline(a.reason, o is Availability.Ready, (o as? Availability.Unavailable)?.reason)
            }
        }
    }

    private suspend fun onlineResolution(consentGranted: Boolean): Resolution {
        if (!consentGranted) return Resolution.NeedConsent
        return when (val o = online.availability()) {
            Availability.Ready -> Resolution.Use(online)
            is Availability.Unavailable -> Resolution.Unavailable(o.reason)
        }
    }
}
