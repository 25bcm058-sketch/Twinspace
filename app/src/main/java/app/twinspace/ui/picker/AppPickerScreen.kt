package app.twinspace.ui.picker

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.twinspace.ui.launcher.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstallableApp(val packageName: String, val label: String)

/** Picker over installed, launchable, non-system apps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(onDone: () -> Unit, vm: LauncherViewModel = viewModel()) {
    val context = LocalContext.current
    var naming by remember { mutableStateOf<InstallableApp?>(null) }

    val apps by produceState(initialValue = emptyList<InstallableApp>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (pkg == context.packageName) null
                    else InstallableApp(pkg, ri.loadLabel(pm).toString())
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Clone an app") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { naming = app }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    naming?.let { app ->
        var label by remember(app) { mutableStateOf(app.label) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("Name this clone") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Work ${app.label}") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addClone(app.packageName, label, badgeColorFor(app.packageName))
                    naming = null
                    onDone()
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }
}

/** Stable per-package badge color so clones of one app share a hue family. */
private fun badgeColorFor(packageName: String): Int {
    val palette = listOf(0xFF1A56C4, 0xFF00796B, 0xFFC62828, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F)
    return palette[(packageName.hashCode() and Int.MAX_VALUE) % palette.size].toInt()
}
