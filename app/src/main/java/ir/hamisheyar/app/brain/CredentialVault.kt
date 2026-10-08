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

/** BYOK credentials stay on this device, encrypted with a non-exportable Android Keystore key. */
object CredentialVault {
    private const val ALIAS = "hamisheyar_brain_credentials_v1"
    private const val PREF = "brain_secrets"
    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    fun put(context: Context, provider: String, value: String) {
        require(provider == "gemini" || provider == "groq")
        val trimmed = value.trim()
        require(trimmed.length in 10..512 && !trimmed.contains('\n'))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val bytes = cipher.iv + encrypted
        prefs(context).edit().putString(provider, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    fun get(context: Context, provider: String): String? {
        return try {
        val encoded = prefs(context).getString(provider, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun ready(context: Context) = get(context, "gemini") != null &&
        get(context, "groq") != null && verified(context, "gemini") && verified(context, "groq")

    fun verified(context: Context, provider: String) =
        prefs(context).getBoolean("verified_$provider", false) && get(context, provider) != null

    fun setVerified(context: Context, provider: String, value: Boolean) {
        prefs(context).edit().putBoolean("verified_$provider", value).apply()
    }

    fun clear(context: Context, provider: String) {
        prefs(context).edit().remove(provider).remove("verified_$provider").apply()
    }

    fun clearAll(context: Context) = prefs(context).edit().clear().apply()
}
