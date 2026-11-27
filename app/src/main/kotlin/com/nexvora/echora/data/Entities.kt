package com.nexvora.echora.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Audio file metadata. Only the path is stored — never audio bytes. [source]: record | import | export | edit. */
@Entity(tableName = "audio", indices = [Index("createdAt"), Index("displayName")])
data class AudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,                 // app-private absolute path, or content:// URI string for imported SAF files
    val displayName: String,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val source: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One saved version of a transcript. [segmentsJson] holds real engine timestamps, or "[]" when none exist. */
@Entity(tableName = "transcripts", indices = [Index("audioId"), Index("updatedAt")])
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioId: Long?,
    val title: String,
    val language: String,
    val engine: String,               // e.g. offline:whisper-tiny | online:<provider>
    val text: String,
    val segmentsJson: String = "[]",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Installed whisper model registry. Files live in app-private storage. */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val family: String,
    val path: String,
    val bytes: Long,
    val installedAt: Long = System.currentTimeMillis(),
)

/**
 * LOCAL CACHE of the last server-verified entitlement. NOT authoritative: the UI treats it as expired
 * after [validUntilMs] and re-validates against the backend. Editing this row never grants features
 * because gated backend calls re-check the signed token.
 */
@Entity(tableName = "subscription")
data class SubscriptionEntity(
    @PrimaryKey val id: Int = 1,
    val plan: String = "FREE",
    val status: String = "none",
    val expiresAtMs: Long = 0,
    val verifiedAtMs: Long = 0,
    val validUntilMs: Long = 0,
)

/** Payment attempts as reported by the backend. Never stores credentials or card data. */
@Entity(tableName = "payments", indices = [Index(value = ["reference"], unique = true)])
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String?,
    val reference: String,            // user-submitted transaction reference
    val plan: String,
    val amountBdt: Int,
    val method: String,
    val status: String,               // PENDING | VERIFIED | REJECTED | EXPIRED | REFUNDED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
