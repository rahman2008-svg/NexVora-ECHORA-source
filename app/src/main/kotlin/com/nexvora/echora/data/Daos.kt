package com.nexvora.echora.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio WHERE (:q = '' OR displayName LIKE '%' || :q || '%') AND (:favOnly = 0 OR favorite = 1) AND (:source = '' OR source = :source) ORDER BY createdAt DESC")
    fun observe(q: String, favOnly: Int, source: String): Flow<List<AudioEntity>>
    @Query("SELECT * FROM audio ORDER BY createdAt DESC LIMIT :n") fun recent(n: Int): Flow<List<AudioEntity>>
    @Query("SELECT * FROM audio WHERE id = :id") suspend fun get(id: Long): AudioEntity?
    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM audio") fun totalBytes(): Flow<Long>
    @Insert suspend fun insert(a: AudioEntity): Long
    @Query("UPDATE audio SET displayName = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE audio SET favorite = :fav WHERE id = :id") suspend fun setFavorite(id: Long, fav: Boolean)
    @Query("DELETE FROM audio WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE (:q = '' OR title LIKE '%' || :q || '%' OR text LIKE '%' || :q || '%') ORDER BY updatedAt DESC")
    fun observe(q: String): Flow<List<TranscriptEntity>>
    @Query("SELECT * FROM transcripts WHERE audioId = :audioId ORDER BY version DESC") fun forAudio(audioId: Long): Flow<List<TranscriptEntity>>
    @Query("SELECT * FROM transcripts WHERE id = :id") suspend fun get(id: Long): TranscriptEntity?
    @Insert suspend fun insert(t: TranscriptEntity): Long
    @Update suspend fun update(t: TranscriptEntity)
    @Query("UPDATE transcripts SET title = :title, updatedAt = :now WHERE id = :id") suspend fun rename(id: Long, title: String, now: Long)
    @Query("DELETE FROM transcripts WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM transcripts") suspend fun deleteAll()
    @Query("SELECT COALESCE(MAX(version),0) FROM transcripts WHERE audioId = :audioId") suspend fun maxVersion(audioId: Long): Int
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models") fun observe(): Flow<List<ModelEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(m: ModelEntity)
    @Query("DELETE FROM models WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription WHERE id = 1") fun observe(): Flow<SubscriptionEntity?>
    @Query("SELECT * FROM subscription WHERE id = 1") suspend fun get(): SubscriptionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(s: SubscriptionEntity)
    @Query("DELETE FROM subscription") suspend fun clear()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY createdAt DESC") fun observe(): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM payments WHERE reference = :ref LIMIT 1") suspend fun byReference(ref: String): PaymentEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(p: PaymentEntity): Long
    @Query("UPDATE payments SET status = :status, serverId = COALESCE(:serverId, serverId), updatedAt = :now WHERE reference = :ref")
    suspend fun updateStatus(ref: String, status: String, serverId: String?, now: Long)
    @Query("DELETE FROM payments") suspend fun clear()
}
