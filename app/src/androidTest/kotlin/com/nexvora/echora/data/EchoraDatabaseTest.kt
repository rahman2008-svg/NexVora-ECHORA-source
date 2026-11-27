package com.nexvora.echora.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Instrumented (device/emulator) tests: ./gradlew connectedDebugAndroidTest. NOT executed in the authoring environment. */
class EchoraDatabaseTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EchoraDatabase::class.java)
    private lateinit var db: EchoraDatabase; private lateinit var repo: EchoraRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EchoraDatabase::class.java).allowMainThreadQueries().build()
        repo = EchoraRepository(db)
    }
    @After fun close() = db.close()

    private fun audio(name: String) = AudioEntity(path = "/x/$name.wav", displayName = name, mimeType = "audio/wav", durationMs = 1000, sizeBytes = 10, source = "record")

    @Test fun saveSearchRenameDeleteAudio() = runBlocking {
        val id = repo.addAudio(audio("Meeting")); repo.addAudio(audio("Lecture"))
        assertEquals(1, repo.audio("meet").first().size)
        repo.renameAudio(id, "Standup"); assertEquals("Standup", repo.getAudio(id)!!.displayName)
        repo.setFavorite(id, true); assertEquals(1, repo.audio(favoritesOnly = true).first().size)
        repo.deleteAudio(id, deleteFile = false); assertNull(repo.getAudio(id))
    }
    @Test fun transcriptsSearchVersionDelete() = runBlocking {
        val a = repo.addAudio(audio("a"))
        val t = repo.saveTranscript(TranscriptEntity(audioId = a, title = "T", language = "bn", engine = "offline:whisper-tiny", text = "আমি বাংলায় কথা বলি"))
        assertEquals(1, repo.transcripts("বাংলায়").first().size)
        val v2 = repo.saveTranscriptVersion(repo.getTranscript(t)!!, "edited"); assertEquals(2, repo.getTranscript(v2)!!.version)
        assertEquals(2, repo.transcriptsFor(a).first().size)                 // original preserved
        repo.deleteTranscript(v2); assertEquals(1, repo.transcriptsFor(a).first().size)
    }
    @Test fun duplicatePaymentReferenceRejected() = runBlocking {
        val p = PaymentEntity(serverId = null, reference = "ABC12345", plan = "PLUS", amountBdt = 99, method = "manual", status = "PENDING")
        assertTrue(repo.insertPayment(p)); assertFalse(repo.insertPayment(p.copy(plan = "PRO")))
        repo.updatePaymentStatus("ABC12345", "VERIFIED", "srv1"); assertEquals("VERIFIED", repo.paymentByReference("ABC12345")!!.status)
    }
    @Test fun migrationsValidateFromV1() { helper.createDatabase("mig-test", 1).close(); helper.runMigrationsAndValidate("mig-test", 1, true, *EchoraDatabase.MIGRATIONS) }
}
