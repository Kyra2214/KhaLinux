package com.khalinux.android.packages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khalinux.android.R
import com.khalinux.runtime.packages.PackageValidation
import java.text.DateFormat
import java.util.Date

/** Tela do gerenciador de pacotes: busca, categorias, instalar/remover/atualizar, histórico. */
@Composable
fun PackagesScreen(state: PackagesUiState, viewModel: PackagesViewModel) {
    val text = Color(0xFFE8F0F2)
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.action_packages), style = MaterialTheme.typography.headlineMedium, color = text)
        if (!state.available) {
            Text(stringResource(R.string.pkg_unavailable), color = text)
            OutlinedButton(onClick = viewModel::close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pkg_back)) }
            return@Column
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.pkg_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.category == null,
                onClick = { viewModel.setCategory(null) },
                label = { Text(stringResource(R.string.pkg_all_categories)) }
            )
            state.categories.forEach { (category, count) ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { viewModel.setCategory(category) },
                    label = { Text("${category.id} ($count)") }
                )
            }
        }
        state.busy?.let { busy ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pkg_busy, busy), color = Color(0xFF80CBC4), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.pkg_cancel)) }
            }
        }
        state.message?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.fillMaxWidth()) }
        if (state.updateCount > 0) {
            Button(onClick = { viewModel.update(null) }, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pkg_update_all, state.updateCount))
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.rows.isEmpty()) item { Text(stringResource(R.string.pkg_empty), color = text) }
            items(state.rows, key = { it.manifest.name }) { row ->
                val m = row.manifest
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${m.title} · ${m.version}", color = text, style = MaterialTheme.typography.titleMedium)
                    Text(m.description, color = text, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(
                            when (m.validation) {
                                PackageValidation.VERIFIED -> R.string.pkg_validation_verified
                                PackageValidation.UNVERIFIED -> R.string.pkg_validation_unverified
                                PackageValidation.KNOWN_LIMITED -> R.string.pkg_validation_limited
                            }
                        ),
                        color = Color(0xFFFFE082),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (m.limitations.isNotEmpty()) {
                        Text(stringResource(R.string.pkg_limitations, m.limitations.joinToString(" ")), color = text, style = MaterialTheme.typography.bodySmall)
                    }
                    row.installedVersion?.let { Text(stringResource(R.string.pkg_installed_version, it), color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall) }
                    row.updateTo?.let { Text(stringResource(R.string.pkg_update_to, it), color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val idle = state.busy == null
                        if (row.installedVersion == null) {
                            Button(onClick = { viewModel.install(m.name) }, enabled = idle) { Text(stringResource(R.string.pkg_install)) }
                        } else {
                            if (row.updateTo != null) Button(onClick = { viewModel.update(m.name) }, enabled = idle) { Text(stringResource(R.string.pkg_update)) }
                            OutlinedButton(onClick = { viewModel.remove(m.name) }, enabled = idle) { Text(stringResource(R.string.pkg_remove)) }
                        }
                    }
                }
            }
            if (state.showHistory) items(state.history) { entry ->
                val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.epochMillis))
                Text(
                    "$whenText · ${entry.action.id} ${entry.packageName} ${entry.version ?: ""} · ${if (entry.success) "ok" else "falha: ${entry.detail}"}",
                    color = text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::toggleHistory, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (state.showHistory) R.string.pkg_hide_history else R.string.pkg_history))
            }
            OutlinedButton(onClick = viewModel::close, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.pkg_back)) }
        }
    }
}
