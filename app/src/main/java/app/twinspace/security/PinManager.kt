package app.twinspace.security

import android.content.Context
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage: PBKDF2-HMAC-SHA256, 120k iterations, per-device random salt.
 * The stored verifier blob is itself Keystore-wrapped so a prefs dump yields nothing.
 */
class PinManager(context: Context, private val keystore: KeystoreManager) {

    private val prefs = context.getSharedPreferences("twinspace_lock", Context.MODE_PRIVATE)

    val isPinSet: Boolean get() = prefs.contains(KEY_VERIFIER)

    fun setPin(pin: CharArray) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_VERIFIER, android.util.Base64.encodeToString(keystore.encrypt(salt + hash), android.util.Base64.NO_WRAP))
            .apply()
    }

    fun verify(pin: CharArray): Boolean {
        val blob = prefs.getString(KEY_VERIFIER, null)?.let {
            runCatching { keystore.decrypt(android.util.Base64.decode(it, android.util.Base64.NO_WRAP)) }.getOrNull()
        } ?: return false
        val salt = blob.copyOfRange(0, 16)
        val expected = blob.copyOfRange(16, blob.size)
        return hash(pin, salt).contentEquals(expected)
    }

    fun clear() = prefs.edit().remove(KEY_VERIFIER).apply()

    private fun hash(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private companion object {
        const val KEY_VERIFIER = "pin_verifier"
        const val ITERATIONS = 120_000
    }
}
