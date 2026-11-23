package com.khalinux.android.blue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khalinux.android.red.RedEvent
import com.khalinux.android.red.RedEventType
import com.khalinux.android.red.RedSession

@Composable
fun BlueScreen(state: BlueUiState, viewModel: BlueViewModel) {
    val session = state.selectedSession
    val displayEvents = if (session != null && state.liveEvidence.sessionId == session.id) {
        state.liveEvidence.events
    } else session?.events.orEmpty()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("ÁREA BLUE", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Análise defensiva do mesmo cenário observado no RED.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = viewModel::refresh) { Text("Atualizar") }
        }
        Text("Cenários RED disponíveis", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.sessions.take(4).forEach { item ->
                FilterChip(
                    selected = item.id == state.selectedSessionId,
                    onClick = { viewModel.selectSession(item.id) },
                    label = { Text("${item.operationId.take(12)} · ${item.state}") }
                )
            }
        }
        if (session == null) {
            Text(state.message ?: "Execute uma operação RED para gerar evidências.")
            Text("BLUE não inventa alertas: a análise depende dos eventos realmente registrados pelo laboratório.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Cenário observado", style = MaterialTheme.typography.titleMedium)
                            Text(session.operationId)
                            Text("Estado: ${session.state} · modo: ${if (session.portalMode.name == "LOCAL_LOGIN") "login local" else "acesso direto"}")
                            Text("Eventos registrados: ${displayEvents.size}")
                            Text("Início: ${session.startedAt}")
                        }
                    }
                }
                if (state.liveEvidence.sessionId == session.id) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Evidências ao vivo do RED", style = MaterialTheme.typography.titleMedium)
                                Text("Requisições HTTP observadas: ${state.liveEvidence.requests.size}")
                                state.liveEvidence.requests.takeLast(8).asReversed().forEach { request ->
                                    Text(
                                        "${request.method} ${request.path} · HTTP ${request.statusCode} · ${request.bodyBytes} bytes",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                state.liveEvidence.latestSubmission?.let { submission ->
                                    Text(
                                        if (submission.accepted) "Formulário sintético aceito localmente" else "Formulário sintético rejeitado",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    submission.fields.forEach { (key, value) ->
                                        Text("$key: $value", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Estes valores são dados de teste efêmeros, não credenciais reais.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Como se defender neste cenário", style = MaterialTheme.typography.titleMedium)
                    Text("• Confirme o nome da rede e a origem do portal antes de continuar.")
                    Text("• Não use credenciais pessoais em páginas de acesso Wi-Fi.")
                    Text("• Confira o endereço e o protocolo após cada redirecionamento.")
                    Text("• Conceda permissões somente quando a função e o aplicativo justificarem o pedido.")
                    Text("• Prefira HTTPS válido para serviços reais; HTTP local não oferece criptografia de transporte.")
                    Text("• Ao terminar, desconecte-se e remova a rede se não for confiável.")
                }
                item { Text("Linha do tempo e análise", style = MaterialTheme.typography.titleMedium) }
                items(displayEvents.sortedBy { it.sequence }, key = { it.id }) { event ->
                    BlueEventCard(event)
                }
            }
        }
        OutlinedButton(onClick = viewModel::close, modifier = Modifier.fillMaxWidth()) { Text("Fechar BLUE") }
    }
}

@Composable
private fun BlueEventCard(event: RedEvent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("#${event.sequence} · ${event.type} · ${event.timestamp}", style = MaterialTheme.typography.labelLarge)
            Text(event.detail, style = MaterialTheme.typography.bodySmall)
            event.httpMethod?.let { method ->
                Text("HTTP: $method ${event.route ?: "/"} · ${event.httpStatus ?: "?"} · ${event.bytesReceived ?: 0} bytes", style = MaterialTheme.typography.bodySmall)
            }
            Text("Leitura BLUE: ${defensiveAdvice(event.type)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun defensiveAdvice(type: RedEventType): String = when (type) {
    RedEventType.HOTSPOT_READY -> "A conexão Wi-Fi, por si só, não comprova que o ponto de acesso é legítimo. Confirme a rede com o estabelecimento."
    RedEventType.PORTAL_OPENED -> "A abertura de um portal não autentica a rede nem comprova a identidade de quem a administra."
    RedEventType.HTTP_REQUEST_RECEIVED -> "O servidor recebeu uma requisição HTTP local. HTTP não cifra o conteúdo da comunicação."
    RedEventType.NAVIGATION -> "Confira a URL e o destino antes de seguir. Uma página familiar não garante que o domínio seja legítimo."
    RedEventType.REDIRECT -> "Redirecionamentos podem levar a destinos inesperados. Verifique a URL final antes de inserir qualquer dado."
    RedEventType.FORM_SUBMITTED -> "O formulário foi enviado ao portal local. Não reutilize senhas pessoais em portais de Wi-Fi."
    RedEventType.FORM_REJECTED -> "O envio foi rejeitado pelo laboratório. Uma falha ou erro não deve ser confundido com acesso liberado."
    RedEventType.PERMISSION_REQUESTED -> "O aplicativo participante solicitou uma permissão. Leia o diálogo do sistema e avalie a necessidade."
    RedEventType.PERMISSION_GRANTED -> "A permissão foi concedida no aparelho participante. Revise o acesso e revogue quando não for necessário."
    RedEventType.PERMISSION_DENIED -> "A solicitação foi negada. O recurso deve permanecer indisponível para aquela função."
    RedEventType.PERMISSION_CANCELLED -> "A solicitação foi cancelada; não presuma que o recurso foi autorizado."
    RedEventType.FILE_PICKER_OPENED -> "O seletor do sistema permite ao usuário escolher o item, em vez de liberar a biblioteca inteira."
    RedEventType.FILE_SELECTED -> "Antes de compartilhar um arquivo, confirme o destino e o conteúdo escolhido."
    RedEventType.RESOURCE_PREVIEW_STARTED -> "A prévia ocorreu localmente no aparelho participante. O evento recebido não contém o fluxo de mídia."
    RedEventType.RESOURCE_PREVIEW_STOPPED -> "A prévia foi encerrada. Revise permissões e indicadores de uso do recurso."
    RedEventType.HOTSPOT_FAILED, RedEventType.PORTAL_ERROR, RedEventType.ERROR -> "Ocorreu uma falha observada. Não trate uma tentativa como ação concluída."
    else -> "Compare o evento com a ação realizada e mantenha o escopo da demonstração."
}
