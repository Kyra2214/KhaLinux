package com.khalinux.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.khalinux.runtime.TerminalSnapshot
import com.khalinux.runtime.TerminalStyle
import com.khalinux.runtime.tutor.TutorContext

/**
 * Raiz da UI: conteúdo da seção atual + navegação inferior persistente.
 * As telas secundárias (Pacotes, Ferramentas, Labs, Tutor, Arquivos, Diagnóstico) continuam controladas pelos
 * respectivos ViewModels (flag `visible`); a barra inferior apenas fecha uma e abre outra.
 */
@Composable
fun KhaLinuxApp(
    viewModel: TerminalViewModel,
    packagesViewModel: com.khalinux.android.packages.PackagesViewModel,
    toolkitViewModel: com.khalinux.android.toolkit.ToolkitViewModel,
    labsViewModel: com.khalinux.android.labs.LabsViewModel,
    filesViewModel: com.khalinux.android.files.FilesViewModel,
    tutorViewModel: com.khalinux.android.tutor.TutorViewModel
) {
    // O release com targetSdk 28 pode montar o Compose antes de o ViewTreeLifecycleOwner
    // estar disponível; collectAsStateWithLifecycle() então lança
    // "LocalLifecycleOwner not present" na primeira composição. Os StateFlows continuam
    // ativos nos ViewModels, portanto collectAsState() é seguro para esta raiz da UI.
    val state by viewModel.uiState.collectAsState()
    val packagesState by packagesViewModel.uiState.collectAsState()
    val toolkitState by toolkitViewModel.uiState.collectAsState()
    val labsState by labsViewModel.uiState.collectAsState()
    val filesState by filesViewModel.uiState.collectAsState()
    val tutorState by tutorViewModel.uiState.collectAsState()
    var showMore by remember { mutableStateOf(false) }

    val section = when {
        packagesState.visible -> Section.PACKAGES
        toolkitState.visible -> Section.TOOLKIT
        tutorState.visible -> Section.TUTOR
        labsState.visible -> Section.LABS
        filesState.visible -> Section.FILES
        state.showDiagnostics -> Section.DIAGNOSTICS
        else -> Section.TERMINAL
    }

    val navigate: (Section) -> Unit = { target ->
        if (target != section) {
            packagesViewModel.close()
            toolkitViewModel.close()
            labsViewModel.close()
            tutorViewModel.close()
            filesViewModel.close()
            viewModel.closeDiagnostics()
            when (target) {
                Section.TERMINAL -> Unit
                Section.PACKAGES -> packagesViewModel.open()
                Section.TOOLKIT -> toolkitViewModel.open()
                Section.LABS -> labsViewModel.open()
                Section.TUTOR -> tutorViewModel.open()
                Section.FILES -> filesViewModel.open()
                Section.DIAGNOSTICS -> viewModel.openDiagnostics()
            }
        }
    }

    KhaLinuxTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = KhaColors.Background) {
            // Com o teclado aberto a barra inferior some (libera espaço para o terminal e evita lacuna sob o IME).
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (imeVisible) Modifier else Modifier.consumeWindowInsets(WindowInsets.navigationBars))
                ) {
                    when (section) {
                        Section.PACKAGES -> com.khalinux.android.packages.PackagesScreen(packagesState, packagesViewModel)
                        Section.TOOLKIT -> com.khalinux.android.toolkit.ToolkitScreen(
                            toolkitState,
                            toolkitViewModel,
                            onUseInTerminal = { command ->
                                toolkitViewModel.close()
                                viewModel.updateCommand(command)
                            }
                        )
                        Section.TUTOR -> com.khalinux.android.tutor.TutorScreen(tutorState, tutorViewModel)
                        Section.LABS -> com.khalinux.android.labs.LabsScreen(
                            labsState,
                            labsViewModel,
                            onAskTutor = { lab, exercise ->
                                tutorViewModel.openWith(TutorContext(lab.id, exercise.title, exercise.instructions))
                            }
                        )
                        Section.FILES -> com.khalinux.android.files.FilesScreen(filesState, filesViewModel)
                        Section.DIAGNOSTICS -> DiagnosticsScreen(
                            state = state,
                            onRun = viewModel::runDiagnostics,
                            onClose = viewModel::closeDiagnostics,
                            onConfirm = viewModel::confirmManualDiagnostic
                        )
                        Section.TERMINAL -> HomeScreen(state, viewModel)
                    }
                }
                if (!imeVisible) {
                    KhaNavigationBar(
                        current = section,
                        enabled = state.serviceConnected && !state.isInstalling,
                        onSelect = navigate,
                        onMore = { showMore = true }
                    )
                }
            }
            if (showMore) {
                MoreSheet(onDismiss = { showMore = false }, onSelect = { target ->
                    showMore = false
                    navigate(target)
                })
            }
        }
    }
}

internal fun TerminalSnapshot.toAnnotatedString(defaultForeground: Color, defaultBackground: Color): AnnotatedString = buildAnnotatedString {
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
            pushStyle(style.toComposeSpanStyle(defaultForeground, defaultBackground))
            for (index in column until runEnd) append(line[index].text)
            pop()
            column = runEnd
        }
        if (rowIndex != range.last) append('\n')
    }
}

private fun TerminalStyle.toComposeSpanStyle(defaultForeground: Color, defaultBackground: Color): SpanStyle {
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
