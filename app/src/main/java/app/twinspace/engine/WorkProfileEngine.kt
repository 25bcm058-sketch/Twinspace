package app.twinspace.engine

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import app.twinspace.data.db.CloneEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OS-supported backend (ARCHITECTURE.md §1): each clone maps to an app installed
 * in the managed profile this app owns. Isolation is enforced by Android itself.
 *
 * Functional when TwinSpace is profile owner of a managed profile (activated via
 * `adb shell dpm set-profile-owner` or MDM provisioning). On a stock consumer
 * device [isAvailable] is false and the container falls back to the stub.
 *
 * Note: one managed profile = one clone *slot per app*. Multiple clones of the
 * same package require multiple profiles (device-owner territory) — this backend
 * therefore supports one clone per package, which the repository enforces by
 * refusing duplicates when this backend is active.
 */
class WorkProfileEngine(private val context: Context) : VirtualEngine {

    override val backendName = "work-profile"

    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    companion object {
        fun isAvailable(context: Context): Boolean {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            // A managed profile exists and is usable by us.
            return um.userProfiles.any { it != Process.myUserHandle() }
        }
    }

    private val profileHandle
        get() = userManager.userProfiles.firstOrNull { it != Process.myUserHandle() }

    override fun isOperational(): Boolean = profileHandle != null

    override suspend fun install(clone: CloneEntity, env: CloneEnvironment): Result<Unit> =
        withContext(Dispatchers.IO) {
            val handle = profileHandle
                ?: return@withContext Result.failure(EngineNotOperationalException("No managed profile"))
            val installed = launcherApps.getActivityList(clone.packageName, handle).isNotEmpty()
            if (installed) Result.success(Unit)
            else Result.failure(
                EngineNotOperationalException(
                    "${clone.packageName} is not installed in the work profile. " +
                        "Install it there (profile-owner installExistingPackage) first."
                )
            )
        }

    override suspend fun launch(clone: CloneEntity, env: CloneEnvironment): Result<Unit> =
        withContext(Dispatchers.Main) {
            val handle = profileHandle
                ?: return@withContext Result.failure(EngineNotOperationalException("No managed profile"))
            val activities = launcherApps.getActivityList(clone.packageName, handle)
            val main = activities.firstOrNull()
                ?: return@withContext Result.failure(
                    EngineNotOperationalException("No launchable activity for ${clone.packageName} in profile")
                )
            runCatching {
                launcherApps.startMainActivity(main.componentName, handle, null, null)
            }
        }

    override suspend fun stop(clone: CloneEntity): Result<Unit> = Result.success(Unit)
    // OS manages profile app processes; nothing to do.

    override fun isRunning(clone: CloneEntity): Boolean = false
    // Best-effort: the OS does not expose per-profile process state to us.

    override suspend fun uninstall(clone: CloneEntity, env: CloneEnvironment): Result<Unit> =
        Result.success(Unit) // Profile data is wiped by the OS when the app is removed there.
}
