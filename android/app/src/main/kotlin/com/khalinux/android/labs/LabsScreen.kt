package com.khalinux.android.labs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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

private val TextColor = Color(0xFFE8F0F2)
private val Accent = Color(0xFF80CBC4)
private val Warn = Color(0xFFFFE082)

/** Tela de Laboratórios: trilhas com pré-requisitos, exercícios com dicas, resposta e pontuação persistida. */
@Composable
fun LabsScreen(state: LabsUiState, viewModel: LabsViewModel, onAskTutor: (com.khalinux.runtime.labs.LabDescriptor, com.khalinux.runtime.labs.LabExercise) -> Unit = { _, _ -> }) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.action_labs), style = MaterialTheme.typography.headlineMedium, color = TextColor)
        if (!state.available) {
            Text(stringResource(R.string.pkg_unavailable), color = TextColor)
            OutlinedButton(onClick = viewModel::close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pkg_back)) }
            return@Column
        }
        Text(stringResource(R.string.lab_policy), color = Warn, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.lab_points, state.totalPoints), color = Accent)
        state.message?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.fillMaxWidth()) }
        val open = state.openLab
        if (open == null) LabList(state, viewModel) else LabDetail(state, open, viewModel, onAskTutor)
    }
}

@Composable
private fun ColumnScope.LabList(state: LabsUiState, viewModel: LabsViewModel) {
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.rows, key = { it.lab.id }) { row ->
            val lab = row.lab
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${lab.title} · ${lab.track.id} · ${lab.difficulty.id}", color = TextColor, style = MaterialTheme.typography.titleMedium)
                Text(lab.summary, color = TextColor, style = MaterialTheme.typography.bodySmall)
                Text(
                    when {
                        row.completed -> stringResource(R.string.lab_status_completed, row.points)
                        row.locked -> stringResource(R.string.lab_status_locked, row.missing.joinToString(", "))
                        else -> stringResource(R.string.lab_status_available, row.points)
                    },
                    color = Accent, style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { viewModel.openLab(lab.id) }, enabled = !row.locked) { Text(stringResource(R.string.lab_open)) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = viewModel::close) { Text(stringResource(R.string.pkg_back)) }
        OutlinedButton(onClick = viewModel::resetAll) { Text(stringResource(R.string.lab_reset_all)) }
    }
}

@Composable
private fun ColumnScope.LabDetail(
    state: LabsUiState,
    lab: com.khalinux.runtime.labs.LabDescriptor,
    viewModel: LabsViewModel,
    onAskTutor: (com.khalinux.runtime.labs.LabDescriptor, com.khalinux.runtime.labs.LabExercise) -> Unit
) {
    Text("${lab.title} · ${lab.difficulty.id}", color = TextColor, style = MaterialTheme.typography.titleMedium)
    if (lab.services.isNotEmpty()) {
        Text(stringResource(R.string.lab_service_hint), color = Warn, style = MaterialTheme.typography.bodySmall)
        lab.services.forEach {
            Text(stringResource(R.string.lab_service, it.name, it.command), color = Accent, style = MaterialTheme.typography.bodySmall)
        }
    }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(state.exercises, key = { it.exercise.id }) { view ->
            val ex = view.exercise
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${ex.title} · ${ex.difficulty.id}", color = TextColor, style = MaterialTheme.typography.titleSmall)
                Text(ex.instructions, color = TextColor, style = MaterialTheme.typography.bodySmall)
                view.hints.forEachIndexed { index, hint ->
                    Text("${index + 1}. ${hint.text}", color = Warn, style = MaterialTheme.typography.bodySmall)
                }
                if (view.state.solved) {
                    Text(stringResource(R.string.lab_solved, view.state.pointsAwarded), color = Accent)
                } else {
                    OutlinedTextField(
                        value = state.answers[ex.id].orEmpty(),
                        onValueChange = { viewModel.setAnswer(ex.id, it) },
                        label = { Text(stringResource(R.string.lab_answer)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.submit(ex.id) }) { Text(stringResource(R.string.lab_submit)) }
                        val next = ex.hints.getOrNull(view.hints.size)
                        if (next != null) {
                            OutlinedButton(onClick = { viewModel.revealHint(ex.id) }) { Text(stringResource(R.string.lab_hint, next.cost)) }
                        }
                        OutlinedButton(onClick = { onAskTutor(lab, ex) }) { Text(stringResource(R.string.tutor_ask_exercise)) }
                    }
                }
                state.feedback[ex.id]?.let { Text(it, color = Accent, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = viewModel::closeLab) { Text(stringResource(R.string.pkg_back)) }
        OutlinedButton(onClick = { viewModel.resetLab(lab.id) }) { Text(stringResource(R.string.lab_reset_lab)) }
    }
}
