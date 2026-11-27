package com.nexvora.echora.core.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Abstraction so tests can use an in-memory fake instead of the Android Keystore. */
interface SecretStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

/**
 * Stores secrets (online API key, entitlement token) encrypted with an AES-256-GCM key that lives in the
 * Android Keystore. Ciphertext is kept in private SharedPreferences. Secrets are never logged or backed up.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("echora_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    @Synchronized
    override fun put(key: String, value: String) {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString(key, packed).apply()
    }

    @Synchronized
    override fun get(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        return runCatching {
            val (iv, ct) = packed.split(":", limit = 2)
            val c = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
            String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized
    override fun remove(key: String) { prefs.edit().remove(key).apply() }

    private companion object {
        const val ALIAS = "echora_secret_key_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}

object SecretKeys {
    const val ONLINE_API_KEY = "online_api_key"
    const val ENTITLEMENT_TOKEN = "entitlement_token"
    const val DEVICE_ID = "device_id"
}
