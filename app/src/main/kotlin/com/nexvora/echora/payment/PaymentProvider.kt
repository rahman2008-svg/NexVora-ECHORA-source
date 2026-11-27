package com.nexvora.echora.payment

import com.nexvora.echora.data.EchoraRepository
import com.nexvora.echora.data.PaymentEntity
import com.nexvora.echora.premium.BackendApi
import com.nexvora.echora.premium.EntitlementRepository
import com.nexvora.echora.premium.Plan
import com.nexvora.echora.premium.PlanCatalog

enum class PaymentStatus { PENDING, VERIFIED, REJECTED, EXPIRED, REFUNDED }

/** Payment processing is separate from entitlement logic: providers only create/track payments, they never grant plans. */
interface PaymentProvider {
    val id: String
    val displayName: String
    /** Official instructions to show before the user pays, or null when the provider is not configured. */
    fun instructions(plan: Plan): String?
    suspend fun submit(plan: Plan, reference: String): Result<PaymentEntity>
}

object ReferenceValidator {
    private val re = Regex("^[A-Za-z0-9]{6,32}$")
    fun isValid(ref: String) = re.matches(ref.trim())
    fun normalize(ref: String) = ref.trim().uppercase()
}

/**
 * Manual mobile-wallet flow (e.g. bKash/Nagad send-money): user pays out-of-band, submits the transaction ID,
 * the backend stores it PENDING, an administrator verifies, then the backend grants the plan. This class never
 * marks anything verified — only the backend's response can set a status.
 */
class ManualPaymentProvider(
    private val repo: EchoraRepository,
    private val api: BackendApi?,
    private val entitlements: EntitlementRepository,
    private val officialInstructions: String?,         // configured by the operator; null => provider disabled
    override val id: String = "manual",
    override val displayName: String = "Manual transfer (verified by admin)",
) : PaymentProvider {
    override fun instructions(plan: Plan): String? =
        officialInstructions?.replace("{AMOUNT}", PlanCatalog.defaults.first { it.plan == plan }.priceBdtPerMonth.toString())

    override suspend fun submit(plan: Plan, reference: String): Result<PaymentEntity> {
        val api = api ?: return Result.failure(IllegalStateException("Payments need the backend. Configure it first (see PAYMENT_SETUP.md)."))
        if (plan == Plan.FREE) return Result.failure(IllegalArgumentException("Free plan needs no payment."))
        if (officialInstructions == null) return Result.failure(IllegalStateException("No payment method is configured by the operator."))
        val ref = ReferenceValidator.normalize(reference)
        if (!ReferenceValidator.isValid(ref)) return Result.failure(IllegalArgumentException("Enter the transaction ID exactly as shown in your payment receipt (6–32 letters/digits)."))
        repo.paymentByReference(ref)?.let { return Result.failure(IllegalStateException("This transaction ID was already submitted (status: ${it.status}).")) }  // duplicate guard (client side; server enforces too)
        val idem = "$ref:${plan.name}"                       // stable key => retries are idempotent
        return api.submitPayment(entitlements.deviceId(), plan, id, ref, idem).map { r ->
            val e = PaymentEntity(serverId = r.serverId, reference = r.reference, plan = r.plan.name, amountBdt = r.amountBdt, method = r.method, status = r.status)
            repo.insertPayment(e); e
        }
    }

    /** Pulls authoritative statuses from the server, then refreshes the entitlement. */
    suspend fun sync(): Result<Unit> {
        val api = api ?: return Result.failure(IllegalStateException("Backend is not configured."))
        return api.listPayments(entitlements.deviceId()).mapCatching { list ->
            list.forEach { r ->
                if (repo.paymentByReference(r.reference) == null)
                    repo.insertPayment(PaymentEntity(serverId = r.serverId, reference = r.reference, plan = r.plan.name, amountBdt = r.amountBdt, method = r.method, status = r.status))
                else repo.updatePaymentStatus(r.reference, r.status, r.serverId)
            }
            entitlements.refresh().getOrThrow(); Unit
        }
    }
}

/** Placeholder contract for a real gateway (e.g. bKash Tokenized Checkout). Intentionally NOT implemented: needs provider credentials and docs. */
class GatewayPaymentProvider : PaymentProvider {
    override val id = "gateway"
    override val displayName = "Online gateway (not configured)"
    override fun instructions(plan: Plan): String? = null
    override suspend fun submit(plan: Plan, reference: String): Result<PaymentEntity> =
        Result.failure(UnsupportedOperationException("No gateway credentials configured. See PAYMENT_SETUP.md."))
}
