package com.khalinux.android.red

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun RedScreen(state: RedUiState, viewModel: RedViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val hotspotPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startNetworkLab()
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("ÁREA RED", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                Text("Operações práticas em laboratório autorizado.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = viewModel::refreshHistory) { Text("Histórico") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ESCOPO CONTROLADO", style = MaterialTheme.typography.labelLarge)
                Text("Use somente aparelhos, contas e redes próprias ou com autorização explícita. Todas as páginas de demonstração são identificadas como simulação.")
            }
        }
        val operation = state.selectedOperationId?.let(RedCatalog::find)
        if (operation == null) {
            Text("Catálogo de operações", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.category == null, onClick = { viewModel.filter(null, state.difficulty) }, label = { Text("Todas") })
                FilterChip(selected = state.category == RedCategory.NETWORKS, onClick = { viewModel.filter(RedCategory.NETWORKS, state.difficulty) }, label = { Text("Redes") })
                FilterChip(selected = state.difficulty == RedDifficulty.BEGINNER, onClick = { viewModel.filter(state.category, RedDifficulty.BEGINNER) }, label = { Text("Iniciante") })
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(RedCatalog.operations.filter { (state.category == null || it.category == state.category) && (state.difficulty == null || it.difficulty == state.difficulty) }, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("OPERAÇÃO ${item.number.toString().padStart(2, '0')} · ${item.title}", style = MaterialTheme.typography.titleMedium)
                            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.category.name} · ${item.difficulty.name}", style = MaterialTheme.typography.labelSmall)
                            Button(onClick = { viewModel.select(item.id) }) { Text("Abrir operação") }
                        }
                    }
                }
                item {
                    Text("Histórico recente", style = MaterialTheme.typography.titleMedium)
                    state.history.take(5).forEach { session ->
                        Text("${session.operationId} · ${session.state} · ${session.events.size} eventos", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(viewModel.report(session))) }) { Text("Copiar relatório") }
                    }
                    if (state.history.isNotEmpty()) TextButton(onClick = viewModel::deleteHistory) { Text("Apagar histórico") }
                }
            }
        } else {
            Text("OPERAÇÃO ${operation.number.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Text(operation.title, style = MaterialTheme.typography.headlineSmall)
            Text(operation.summary)
            Text("Objetivos", style = MaterialTheme.typography.titleMedium)
            operation.objectives.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text("Pré-requisitos", style = MaterialTheme.typography.titleMedium)
            operation.prerequisites.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text("Ambiente: ${operation.environment}", style = MaterialTheme.typography.bodySmall)
            state.session?.let { session ->
                Text("Sessão: ${session.state} · ${session.completedSteps.size}/${operation.steps.size} etapas")
                if (state.networkRunning) {
                    Text("Portal: ${state.portalAddress ?: "endereço local não identificado"}", style = MaterialTheme.typography.bodySmall)
                    state.hotspotInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = viewModel::stopNetworkLab) { Text("Parar hotspot e portal") }
                } else {
                    Button(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startNetworkLab()
                        } else {
                            hotspotPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }) { Text("Iniciar hotspot e portal local") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(operation.steps, key = { it.id }) { step ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(step.title, style = MaterialTheme.typography.titleSmall)
                                Text(step.instructions, style = MaterialTheme.typography.bodySmall)
                                Text(if (step.id in session.completedSteps) "Concluída" else "Pendente", style = MaterialTheme.typography.labelSmall)
                                if (step.id !in session.completedSteps) OutlinedButton(onClick = { viewModel.markStep(step.id) }) { Text("Marcar etapa concluída") }
                            }
                        }
                    }
                    item {
                        Text("Armadilha sintética", style = MaterialTheme.typography.titleMedium)
                        Text("SIMULAÇÃO DE LABORATÓRIO. Digite apenas valores fictícios. O conteúdo digitado não é persistido.")
                        var email = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("xxx@gmail.com") }
                        var password = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("123") }
                        OutlinedTextField(value = email.value, onValueChange = { email.value = it.take(120) }, label = { Text("E-mail fictício") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = password.value, onValueChange = { password.value = it.take(80) }, label = { Text("Senha fictícia") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { viewModel.recordDemoForm(); password.value = "" }) { Text("Enviar demonstração local") }
                        Text("O evento registra somente que o formulário foi concluído. Nenhum valor é enviado ou salvo.")
                        Text("Eventos da sessão", style = MaterialTheme.typography.titleMedium)
                        session.events.asReversed().forEach { Text("${it.timestamp}: ${it.detail}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(viewModel.report(session))) }) { Text("Copiar relatório") }
                    Button(onClick = viewModel::finishSession) { Text("Encerrar sessão") }
                }
            } ?: run {
                Text("Tutorial completo disponível antes da execução. A criação de sessão não ativa rede nem hotspot por conta própria.")
                Button(onClick = viewModel::startSession) { Text("Iniciar sessão de estudo") }
            }
            TextButton(onClick = viewModel::backToCatalog) { Text("Voltar ao catálogo") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
    }
}
