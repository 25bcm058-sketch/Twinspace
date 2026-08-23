package app.twinspace.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.twinspace.data.db.CloneEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    onAddClone: () -> Unit,
    onOpenSettings: () -> Unit,
    deepLinkCloneId: String? = null,
    vm: LauncherViewModel = viewModel(),
) {
    val clones by vm.clones.collectAsState()
    val event by vm.events.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(deepLinkCloneId) {
        deepLinkCloneId?.let { id -> clones.firstOrNull { it.id == id }?.let(vm::launch) }
    }
    LaunchedEffect(event) {
        event?.let {
            if (!it.startsWith("locked:")) snackbar.showSnackbar(it)
            vm.consumeEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TwinSpace") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClone) {
                Icon(Icons.Default.Add, contentDescription = "Add clone")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (clones.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            CloneGrid(clones, vm, Modifier.padding(padding))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No clones yet.\nTap + to clone an installed app.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CloneGrid(
    clones: List<CloneEntity>,
    vm: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(clones, key = { it.id }) { clone ->
            CloneCell(clone, vm)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloneCell(clone: CloneEntity, vm: LauncherViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(
            onClick = { vm.launch(clone) },
            onLongClick = { menuOpen = true },
        ),
    ) {
        // Icon rendering is engine-agnostic: the badged bitmap is generated at
        // creation; here we show the label badge placeholder until icons load.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            Text(
                clone.label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Text(
            clone.label + if (clone.locked) " 🔒" else "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameOpen = true })
            DropdownMenuItem(
                text = { Text(if (clone.locked) "Remove app lock" else "App lock") },
                onClick = { menuOpen = false; vm.toggleLock(clone) },
            )
            DropdownMenuItem(
                text = { Text(if (clone.clipboardSharing) "Disable clipboard sharing" else "Allow clipboard sharing") },
                onClick = { menuOpen = false; vm.toggleClipboard(clone) },
            )
            DropdownMenuItem(text = { Text("Reset data") }, onClick = { menuOpen = false; vm.reset(clone) })
            DropdownMenuItem(text = { Text("Delete clone") }, onClick = { menuOpen = false; confirmDelete = true })
        }
    }

    if (renameOpen) {
        RenameDialog(clone, onDismiss = { renameOpen = false }, onConfirm = { vm.rename(clone, it); renameOpen = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${clone.label}?") },
            text = { Text("This wipes only this clone's data. The original app and other clones are untouched.") },
            confirmButton = { TextButton(onClick = { vm.delete(clone); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RenameDialog(clone: CloneEntity, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(clone.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename clone") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
