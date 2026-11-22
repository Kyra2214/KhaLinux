package com.khalinux.android.toolkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khalinux.android.R
import java.text.DateFormat
import java.util.Date

/** Tela do Cyber Toolkit: ferramentas por grupo, uso autorizado e verificação no aparelho. */
@Composable
fun ToolkitScreen(state: ToolkitUiState, viewModel: ToolkitViewModel, onUseInTerminal: (String) -> Unit = {}) {
    val text = Color(0xFFE8F0F2)
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.action_toolkit), style = MaterialTheme.typography.headlineMedium, color = text)
        if (!state.available) {
            Text(stringResource(R.string.pkg_unavailable), color = text)
            OutlinedButton(onClick = viewModel::close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pkg_back)) }
            return@Column
        }
        Text(stringResource(R.string.tk_policy), color = Color(0xFFFFE082), style = MaterialTheme.typography.bodySmall)
        state.busy?.let { Text(stringResource(R.string.tk_busy, it), color = Color(0xFF80CBC4)) }
        state.message?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.fillMaxWidth()) }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Favoritas primeiro; dentro de cada metade a ordem do catálogo é preservada.
            items(state.tools.sortedByDescending { it.tool.id in state.favoriteToolIds }, key = { it.tool.id }) { status ->
                val tool = status.tool
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${tool.title} · ${tool.group.id}", color = text, style = MaterialTheme.typography.titleMedium)
                    Text(tool.summary, color = text, style = MaterialTheme.typography.bodySmall)
                    if (tool.rootLimitations.isNotEmpty()) {
                        Text(stringResource(R.string.pkg_limitations, tool.rootLimitations.joinToString(" ")), color = text, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        status.installedVersion?.let { stringResource(R.string.pkg_installed_version, it) } ?: stringResource(R.string.tk_not_installed),
                        color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall
                    )
                    val check = status.lastVerification
                    Text(
                        if (check == null) stringResource(R.string.pkg_validation_unverified) else {
                            val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(check.epochMillis))
                            stringResource(if (check.success) R.string.tk_check_ok else R.string.tk_check_failed, whenText)
                        },
                        color = Color(0xFFFFE082), style = MaterialTheme.typography.bodySmall
                    )
                    if (tool.requiresAuthorization) {
                        Text(
                            stringResource(if (status.allowed) R.string.tk_scope_ok else R.string.tk_scope_needed),
                            color = Color(0xFFFFE082), style = MaterialTheme.typography.bodySmall
                        )
                    }
                    // Duas linhas de botões com a mesma largura: evita quebrar "Favoritar" em duas linhas.
                    val compact = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.verify(tool.id) },
                            enabled = state.busy == null && status.installedVersion != null,
                            contentPadding = compact,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.tk_verify), maxLines = 1) }
                        // Integração (7.7): leva o comando da ferramenta ao terminal; ferramentas que exigem autorização só depois do escopo confirmado.
                        OutlinedButton(
                            onClick = { onUseInTerminal(tool.commands.first() + " ") },
                            enabled = status.installedVersion != null && tool.commands.isNotEmpty() && (!tool.requiresAuthorization || status.allowed),
                            contentPadding = compact,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.tk_use_in_terminal), maxLines = 1) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFavorite(tool.id) },
                            contentPadding = compact,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(if (tool.id in state.favoriteToolIds) R.string.fav_tool_on else R.string.fav_tool_off), maxLines = 1)
                        }
                        if (tool.requiresAuthorization) {
                            if (status.allowed) OutlinedButton(onClick = { viewModel.revoke(tool.id) }, contentPadding = compact, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tk_revoke), maxLines = 1) }
                            else OutlinedButton(onClick = { viewModel.askScope(tool.id) }, contentPadding = compact, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tk_confirm_scope), maxLines = 1) }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = viewModel::close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pkg_back)) }
    }
    if (state.scopeDialogFor != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelScope,
            title = { Text(stringResource(R.string.tk_scope_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tk_scope_body))
                    OutlinedTextField(
                        value = state.scopeText,
                        onValueChange = viewModel::setScopeText,
                        label = { Text(stringResource(R.string.tk_scope_hint)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmScope) { Text(stringResource(R.string.tk_scope_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelScope) { Text(stringResource(R.string.pkg_cancel)) } }
        )
    }
}
