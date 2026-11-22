package com.khalinux.android.tutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import com.khalinux.runtime.tutor.TutorReplyKind

private val TextColor = Color(0xFFE8F0F2)
private val Accent = Color(0xFF80CBC4)
private val Warn = Color(0xFFFFB74D)

/** Tela do tutor didático offline: pergunta e resposta em texto, com aviso de que não há IA nem rede. */
@Composable
fun TutorScreen(state: TutorUiState, viewModel: TutorViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.action_tutor), style = MaterialTheme.typography.headlineMedium, color = TextColor)
        Text(stringResource(R.string.tutor_disclaimer), color = Warn, style = MaterialTheme.typography.bodySmall)
        if (state.context.hasExercise) {
            Text(stringResource(R.string.tutor_context, state.context.exerciseTitle.orEmpty()), color = Accent, style = MaterialTheme.typography.bodySmall)
        }
        if (state.unavailable) Text(stringResource(R.string.tutor_unavailable), color = Color(0xFFFFB4AB))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.messages) { message ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("› " + message.question, color = Accent, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        message.reply.text,
                        color = if (message.reply.kind == TutorReplyKind.OUT_OF_SCOPE || message.reply.kind == TutorReplyKind.ANSWER_WITHHELD) Warn else TextColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                    message.reply.doc?.let { Text(stringResource(R.string.tutor_doc, it), color = Accent, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::setInput,
            label = { Text(stringResource(R.string.tutor_question)) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = viewModel::send, enabled = state.input.isNotBlank()) { Text(stringResource(R.string.tutor_ask)) }
            OutlinedButton(onClick = viewModel::clear, enabled = state.messages.isNotEmpty()) { Text(stringResource(R.string.tutor_clear)) }
            OutlinedButton(onClick = viewModel::close) { Text(stringResource(R.string.pkg_back)) }
        }
    }
}
