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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.khalinux.runtime.TerminalSnapshot
import com.khalinux.runtime.TerminalStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun KhaLinuxApp(viewModel: TerminalViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val outputScroll = rememberScrollState()
    val terminalText = remember(state.terminalSnapshot) { state.terminalSnapshot.toAnnotatedString() }

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
                        state.rootfsInstalled && state.prootInstalled -> "RootFS e PRoot instalados; runtime pendente"
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

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Fonte do terminal: ${state.fontSizeSp.toInt()} sp",
                        color = Color(0xFF8A9AA0),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = state.fontSizeSp,
                        onValueChange = viewModel::updateFontSize,
                        onValueChangeFinished = viewModel::saveTerminalSettings,
                        valueRange = 10f..22f,
                        steps = 11
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Linhas: ${state.terminalRows}", color = Color(0xFF8A9AA0), modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.adjustTerminalRows(-1) }) { Text("−") }
                        OutlinedButton(onClick = { viewModel.adjustTerminalRows(1) }) { Text("+") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Colunas: ${state.terminalColumns}", color = Color(0xFF8A9AA0), modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { viewModel.adjustTerminalColumns(-8) }) { Text("− 8") }
                        OutlinedButton(onClick = { viewModel.adjustTerminalColumns(8) }) { Text("+ 8") }
                    }
                }

                if (state.commandHistory.isNotEmpty()) {
                    Text("Histórico recente", color = Color(0xFF8A9AA0), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.commandHistory.take(8).forEach { entry ->
                            OutlinedButton(onClick = { viewModel.restoreCommandFromHistory(entry.command) }) {
                                Text(entry.command, maxLines = 1)
                            }
                        }
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
                                text = terminalText,
                                color = Color(0xFFE8F0F2),
                                fontFamily = FontFamily.Monospace,
                                fontSize = state.fontSizeSp.sp,
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


private fun TerminalSnapshot.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    val range = displayRange()
    range.forEach { rowIndex ->
        val line = lines[rowIndex]
        val lastContent = line.indexOfLast { it.text != " " || it.style != TerminalStyle() }
        val cursorColumnInLine = if (cursorVisible && rowIndex == cursorRow && cursorColumn < columns) {
            cursorColumn
        } else {
            -1
        }
        val end = maxOf(lastContent + 1, cursorColumnInLine + 1).coerceIn(0, columns)
        var column = 0
        while (column < end) {
            fun styleAt(index: Int): TerminalStyle {
                val cellStyle = line[index].style
                return if (rowIndex == cursorRow && index == cursorColumnInLine) {
                    cellStyle.copy(inverse = !cellStyle.inverse)
                } else {
                    cellStyle
                }
            }
            val style = styleAt(column)
            var runEnd = column + 1
            while (runEnd < end && styleAt(runEnd) == style) runEnd++
            pushStyle(style.toComposeSpanStyle())
            for (index in column until runEnd) append(line[index].text)
            pop()
            column = runEnd
        }
        if (rowIndex != range.last) append('\n')
    }
}

private fun TerminalStyle.toComposeSpanStyle(): SpanStyle {
    val defaultForeground = Color(0xFFE8F0F2)
    val defaultBackground = Color(0xFF0B0F12)
    var foreground = this.foreground?.let { Color(it) } ?: defaultForeground
    var background = this.background?.let { Color(it) } ?: Color.Transparent
    if (inverse) {
        val swappedForeground = this.background?.let { Color(it) } ?: defaultBackground
        val swappedBackground = this.foreground?.let { Color(it) } ?: defaultForeground
        foreground = swappedForeground
        background = swappedBackground
    }
    if (dim) foreground = foreground.copy(alpha = foreground.alpha * 0.65f)
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strikeThrough) add(TextDecoration.LineThrough)
    }
    val decoration = when (decorations.size) {
        0 -> null
        1 -> decorations.first()
        else -> TextDecoration.combine(decorations)
    }
    return SpanStyle(
        color = foreground,
        background = background,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = decoration
    )
}
