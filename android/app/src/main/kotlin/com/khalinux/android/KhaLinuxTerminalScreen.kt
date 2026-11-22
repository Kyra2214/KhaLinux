package com.khalinux.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun KhaLinuxApp(viewModel: TerminalViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val outputScroll = rememberScrollState()

    LaunchedEffect(state.output) {
        outputScroll.animateScrollTo(outputScroll.maxValue)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF80CBC4),
            background = Color(0xFF101418),
            surface = Color(0xFF182126)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101418)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("KhaLinux", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8F0F2))
                Text(
                    text = when {
                        !state.serviceConnected -> state.connectionMessage ?: "Conectando ao Service local…"
                        state.runtimeInstalled -> "Runtime Linux instalado"
                        state.rootfsInstalled && state.prootInstalled -> "RootFS e PRoot instalados"
                        state.rootfsInstalled -> "RootFS instalado; PRoot pendente"
                        state.prootInstalled -> "PRoot instalado; RootFS pendente"
                        else -> "Ambiente Linux ainda não instalado"
                    } + when {
                        state.isStarting -> " · iniciando sessão…"
                        state.sessionId != null -> " · sessão ${state.sessionId}"
                        else -> ""
                    },
                    color = Color(0xFF80CBC4),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (state.serviceConnected && !state.rootfsInstalled) {
                    OutlinedButton(
                        onClick = viewModel::installRootfs,
                        enabled = !state.isInstallingRootfs,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isInstallingRootfs) "Instalando Debian…" else "Instalar Debian ARM64")
                    }
                }

                if (state.serviceConnected && state.rootfsInstalled && !state.prootInstalled) {
                    OutlinedButton(
                        onClick = viewModel::installProot,
                        enabled = !state.isInstallingProot,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isInstallingProot) "Instalando PRoot…" else "Instalar PRoot ARM64")
                    }
                }

                state.rootfsInstallProgress?.let { progress ->
                    Text(progress, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall)
                }
                state.rootfsInstallMessage?.let { message ->
                    Text(message, color = Color(0xFFE8F0F2), style = MaterialTheme.typography.bodySmall)
                }
                state.prootInstallProgress?.let { progress ->
                    Text(progress, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall)
                }
                state.prootInstallMessage?.let { message ->
                    Text(message, color = Color(0xFFE8F0F2), style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = state.command,
                    onValueChange = viewModel::updateCommand,
                    label = { Text(if (state.isInteractive) "Entrada do shell" else "Comando Bash") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = if (state.isInteractive) viewModel::sendInput else viewModel::runCommand,
                        enabled = if (state.isInteractive) {
                            state.command.isNotEmpty() && !state.isSendingInput
                        } else {
                            state.serviceConnected && state.runtimeInstalled && !state.isRunning && state.command.isNotBlank()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state.isSendingInput) "Enviando…" else if (state.isInteractive) "Enviar" else "Executar")
                    }
                    OutlinedButton(
                        onClick = viewModel::openInteractiveShell,
                        enabled = state.serviceConnected && state.runtimeInstalled && !state.isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Abrir shell")
                    }
                    OutlinedButton(
                        onClick = viewModel::cancelCommand,
                        enabled = state.sessionId != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = Color(0xFF0B0F12),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(outputScroll).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.output.isEmpty()) {
                            Text(
                                "A saída dos comandos aparecerá aqui.",
                                color = Color(0xFF8A9AA0),
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = state.output,
                                color = Color(0xFFE8F0F2),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}
