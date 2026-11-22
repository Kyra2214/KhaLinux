package com.khalinux.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.khalinux.runtime.diagnostics.DiagnosticId
import com.khalinux.runtime.diagnostics.DiagnosticItem
import com.khalinux.runtime.diagnostics.DiagnosticStatus

/** Tela do ponto de teste físico: OK/falha por item, relatório copiável e confirmações manuais. */
@Composable
fun DiagnosticsScreen(
    state: TerminalUiState,
    onRun: () -> Unit,
    onClose: () -> Unit,
    onConfirm: (DiagnosticId, Boolean) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.action_diagnostics), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8F0F2))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.diagnostics.items.forEach { item ->
                DiagnosticRow(item, onConfirm)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onRun, enabled = !state.diagnosticsRunning, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (state.diagnosticsRunning) R.string.diag_running else R.string.diag_run_again))
            }
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(state.diagnostics.toText())) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.diag_copy)) }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.diag_back)) }
        }
    }
}

@Composable
private fun DiagnosticRow(item: DiagnosticItem, onConfirm: (DiagnosticId, Boolean) -> Unit) {
    val manual = item.id == DiagnosticId.ROTATION || item.id == DiagnosticId.BACKGROUND
    val color = when (item.status) {
        DiagnosticStatus.OK -> Color(0xFF81C784)
        DiagnosticStatus.FAILED -> Color(0xFFE57373)
        DiagnosticStatus.MANUAL -> Color(0xFFFFB74D)
        else -> Color(0xFF8A9AA0)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.title, color = Color(0xFFE8F0F2), style = MaterialTheme.typography.bodyLarge)
        Text(item.status.label.uppercase(), color = color, style = MaterialTheme.typography.labelMedium)
        item.detail?.let { Text(it, color = Color(0xFF8A9AA0), style = MaterialTheme.typography.bodySmall) }
        if (manual && item.status != DiagnosticStatus.PENDING && item.status != DiagnosticStatus.RUNNING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onConfirm(item.id, true) }) { Text(stringResource(R.string.diag_worked)) }
                OutlinedButton(onClick = { onConfirm(item.id, false) }) { Text(stringResource(R.string.diag_failed)) }
            }
        }
    }
}
