package app.twinspace.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CloneState { INSTALLING, READY, CORRUPT }

@Entity(tableName = "clones")
data class CloneEntity(
    /** UUID; also the storage subtree name and WebView data-directory suffix. */
    @PrimaryKey val id: String,
    val packageName: String,
    /** User-chosen label, e.g. "Work WhatsApp". */
    val label: String,
    /** Badge overlay color for the home-screen icon. */
    val badgeColor: Int,
    /** Path to the generated badged icon bitmap, null until generated. */
    val iconPath: String?,
    val createdAt: Long,
    val lastLaunchedAt: Long,
    val state: CloneState,
    /** Per-clone app lock (ARCHITECTURE.md §11). */
    val locked: Boolean,
    /** Virtual Settings.Secure.ANDROID_ID for this clone (§6). */
    val androidId: String,
    /** Virtual Advertising ID for this clone (§6). */
    val advertisingId: String,
    /** Clone process suffix, e.g. ":clone_a1b2c3d4" (§3). */
    val processSuffix: String,
    /** Clipboard sharing with host/other clones; default false (§8). */
    val clipboardSharing: Boolean = false,
)
