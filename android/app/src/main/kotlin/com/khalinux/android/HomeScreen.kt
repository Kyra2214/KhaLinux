package com.khalinux.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khalinux.runtime.TerminalKey
import com.khalinux.runtime.settings.TerminalThemes

/** Seções de topo da navegação inferior. As telas "Mais" ficam agrupadas sob um único item. */
enum class Section { TERMINAL, PACKAGES, TOOLKIT, LABS, RED, BLUE, TUTOR, FILES, DIAGNOSTICS }

private val QUICK_COMMANDS = listOf("ls -la", "uname -a", "cat /etc/os-release", "whoami", "df -h", "apt update")

// ---------------------------------------------------------------------------------------------
// Navegação inferior
// ---------------------------------------------------------------------------------------------

@Composable
fun KhaNavigationBar(current: Section, enabled: Boolean, onSelect: (Section) -> Unit, onMore: () -> Unit) {
    val moreSelected = current == Section.TUTOR || current == Section.FILES || current == Section.DIAGNOSTICS
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavItem(current == Section.TERMINAL, true, "⌨", stringResource(R.string.nav_terminal)) { onSelect(Section.TERMINAL) }
        NavItem(current == Section.PACKAGES, enabled, "📦", stringResource(R.string.nav_packages)) { onSelect(Section.PACKAGES) }
        NavItem(current == Section.TOOLKIT, enabled, "🛠", stringResource(R.string.nav_tools)) { onSelect(Section.TOOLKIT) }
        NavItem(current == Section.LABS, enabled, "🧪", stringResource(R.string.nav_labs)) { onSelect(Section.LABS) }
        NavItem(current == Section.RED, enabled, "🎯", "RED") { onSelect(Section.RED) }
        NavItem(moreSelected, true, "⋯", stringResource(R.string.nav_more)) { onMore() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    enabled: Boolean,
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        icon = { Text(icon, fontSize = 20.sp) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
        alwaysShowLabel = true
    )
}

/** Folha "Mais": Tutor, Arquivos e Diagnóstico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(onDismiss: () -> Unit, onSelect: (Section) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.more_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            MoreRow("🛡", "BLUE", "Análise defensiva dos cenários e evidências do RED") { onSelect(Section.BLUE) }
            MoreRow("🎓", stringResource(R.string.action_tutor), stringResource(R.string.more_tutor_desc)) { onSelect(Section.TUTOR) }
            MoreRow("📁", stringResource(R.string.action_files), stringResource(R.string.more_files_desc)) { onSelect(Section.FILES) }
            MoreRow("🩺", stringResource(R.string.action_diagnostics), stringResource(R.string.more_diag_desc)) { onSelect(Section.DIAGNOSTICS) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MoreRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tela inicial (Terminal)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: TerminalUiState, viewModel: TerminalViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    var showShortcuts by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeHeader(state)
        when {
            !state.serviceConnected -> CenteredMessage(state.connectionMessage ?: stringResource(R.string.connecting_title), busy = true)
            !state.runtimeInstalled -> InstallCard(
                state = state,
                onRetry = viewModel::startInstall,
                onCancel = viewModel::cancelInstall,
                modifier = Modifier.weight(1f)
            )
            else -> TerminalArea(
                state = state,
                viewModel = viewModel,
                onShortcuts = { showShortcuts = true },
                onSettings = { showSettings = true },
                modifier = Modifier.weight(1f)
            )
        }
    }

    ScriptEditorDialog(state, viewModel)
    if (showSettings) SettingsSheet(state, viewModel) { showSettings = false }
    if (showShortcuts) ShortcutsSheet(state, viewModel) { showShortcuts = false }
}

private data class StatusInfo(val text: String, val color: Color)

@Composable
private fun statusOf(state: TerminalUiState): StatusInfo = when {
    !state.serviceConnected -> StatusInfo(stringResource(R.string.status_connecting).removeSuffix("…"), KhaColors.Warn)
    state.isInstalling -> StatusInfo("${state.installPercent}%", KhaColors.Primary)
    !state.runtimeInstalled && state.installError != null -> StatusInfo(stringResource(R.string.status_failed), KhaColors.Error)
    !state.runtimeInstalled -> StatusInfo(stringResource(R.string.status_not_ready), KhaColors.Warn)
    state.isStarting -> StatusInfo(stringResource(R.string.status_starting), KhaColors.Warn)
    state.sessionId != null -> StatusInfo(stringResource(R.string.status_session), KhaColors.Ok)
    else -> StatusInfo(stringResource(R.string.status_ready), KhaColors.Ok)
}

@Composable
private fun HomeHeader(state: TerminalUiState) {
    val status = statusOf(state)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Debian ARM64 · PRoot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(status.color))
                Text(status.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, busy: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) CircularProgressIndicator()
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Instalação
// ---------------------------------------------------------------------------------------------

@Composable
private fun InstallCard(state: TerminalUiState, onRetry: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.install_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.install_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StepRow(stringResource(R.string.install_step_proot), done = state.prootInstalled)
                StepRow(stringResource(R.string.install_step_rootfs), done = state.rootfsInstalled)
                Text(
                    text = state.installPhase?.label ?: stringResource(R.string.install_preparing),
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { state.installPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${state.installPercent}%" + (state.installDetail?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isInstalling) {
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.install_cancel)) }
                    } else {
                        Button(onClick = onRetry) { Text(stringResource(R.string.install_retry)) }
                    }
                }
            }
        }
        state.installError?.let { message ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.install_failed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (done) "✓" else "○", color = if (done) KhaColors.Ok else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// Terminal
// ---------------------------------------------------------------------------------------------

@Composable
private fun TerminalArea(
    state: TerminalUiState,
    viewModel: TerminalViewModel,
    onShortcuts: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = TerminalThemes.byIndex(state.themeIndex)
    val outputScroll = rememberScrollState()
    val terminalText = remember(state.terminalSnapshot, state.themeIndex) {
        state.terminalSnapshot.toAnnotatedString(Color(theme.foreground), Color(theme.background))
    }
    LaunchedEffect(state.output) { outputScroll.animateScrollTo(outputScroll.maxValue) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabsRow(state, viewModel)

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            color = Color(theme.background),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(outputScroll).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.output.isEmpty()) {
                    EmptyTerminal(onPick = viewModel::updateCommand)
                } else {
                    Text(
                        text = terminalText,
                        color = Color(theme.foreground),
                        fontFamily = FontFamily.Monospace,
                        fontSize = state.fontSizeSp.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(1.dp))
            }
        }

        if (state.isInteractive) ExtraKeysRow(state, viewModel)

        CommandBar(state, viewModel)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val compact = PaddingValues(horizontal = 6.dp)
            OutlinedButton(
                onClick = viewModel::openInteractiveShell,
                enabled = state.serviceConnected && state.runtimeInstalled && !state.isRunning,
                contentPadding = compact,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.action_open_shell), fontSize = 12.sp, maxLines = 1) }
            OutlinedButton(
                onClick = viewModel::cancelCommand,
                enabled = state.sessionId != null,
                contentPadding = compact,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.action_cancel), fontSize = 12.sp, maxLines = 1) }
            OutlinedButton(onClick = onShortcuts, contentPadding = compact, modifier = Modifier.weight(1f)) {
                Text("★ " + stringResource(R.string.action_shortcuts), fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(onClick = onSettings, contentPadding = compact, modifier = Modifier.weight(1f)) {
                Text("⚙ " + stringResource(R.string.action_settings), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyTerminal(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.terminal_empty_title),
            color = Color(0xFFE8F0F2),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.terminal_empty_hint),
            color = Color(0xFF8A9AA0),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_COMMANDS.forEach { command ->
                AssistChip(onClick = { onPick(command) }, label = { Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsRow(state: TerminalUiState, viewModel: TerminalViewModel) {
    var renaming by remember { mutableStateOf<String?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.tabs.forEach { tab ->
            val active = tab.id == state.activeTabId
            FilterChip(
                selected = active,
                onClick = {
                    if (active) {
                        renaming = tab.id
                        draftTitle = tab.title
                    } else {
                        viewModel.selectTab(tab.id)
                    }
                },
                label = { Text(tab.title + if (tab.running) " ●" else "", maxLines = 1) },
                trailingIcon = if (active && state.tabs.size > 1) {
                    { Text("✕", modifier = Modifier.clickable { viewModel.closeTab(tab.id) }.padding(horizontal = 2.dp)) }
                } else null
            )
        }
        AssistChip(onClick = viewModel::addTab, enabled = state.canAddTab, label = { Text("＋") })
    }
    val target = renaming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.tab_rename_title)) },
            text = { OutlinedTextField(value = draftTitle, onValueChange = { draftTitle = it.take(24) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameTab(target, draftTitle)
                    renaming = null
                }) { Text(stringResource(R.string.tab_rename_ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraKeysRow(state: TerminalUiState, viewModel: TerminalViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(selected = state.ctrlArmed, onClick = viewModel::toggleCtrl, label = { Text(stringResource(R.string.key_ctrl)) })
        FilterChip(selected = state.altArmed, onClick = viewModel::toggleAlt, label = { Text(stringResource(R.string.key_alt)) })
        TerminalKey.values().forEach { key ->
            AssistChip(onClick = { viewModel.sendKey(key) }, label = { Text(key.label) })
        }
    }
}

@Composable
private fun CommandBar(state: TerminalUiState, viewModel: TerminalViewModel) {
    val canSend = if (state.isInteractive) {
        state.command.isNotEmpty() && !state.isSendingInput
    } else {
        state.serviceConnected && state.runtimeInstalled && !state.isRunning && state.command.isNotBlank()
    }
    val submit: () -> Unit = { if (state.isInteractive) viewModel.sendInput() else viewModel.runCommand() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = state.command,
            onValueChange = viewModel::updateCommand,
            modifier = Modifier.weight(1f).testTag("command_input"),
            singleLine = true,
            placeholder = {
                Text(
                    if (state.isInteractive) stringResource(R.string.label_shell_input) else stringResource(R.string.input_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = { Text("$", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) submit() })
        )
        Button(
            onClick = submit,
            enabled = canSend,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(52.dp)
        ) { Text(if (state.isSendingInput) "…" else "▶", fontSize = 18.sp) }
    }
}

// ---------------------------------------------------------------------------------------------
// Ajustes e atalhos (folhas inferiores)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: TerminalUiState, viewModel: TerminalViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.sheet_settings_title), style = MaterialTheme.typography.titleMedium)

            SectionLabel(stringResource(R.string.label_theme))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalThemes.all.forEach { option ->
                    FilterChip(
                        selected = option.index == state.themeIndex,
                        onClick = { viewModel.selectTheme(option.index) },
                        label = { Text(option.label) },
                        leadingIcon = {
                            Box(
                                Modifier.size(14.dp).clip(CircleShape)
                                    .background(Color(option.background))
                            )
                        }
                    )
                }
            }

            SectionLabel(stringResource(R.string.label_font_size, state.fontSizeSp.toInt()))
            Slider(
                value = state.fontSizeSp,
                onValueChange = viewModel::updateFontSize,
                onValueChangeFinished = viewModel::saveTerminalSettings,
                valueRange = 10f..22f,
                steps = 11
            )

            Stepper(stringResource(R.string.label_rows_short), state.terminalRows.toString(),
                onMinus = { viewModel.adjustTerminalRows(-1) }, onPlus = { viewModel.adjustTerminalRows(1) })
            Stepper(stringResource(R.string.label_columns_short), state.terminalColumns.toString(),
                onMinus = { viewModel.adjustTerminalColumns(-8) }, onPlus = { viewModel.adjustTerminalColumns(8) })

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onMinus, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
        Text(value, modifier = Modifier.width(52.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = onPlus, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutsSheet(state: TerminalUiState, viewModel: TerminalViewModel, onDismiss: () -> Unit) {
    val canRun = state.serviceConnected && state.runtimeInstalled && !state.isRunning
    val clipboard = LocalClipboardManager.current
    var copyMessage by remember { mutableStateOf<String?>(null) }
    val inputDone = stringResource(R.string.copy_input_done)
    val outputDone = stringResource(R.string.copy_output_done)
    val nothingToCopy = stringResource(R.string.copy_empty)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.sheet_shortcuts_title), style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = viewModel::toggleFavoriteCommand,
                    enabled = !state.isInteractive && state.command.isNotBlank()
                ) {
                    Text(
                        if (state.command.trim() in state.favoriteCommands) stringResource(R.string.fav_remove_current)
                        else stringResource(R.string.fav_add_current)
                    )
                }
                OutlinedButton(onClick = viewModel::saveOutputNote, enabled = state.output.isNotBlank()) {
                    Text(stringResource(R.string.note_save))
                }
            }
            // Copiar para a área de transferência: entrada (campo de comando) e saída (texto visível da aba ativa,
            // que já inclui os comandos enviados). O conteúdo é copiado como está; nada é gravado em disco.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(onClick = {
                    val text = state.command
                    if (text.isEmpty()) {
                        copyMessage = nothingToCopy
                    } else {
                        clipboard.setText(AnnotatedString(text))
                        copyMessage = inputDone
                    }
                }) { Text(stringResource(R.string.copy_input)) }
                OutlinedButton(onClick = {
                    val text = state.output.trimEnd()
                    if (text.isEmpty()) {
                        copyMessage = nothingToCopy
                    } else {
                        clipboard.setText(AnnotatedString(text))
                        copyMessage = outputDone
                    }
                }) { Text(stringResource(R.string.copy_output)) }
            }
            copyMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            state.noteMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

            Divider()
            SectionLabel(stringResource(R.string.section_favorites))
            if (state.favoriteCommands.isEmpty()) EmptyLine(stringResource(R.string.empty_favorites))
            state.favoriteCommands.take(20).forEach { favorite ->
                CommandRow(
                    text = favorite,
                    onPick = { viewModel.restoreCommandFromHistory(favorite); onDismiss() },
                    trailing = "✕",
                    onTrailing = { viewModel.removeFavoriteCommand(favorite) }
                )
            }

            Divider()
            SectionLabel(stringResource(R.string.section_history))
            if (state.commandHistory.isEmpty()) EmptyLine(stringResource(R.string.empty_history))
            state.commandHistory.take(10).forEach { entry ->
                CommandRow(text = entry.command, onPick = { viewModel.restoreCommandFromHistory(entry.command); onDismiss() })
            }

            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel(stringResource(R.string.section_scripts)) }
                TextButton(onClick = viewModel::newScript) { Text(stringResource(R.string.script_new)) }
            }
            if (state.scripts.isEmpty()) EmptyLine(stringResource(R.string.empty_scripts))
            state.scripts.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                    TextButton(onClick = { viewModel.runScript(name); onDismiss() }, enabled = canRun) { Text("▶") }
                    TextButton(onClick = { viewModel.editScript(name) }) { Text("✎") }
                    TextButton(onClick = { viewModel.deleteScript(name) }) { Text("✕") }
                }
            }
            state.scriptMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CommandRow(text: String, onPick: () -> Unit, trailing: String? = null, onTrailing: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onPick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        if (trailing != null) TextButton(onClick = onTrailing) { Text(trailing) }
    }
}

@Composable
private fun ScriptEditorDialog(state: TerminalUiState, viewModel: TerminalViewModel) {
    val editor = state.scriptEditor ?: return
    AlertDialog(
        onDismissRequest = viewModel::cancelScriptEditor,
        title = { Text(stringResource(R.string.script_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { viewModel.updateScriptEditor(it, editor.content) },
                    label = { Text(stringResource(R.string.script_name)) },
                    enabled = editor.isNew,
                    singleLine = true
                )
                OutlinedTextField(
                    value = editor.content,
                    onValueChange = { viewModel.updateScriptEditor(editor.name, it) },
                    label = { Text(stringResource(R.string.script_content)) },
                    modifier = Modifier.height(220.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                )
                state.scriptMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveScript) { Text(stringResource(R.string.script_save)) } },
        dismissButton = { TextButton(onClick = viewModel::cancelScriptEditor) { Text(stringResource(R.string.action_cancel)) } }
    )
}
