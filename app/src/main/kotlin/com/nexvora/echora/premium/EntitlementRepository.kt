package com.nexvora.echora.premium

import com.nexvora.echora.core.secure.SecretKeys
import com.nexvora.echora.core.secure.SecretStore
import com.nexvora.echora.data.EchoraRepository
import com.nexvora.echora.data.SubscriptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Entitlements come from the BACKEND only. The local row is a cache that is honoured for [CACHE_GRACE_MS]
 * after the last successful server check (so the app works offline), never longer, and never raises a plan
 * above what the server last said. Nothing here lets a UI button or a locally edited value grant premium.
 */
class EntitlementRepository(
    private val repo: EchoraRepository,
    private val api: BackendApi?,
    private val secrets: SecretStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val entitlement: Flow<Entitlement> = repo.subscription().map { it.toEntitlement(clock()) }

    suspend fun current(): Entitlement = repo.cachedSubscription().toEntitlement(clock())

    /** Refresh from the server. Returns the new entitlement, or the previous cached one if the server is unreachable. */
    suspend fun refresh(): Result<Entitlement> {
        val api = api ?: return Result.failure(IllegalStateException("Backend is not configured."))
        return api.fetchEntitlement(deviceId()).map { r ->
            val now = clock()
            repo.cacheSubscription(SubscriptionEntity(1, r.plan.name, r.status, r.expiresAtMs, now, now + CACHE_GRACE_MS))
            r.token?.let { secrets.put(SecretKeys.ENTITLEMENT_TOKEN, it) }
            Entitlement(r.plan, r.expiresAtMs, now, Entitlement.Source.SERVER)
        }
    }

    suspend fun clearLocal() { repo.clearSubscription(); secrets.remove(SecretKeys.ENTITLEMENT_TOKEN) }

    fun deviceId(): String = secrets.get(SecretKeys.DEVICE_ID) ?: java.util.UUID.randomUUID().toString().also { secrets.put(SecretKeys.DEVICE_ID, it) }

    private fun SubscriptionEntity?.toEntitlement(now: Long): Entitlement {
        if (this == null || plan == "FREE") return Entitlement.Free
        if (now > validUntilMs) return Entitlement.Free            // stale cache: need a fresh server check
        val p = runCatching { Plan.valueOf(plan) }.getOrDefault(Plan.FREE)
        return Entitlement(p, expiresAtMs, verifiedAtMs, Entitlement.Source.CACHE)
    }

    companion object { const val CACHE_GRACE_MS = 3L * 24 * 60 * 60 * 1000 }
}
