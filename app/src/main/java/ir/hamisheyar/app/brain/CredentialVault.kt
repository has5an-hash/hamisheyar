package ir.hamisheyar.app.brain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** User-owned API keys never leave the device except in TLS requests to their providers. */
object CredentialVault {
    private const val ALIAS = "hamisheyar_private_ai_credentials_v1"
    private const val PREFS = "hamisheyar_private_credentials"
    const val GEMINI = "gemini"
    const val GROQ = "groq"

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
             .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
             .setKeySize(256)
             .setRandomizedEncryptionRequired(true)
             .build())
        }.generateKey()
    }

    @Synchronized
    fun save(context: Context, provider: String, apiKey: String) {
        require(provider == GEMINI || provider == GROQ)
        require(apiKey.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val iv = cipher.iv
        val data = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + data.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(data, 0, packed, iv.size, data.size)
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(provider, encoded).commit())
    }

    fun read(context: Context, provider: String): String? {
        if (provider != GEMINI && provider != GROQ) return null
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(provider, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size >= 29)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun hasBoth(context: Context): Boolean =
        !read(context, GEMINI).isNullOrBlank() && !read(context, GROQ).isNullOrBlank()

    fun clear(context: Context, provider: String? = null) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (provider == null) edit.clear() else edit.remove(provider)
        edit.commit()
    }
}
