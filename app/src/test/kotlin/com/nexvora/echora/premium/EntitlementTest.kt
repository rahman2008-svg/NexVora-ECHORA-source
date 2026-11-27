package com.nexvora.echora.premium

import com.nexvora.echora.payment.ReferenceValidator
import org.junit.Assert.*
import org.junit.Test

class EntitlementTest {
    private val now = 1_000_000L
    private fun ent(p: Plan, exp: Long) = Entitlement(p, exp, now, Entitlement.Source.SERVER)

    @Test fun freeHasNoPaidFeatures() = Feature.entries.forEach { assertFalse(it.name, Entitlement.Free.allows(it, now)) }
    @Test fun plusUnlocksPlusOnly() {
        val e = ent(Plan.PLUS, now + 1)
        assertTrue(e.allows(Feature.ADVANCED_EDITING, now)); assertTrue(e.allows(Feature.ADVANCED_EXPORT, now))
        assertFalse(e.allows(Feature.BATCH_TRANSCRIPTION, now)); assertFalse(e.allows(Feature.ADVANCED_TOOLS, now))
    }
    @Test fun proUnlocksEverything() = Feature.entries.forEach { assertTrue(it.name, ent(Plan.PRO, now + 1).allows(it, now)) }
    @Test fun expiredPaidPlanFallsBackToFree() {
        val e = ent(Plan.PRO, now - 1)
        assertEquals(Plan.FREE, e.effectivePlan(now)); assertFalse(e.allows(Feature.ADVANCED_EDITING, now))
    }
    @Test fun expiryBoundaryIsExclusive() = assertFalse(ent(Plan.PLUS, now).isActive(now))
    @Test fun offlineTranscriptionIsNotAPaidFeature() = assertTrue(Feature.entries.none { it.name.contains("OFFLINE") })
    @Test fun priceDefaultsAreConfigurable() {
        val p = PlanCatalog.build(120, 250)
        assertEquals(listOf(0, 120, 250), p.map { it.priceBdtPerMonth })
        assertEquals(listOf(0, 99, 199), PlanCatalog.build(99, 199).map { it.priceBdtPerMonth })
    }
    @Test fun transactionReferenceValidation() {
        assertTrue(ReferenceValidator.isValid("8N7A6D5EE1")); assertTrue(ReferenceValidator.isValid(" abc123 "))
        listOf("", "abc", "has space1", "x'; DROP TABLE--", "a".repeat(33), "টাকা১২৩৪৫৬").forEach { assertFalse(it, ReferenceValidator.isValid(it)) }
        assertEquals("ABC123", ReferenceValidator.normalize(" abc123 "))
    }
}
