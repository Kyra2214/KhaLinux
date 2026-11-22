package com.khalinux.android.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.khalinux.android.R

/** Tela do gerenciador de arquivos do home persistente. */
@Composable
fun FilesScreen(state: FilesUiState, viewModel: FilesViewModel) {
    val text = Color(0xFFE8F0F2)
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.action_files), style = MaterialTheme.typography.headlineMedium, color = text)
        Text("/home/khalinux/" + state.path, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall)
        state.message?.let { Text(it, color = Color(0xFFFFB4AB)) }
        state.notice?.let { Text(it, color = Color(0xFF80CBC4)) }
        state.storage?.let { Text(it, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = viewModel::cleanStorage, enabled = state.reclaimableBytes > 0, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.storage_clean)) }
        OutlinedButton(onClick = viewModel::generateReport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.files_report)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = viewModel::up, enabled = state.path.isNotEmpty()) { Text(stringResource(R.string.files_up)) }
            OutlinedButton(onClick = viewModel::newFile) { Text(stringResource(R.string.files_new_file)) }
            OutlinedButton(onClick = viewModel::askNewFolder) { Text(stringResource(R.string.files_new_folder)) }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.entries, key = { it.relativePath }) { entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { viewModel.enter(entry) }, modifier = Modifier.weight(1f)) {
                        Text((if (entry.directory) "📁 " else "") + entry.name + if (entry.directory) "" else " · ${entry.size} B", maxLines = 1)
                    }
                    OutlinedButton(onClick = { viewModel.askRename(entry) }) { Text("✎") }
                    OutlinedButton(onClick = { viewModel.askDelete(entry) }) { Text("✕") }
                }
            }
        }
        OutlinedButton(onClick = viewModel::close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pkg_back)) }
    }

    state.openFile?.let { file ->
        AlertDialog(
            onDismissRequest = viewModel::closeOpenFile,
            title = { Text(stringResource(R.string.files_editor_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = file.path,
                        onValueChange = { viewModel.editOpenFile(it, file.content) },
                        enabled = file.isNew,
                        label = { Text(stringResource(R.string.files_path)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = file.content,
                        onValueChange = { viewModel.editOpenFile(file.path, it) },
                        modifier = Modifier.height(240.dp),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace)
                    )
                    state.message?.let { Text(it, color = Color(0xFFFFB4AB)) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::saveOpenFile) { Text(stringResource(R.string.script_save)) } },
            dismissButton = { TextButton(onClick = viewModel::closeOpenFile) { Text(stringResource(R.string.pkg_cancel)) } }
        )
    }
    if (state.newFolderDialog) {
        NameDialog(stringResource(R.string.files_new_folder), state.newName, viewModel::setNewName, viewModel::createFolder, viewModel::cancelNewFolder)
    }
    if (state.renaming != null) {
        NameDialog(stringResource(R.string.files_rename), state.newName, viewModel::setNewName, viewModel::confirmRename, viewModel::cancelRename)
    }
    state.confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.files_delete_title)) },
            text = { Text(stringResource(if (entry.directory) R.string.files_delete_folder_body else R.string.files_delete_body, entry.name)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.files_delete)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.pkg_cancel)) } }
        )
    }
}

@Composable
private fun NameDialog(title: String, value: String, onChange: (String) -> Unit, onOk: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onChange, singleLine = true) },
        confirmButton = { TextButton(onClick = onOk) { Text(stringResource(R.string.tab_rename_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.pkg_cancel)) } }
    )
}
