package com.nexvora.echora.premium

import com.nexvora.echora.core.secure.SecretKeys
import com.nexvora.echora.core.secure.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class EntitlementResponse(val plan: Plan, val status: String, val expiresAtMs: Long, val token: String?)
data class RemotePayment(val serverId: String, val reference: String, val plan: Plan, val amountBdt: Int, val method: String, val status: String)

/** Contract with backend/ (see backend/README.md). The backend, not this client, decides plans and payment status. */
interface BackendApi {
    suspend fun fetchEntitlement(deviceId: String): Result<EntitlementResponse>
    suspend fun submitPayment(deviceId: String, plan: Plan, method: String, reference: String, idempotencyKey: String): Result<RemotePayment>
    suspend fun listPayments(deviceId: String): Result<List<RemotePayment>>
}

class HttpBackendApi(private val baseUrl: String, private val secrets: SecretStore) : BackendApi {
    init { require(baseUrl.startsWith("https://")) { "Backend URL must be HTTPS." } }

    private suspend fun call(method: String, path: String, body: JSONObject? = null, deviceId: String, extra: Map<String, String> = emptyMap()): Result<JSONObject> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val c = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method; connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Device-Id", deviceId)
                    secrets.get(SecretKeys.ENTITLEMENT_TOKEN)?.let { setRequestProperty("Authorization", "Bearer $it") }
                    extra.forEach { (k, v) -> setRequestProperty(k, v) }
                    if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
                }
                try {
                    body?.let { c.outputStream.use { o -> o.write(it.toString().toByteArray()) } }
                    val code = c.responseCode
                    val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error(runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifBlank { "HTTP $code" })
                    JSONObject(text)
                } finally { c.disconnect() }
            }
        }

    private fun JSONObject.toPayment() = RemotePayment(getString("id"), getString("reference"), Plan.valueOf(getString("plan")), getInt("amountBdt"), getString("method"), getString("status"))

    override suspend fun fetchEntitlement(deviceId: String) = call("GET", "/v1/entitlement", deviceId = deviceId).map {
        EntitlementResponse(Plan.valueOf(it.getString("plan")), it.getString("status"), it.optLong("expiresAtMs", 0), it.optString("token").ifBlank { null })
    }
    override suspend fun submitPayment(deviceId: String, plan: Plan, method: String, reference: String, idempotencyKey: String) =
        call("POST", "/v1/payments", JSONObject().put("plan", plan.name).put("method", method).put("reference", reference), deviceId, mapOf("Idempotency-Key" to idempotencyKey)).map { it.toPayment() }
    override suspend fun listPayments(deviceId: String) = call("GET", "/v1/payments", deviceId = deviceId).map { o ->
        val a: JSONArray = o.getJSONArray("payments"); List(a.length()) { a.getJSONObject(it).toPayment() }
    }
}

/** Resolves the real API on every call, so changing the backend URL in Settings applies immediately. */
class DynamicBackendApi(private val provider: () -> BackendApi?) : BackendApi {
    private val none = Result.failure<Nothing>(IllegalStateException("Backend is not configured. Set the backend URL in Settings."))
    override suspend fun fetchEntitlement(deviceId: String) = provider()?.fetchEntitlement(deviceId) ?: none
    override suspend fun submitPayment(deviceId: String, plan: Plan, method: String, reference: String, idempotencyKey: String) =
        provider()?.submitPayment(deviceId, plan, method, reference, idempotencyKey) ?: none
    override suspend fun listPayments(deviceId: String) = provider()?.listPayments(deviceId) ?: none
}
