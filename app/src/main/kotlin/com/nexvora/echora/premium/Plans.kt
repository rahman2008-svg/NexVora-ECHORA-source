package com.nexvora.echora.premium

/** Prices are configuration (BDT/month), not constants in UI code. Server remains the source of truth for what a plan grants. */
enum class Plan(val rank: Int) { FREE(0), PLUS(1), PRO(2) }

data class PlanInfo(val plan: Plan, val priceBdtPerMonth: Int, val title: String, val features: List<String>)

object PlanCatalog {
    /** Prices come from build config (gradle properties echora.pricePlus / echora.pricePro; spec defaults 99 / 199 BDT). */
    val defaults: List<PlanInfo> get() = build(com.nexvora.echora.BuildConfig.ECHORA_PRICE_PLUS, com.nexvora.echora.BuildConfig.ECHORA_PRICE_PRO)

    fun build(plus: Int, pro: Int): List<PlanInfo> = listOf(
        PlanInfo(Plan.FREE, 0, "Free", listOf(
            "Basic recording & playback", "Offline AI transcription (downloaded model)", "Limited online AI usage",
            "Basic transcript export", "Basic audio editing", "Local recording history")),
        PlanInfo(Plan.PLUS, plus, "Plus", listOf(
            "Advanced audio editing", "Advanced export options", "More online transcription allowance",
            "Batch processing", "Advanced presets", "Ad-free")),
        PlanInfo(Plan.PRO, pro, "Pro", listOf(
            "Everything in Plus", "Advanced audio tools", "Batch transcription",
            "Advanced processing options", "Premium presets", "Extended export", "Priority support")),
    )
}

/** Gated capabilities. Offline transcription is intentionally NOT here: it is free for everyone. */
enum class Feature(val minPlan: Plan) {
    ADVANCED_EDITING(Plan.PLUS), ADVANCED_EXPORT(Plan.PLUS), BATCH_PROCESSING(Plan.PLUS),
    ADVANCED_PRESETS(Plan.PLUS), BATCH_TRANSCRIPTION(Plan.PRO), PREMIUM_PRESETS(Plan.PRO), ADVANCED_TOOLS(Plan.PRO),
}

data class Entitlement(val plan: Plan, val expiresAtMs: Long, val verifiedAtMs: Long, val source: Source) {
    enum class Source { NONE, SERVER, CACHE }

    fun isActive(nowMs: Long): Boolean = plan == Plan.FREE || expiresAtMs > nowMs
    /** Effective plan: an expired paid plan falls back to FREE. */
    fun effectivePlan(nowMs: Long): Plan = if (isActive(nowMs)) plan else Plan.FREE
    fun allows(feature: Feature, nowMs: Long): Boolean = effectivePlan(nowMs).rank >= feature.minPlan.rank

    companion object { val Free = Entitlement(Plan.FREE, 0, 0, Source.NONE) }
}
