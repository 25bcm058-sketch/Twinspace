package app.twinspace.ui.launcher

import android.app.ActivityManager
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.twinspace.TwinSpaceApp
import app.twinspace.data.db.CloneEntity
import app.twinspace.engine.CloneLauncher
import app.twinspace.icons.BadgeIconFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as TwinSpaceApp).container
    private val repository = container.cloneRepository
    private val iconFactory = BadgeIconFactory(app)

    val clones: StateFlow<List<CloneEntity>> =
        repository.observeClones().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One-shot UI events (lock required, engine errors). */
    val events = MutableStateFlow<String?>(null)

    /** RAM-aware cap (ARCHITECTURE.md §10): 10 default, 15 on big-memory devices. */
    val cloneLimit: Int
        get() {
            val am = getApplication<Application>().getSystemService(ActivityManager::class.java)
            val big = am.memoryClass >= 512
            val userCap = container.lockManager // stored in lock prefs for simplicity
                .let { getApplication<Application>().getSharedPreferences("twinspace_prefs", 0).getInt("clone_limit", 0) }
            val default = if (big) 15 else 10
            return if (userCap in 1..MAX_LIMIT) userCap else default
        }

    suspend fun canAddClone(): Boolean = repository.count() < cloneLimit

    fun addClone(packageName: String, label: String, badgeColor: Int) {
        viewModelScope.launch {
            if (!canAddClone()) {
                events.value = "Clone limit reached ($cloneLimit). Raise it in Settings on high-RAM devices."
                return@launch
            }
            val clone = repository.createClone(packageName, label, badgeColor)
            val iconPath = iconFactory.saveIcon(clone)
            iconFactory.pinShortcut(clone, iconPath)
        }
    }

    fun launch(clone: CloneEntity) {
        viewModelScope.launch {
            when (val outcome = container.cloneLauncher.launch(clone)) {
                CloneLauncher.LaunchOutcome.Success -> Unit
                CloneLauncher.LaunchOutcome.Locked -> events.value = "locked:${clone.id}"
                is CloneLauncher.LaunchOutcome.Failure ->
                    events.value = outcome.cause.message ?: "Launch failed"
            }
        }
    }

    fun rename(clone: CloneEntity, newLabel: String) = viewModelScope.launch {
        repository.rename(clone.id, newLabel)
        repository.getById(clone.id)?.let { updated ->
            val path = iconFactory.saveIcon(updated)
            iconFactory.pinShortcut(updated, path)
        }
    }

    fun toggleLock(clone: CloneEntity) = viewModelScope.launch {
        repository.setLocked(clone.id, !clone.locked)
    }

    fun toggleClipboard(clone: CloneEntity) = viewModelScope.launch {
        repository.setClipboardSharing(clone.id, !clone.clipboardSharing)
    }

    fun reset(clone: CloneEntity) = viewModelScope.launch { repository.resetClone(clone.id) }

    fun delete(clone: CloneEntity) = viewModelScope.launch {
        iconFactory.removeShortcut(clone.id)
        repository.deleteClone(clone.id)
    }

    fun consumeEvent() {
        events.value = null
    }

    companion object {
        const val MAX_LIMIT = 25
    }
}
