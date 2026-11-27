package com.nexvora.echora.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Single access point for UI/business code. All suspend calls hop to [io]; Room already moves Flow work off-main. */
class EchoraRepository(private val db: EchoraDatabase, private val io: CoroutineDispatcher = Dispatchers.IO) {
    // Audio
    fun audio(query: String = "", favoritesOnly: Boolean = false, source: String = ""): Flow<List<AudioEntity>> =
        db.audio().observe(query.trim(), if (favoritesOnly) 1 else 0, source)
    fun recentAudio(n: Int = 5) = db.audio().recent(n)
    fun totalAudioBytes() = db.audio().totalBytes()
    suspend fun addAudio(a: AudioEntity): Long = withContext(io) { db.audio().insert(a) }
    suspend fun getAudio(id: Long) = withContext(io) { db.audio().get(id) }
    suspend fun renameAudio(id: Long, name: String) = withContext(io) {
        require(name.isNotBlank()) { "Name cannot be empty." }
        db.audio().rename(id, name.trim())
    }
    suspend fun setFavorite(id: Long, fav: Boolean) = withContext(io) { db.audio().setFavorite(id, fav) }
    /** Deletes the DB row and, for app-private files only, the file itself. */
    suspend fun deleteAudio(id: Long, deleteFile: Boolean = true) = withContext(io) {
        val a = db.audio().get(id) ?: return@withContext
        db.audio().delete(id)
        if (deleteFile && !a.path.startsWith("content://")) runCatching { File(a.path).delete() }
    }

    // Transcripts
    fun transcripts(query: String = "") = db.transcripts().observe(query.trim())
    fun transcriptsFor(audioId: Long) = db.transcripts().forAudio(audioId)
    suspend fun getTranscript(id: Long) = withContext(io) { db.transcripts().get(id) }
    suspend fun saveTranscript(t: TranscriptEntity): Long = withContext(io) { db.transcripts().insert(t) }
    /** Saves an edit as a NEW version so earlier text is preserved. */
    suspend fun saveTranscriptVersion(base: TranscriptEntity, newText: String): Long = withContext(io) {
        val v = (base.audioId?.let { db.transcripts().maxVersion(it) } ?: base.version) + 1
        val now = System.currentTimeMillis()
        db.transcripts().insert(base.copy(id = 0, text = newText, version = v, createdAt = now, updatedAt = now))
    }
    suspend fun renameTranscript(id: Long, title: String) = withContext(io) {
        require(title.isNotBlank()) { "Title cannot be empty." }
        db.transcripts().rename(id, title.trim(), System.currentTimeMillis())
    }
    suspend fun deleteTranscript(id: Long) = withContext(io) { db.transcripts().delete(id) }
    suspend fun deleteAllTranscripts() = withContext(io) { db.transcripts().deleteAll() }

    // Models
    fun models() = db.models().observe()
    suspend fun registerModel(m: ModelEntity) = withContext(io) { db.models().upsert(m) }
    suspend fun unregisterModel(id: String) = withContext(io) { db.models().delete(id) }

    // Subscription cache + payments
    fun subscription() = db.subscription().observe()
    suspend fun cachedSubscription() = withContext(io) { db.subscription().get() }
    suspend fun cacheSubscription(s: SubscriptionEntity) = withContext(io) { db.subscription().put(s) }
    suspend fun clearSubscription() = withContext(io) { db.subscription().clear() }
    fun payments() = db.payments().observe()
    suspend fun paymentByReference(ref: String) = withContext(io) { db.payments().byReference(ref) }
    suspend fun insertPayment(p: PaymentEntity): Boolean = withContext(io) { db.payments().insert(p) != -1L }
    suspend fun updatePaymentStatus(ref: String, status: String, serverId: String?) = withContext(io) {
        db.payments().updateStatus(ref, status, serverId, System.currentTimeMillis())
    }
    suspend fun clearPayments() = withContext(io) { db.payments().clear() }
}
