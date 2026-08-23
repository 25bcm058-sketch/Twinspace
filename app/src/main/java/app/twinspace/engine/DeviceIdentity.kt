package app.twinspace.engine

import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/**
 * Per-clone virtual device identity (ARCHITECTURE.md §6).
 *
 * Generated once at clone creation, persisted, and served by the engine's hooks
 * whenever the cloned app reads ANDROID_ID or the Advertising ID. This is what
 * gives each clone its "different device, different account" feeling while
 * sharing the real OS.
 */
data class DeviceIdentity(
    /** 16 hex chars, same shape as a real ANDROID_ID. */
    val androidId: String,
    /** UUID, same shape as a real AAID. */
    val advertisingId: String,
) {
    fun persist(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            JSONObject()
                .put("androidId", androidId)
                .put("advertisingId", advertisingId)
                .toString()
        )
    }

    companion object {
        private val random = SecureRandom()

        fun generate(): DeviceIdentity {
            val bytes = ByteArray(8).also(random::nextBytes)
            return DeviceIdentity(
                androidId = bytes.joinToString("") { "%02x".format(it) },
                advertisingId = UUID.randomUUID().toString(),
            )
        }

        fun load(file: File): DeviceIdentity? = runCatching {
            val json = JSONObject(file.readText())
            DeviceIdentity(json.getString("androidId"), json.getString("advertisingId"))
        }.getOrNull()
    }
}
