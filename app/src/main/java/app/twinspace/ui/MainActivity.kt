package app.twinspace.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.twinspace.TwinSpaceApp
import app.twinspace.ui.launcher.LauncherScreen
import app.twinspace.ui.lock.LockScreen
import app.twinspace.ui.onboarding.OnboardingScreen
import app.twinspace.ui.picker.AppPickerScreen
import app.twinspace.ui.settings.SettingsScreen
import app.twinspace.ui.theme.TwinSpaceTheme

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOCK = "lock"
    const val LAUNCHER = "launcher"
    const val PICKER = "picker"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_LAUNCH_CLONE = "app.twinspace.action.LAUNCH_CLONE"
        const val EXTRA_CLONE_ID = "app.twinspace.extra.CLONE_ID"
    }

    private val container by lazy { (application as TwinSpaceApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Home-screen clone shortcut deep-link.
        val deepLinkCloneId: String? =
            if (intent?.action == ACTION_LAUNCH_CLONE) intent.getStringExtra(EXTRA_CLONE_ID) else null

        setContent {
            TwinSpaceTheme {
                val start = when {
                    !container.lockManager.isLauncherLocked(pinEnabled = false) &&
                        !onboardingDone() -> Routes.ONBOARDING
                    container.pinManager.isPinSet -> Routes.LOCK
                    else -> Routes.LAUNCHER
                }
                AppNav(startDestination = start, deepLinkCloneId = deepLinkCloneId)
            }
        }
    }

    private fun onboardingDone(): Boolean =
        getSharedPreferences("twinspace_prefs", MODE_PRIVATE).getBoolean("onboarding_done", false)

    @Composable
    private fun AppNav(startDestination: String, deepLinkCloneId: String?) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = startDestination) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onDone = {
                    getSharedPreferences("twinspace_prefs", MODE_PRIVATE)
                        .edit().putBoolean("onboarding_done", true).apply()
                    nav.navigate(if (container.pinManager.isPinSet) Routes.LOCK else Routes.LAUNCHER) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Routes.LOCK) {
                LockScreen(onUnlocked = {
                    container.lockManager.unlockLauncher()
                    nav.navigate(Routes.LAUNCHER) { popUpTo(Routes.LOCK) { inclusive = true } }
                })
            }
            composable(Routes.LAUNCHER) {
                LauncherScreen(
                    onAddClone = { nav.navigate(Routes.PICKER) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    deepLinkCloneId = deepLinkCloneId,
                )
            }
            composable(Routes.PICKER) {
                AppPickerScreen(onDone = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
