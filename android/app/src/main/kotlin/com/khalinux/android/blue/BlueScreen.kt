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
import com.khalinux.android.red.RedWebPermissionResource

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
                item {
                    val received = displayEvents.count { it.type == RedEventType.HTTP_REQUEST_RECEIVED }
                    val rejected = displayEvents.count { it.type == RedEventType.HTTP_REQUEST_REJECTED }
                    val responses = displayEvents.count { it.type == RedEventType.HTTP_RESPONSE_SENT }
                    val cookieChoices = displayEvents.count { it.type == RedEventType.COOKIE_CONSENT_RECORDED }
                    val webPermissionResults = displayEvents.count {
                        it.type in setOf(
                            RedEventType.WEB_PERMISSION_REQUESTED,
                            RedEventType.WEB_PERMISSION_GRANTED,
                            RedEventType.WEB_PERMISSION_DENIED,
                            RedEventType.WEB_PERMISSION_CANCELLED,
                            RedEventType.WEB_PERMISSION_UNAVAILABLE,
                            RedEventType.WEB_PERMISSION_REVOKED
                        )
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Fatos observados", style = MaterialTheme.typography.titleMedium)
                            Text("Requisições recebidas: $received")
                            Text("Requisições rejeitadas: $rejected")
                            Text("Respostas HTTP enviadas: $responses")
                            Text("Escolhas de cookies registradas: $cookieChoices")
                            Text("Estados de permissões web recebidos: $webPermissionResults")
                            Text("Contagens derivadas somente dos eventos desta sessão; não representam detecção de ataque ou lista completa de clientes.")
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
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "${request.method} ${request.path} · HTTP ${request.statusCode}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            "Hora: ${request.timestamp} · ${request.bodyBytes} bytes · ${request.contentType ?: "tipo não informado"}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text("Origem: ${request.remoteAddress ?: "não identificada"}", style = MaterialTheme.typography.bodySmall)
                                        Text("Resposta: ${if (request.responseSent) "enviada" else "não confirmada"}", style = MaterialTheme.typography.bodySmall)
                                        request.rejectionReason?.let { Text("Rejeição: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                        if (request.serverFailure) Text("Falha interna do servidor", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        request.safeHeaders.forEach { (name, value) ->
                                            Text("$name: $value", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
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
                    val latestWebPermissionEvents = enumValues<RedWebPermissionResource>().mapNotNull { resource ->
                        displayEvents.lastOrNull { it.webPermissionResource == resource }
                            ?.let { resource to it }
                    }
                    val latestAndroidPermissionEvent = displayEvents.lastOrNull {
                        it.type in setOf(
                            RedEventType.ANDROID_PERMISSION_REQUESTED,
                            RedEventType.ANDROID_PERMISSION_GRANTED,
                            RedEventType.ANDROID_PERMISSION_DENIED,
                            RedEventType.ANDROID_PERMISSION_REVOKED
                        )
                    }
                    val latestCookieConsent = displayEvents.lastOrNull { it.type == RedEventType.COOKIE_CONSENT_RECORDED }
                    Text("Permissões web observadas no navegador B", style = MaterialTheme.typography.titleMedium)
                    if (latestWebPermissionEvents.isEmpty()) {
                        Text("Nenhum resultado de permissão web foi recebido nesta sessão.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        latestWebPermissionEvents.forEach { (resource, event) ->
                            Text("$resource: ${event.webPermissionState ?: event.type} · ${event.detail}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Permissão do aplicativo operador A", style = MaterialTheme.typography.titleSmall)
                    Text(
                        latestAndroidPermissionEvent?.let { "${it.type}: ${it.detail}" }
                            ?: "Nenhum evento de permissão do operador registrado.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Consentimento de cookies", style = MaterialTheme.typography.titleSmall)
                    Text(
                        latestCookieConsent?.let {
                            "${it.cookieChoice}: análise=${it.cookieAnalytics}, preferências=${it.cookiePreferences}, personalização=${it.cookiePersonalization}"
                        } ?: "Nenhuma escolha de cookies registrada.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
            Text("Fato observado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(event.detail, style = MaterialTheme.typography.bodySmall)
            event.httpMethod?.let { method ->
                Text("HTTP: $method ${event.route ?: "/"} · ${event.httpStatus ?: "?"} · ${event.bytesReceived ?: 0} bytes", style = MaterialTheme.typography.bodySmall)
            }
            event.remoteAddress?.let { Text("Origem remota: $it", style = MaterialTheme.typography.bodySmall) }
            event.responseSent?.let { Text("Resposta enviada: $it", style = MaterialTheme.typography.bodySmall) }
            event.rejectionReason?.let { Text("Motivo da rejeição: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (event.serverFailure == true) Text("Falha interna do servidor", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            event.safeHeaders.forEach { (name, value) -> Text("$name: $value", style = MaterialTheme.typography.bodySmall) }
            event.cookieChoice?.let { Text("Cookies: $it · análise=${event.cookieAnalytics}, preferências=${event.cookiePreferences}, personalização=${event.cookiePersonalization}", style = MaterialTheme.typography.bodySmall) }
            event.webPermissionResource?.let { Text("Permissão web: $it · ${event.webPermissionState}", style = MaterialTheme.typography.bodySmall) }
            Text("Interpretação defensiva", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(defensiveAdvice(event), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun defensiveAdvice(event: RedEvent): String {
    if (event.type == RedEventType.HTTP_REQUEST_REJECTED) {
        when (event.rejectionReason) {
            "invalid_session_token" -> return "O servidor recusou a operação porque o token de sessão estava ausente ou inválido. Reabra o portal pela sessão atual; não reutilize cookies antigos."
            "invalid_request_origin" -> return "A origem do POST não correspondeu ao portal esperado e a operação foi bloqueada. Não envie formulários após redirecionamentos para origens desconhecidas."
            "session_rate_limited" -> return "O servidor aplicou um bloqueio temporário após falhas repetidas. Isso é um controle preventivo, não prova isolada de ataque."
            "session_expired" -> return "A sessão atingiu seu limite de tempo. Solicite ao operador uma nova sessão em vez de reutilizar uma página antiga."
        }
    }
    return when (event.type) {
    RedEventType.SESSION_STARTED -> "Uma sessão de laboratório foi iniciada. Confirme o escopo e o operador antes de qualquer ação."
    RedEventType.SESSION_ENDED -> "A sessão foi encerrada pelo operador; tokens e recursos temporários devem deixar de ser válidos."
    RedEventType.HOTSPOT_READY -> "A conexão Wi-Fi, por si só, não comprova que o ponto de acesso é legítimo. Confirme a rede com o estabelecimento."
    RedEventType.PORTAL_OPENED -> "A abertura de um portal não autentica a rede nem comprova a identidade de quem a administra."
    RedEventType.HTTP_REQUEST_RECEIVED -> "O servidor recebeu uma requisição HTTP local. HTTP não cifra o conteúdo da comunicação."
    RedEventType.HTTP_REQUEST_REJECTED -> "A requisição foi rejeitada por uma regra explícita do servidor. Uma rejeição não significa acesso concedido."
    RedEventType.HTTP_RESPONSE_SENT -> "O servidor enviou uma resposta; confira o status HTTP para distinguir sucesso de erro."
    RedEventType.HTTP_SERVER_FAILURE -> "Ocorreu uma falha interna do servidor. Não confunda resposta de erro com ação concluída."
    RedEventType.SESSION_INTERRUPTED -> "A sessão foi interrompida por segundo plano, encerramento da área ou reinício. Revise os eventos e não a trate como concluída."
    RedEventType.COOKIE_CONSENT_RECORDED -> "A escolha de cookies registra apenas categorias do portal. Não concede câmera, microfone, localização ou acesso a arquivos."
    RedEventType.HOTSPOT_INTERFACE_DISCOVERY -> "O Android criou a rede; o operador ainda está identificando a interface e o IPv4 antes de iniciar o portal."
    RedEventType.ANDROID_PERMISSION_REQUESTED -> "O aplicativo operador solicitou ao Android uma permissão necessária ao hotspot."
    RedEventType.ANDROID_PERMISSION_GRANTED -> "O Android concedeu a permissão ao aplicativo operador. Isso não concede acesso ao aparelho B."
    RedEventType.ANDROID_PERMISSION_DENIED -> "O Android negou a permissão ao aplicativo operador; o recurso dependente deve permanecer indisponível."
    RedEventType.ANDROID_PERMISSION_REVOKED -> "A permissão do aplicativo operador foi revogada nas configurações do Android."
    RedEventType.WEB_PERMISSION_REQUESTED -> "O navegador iniciou uma solicitação web para um recurso específico da origem do portal."
    RedEventType.WEB_PERMISSION_GRANTED -> "O navegador concedeu a permissão web solicitada para a origem do portal. O laboratório não recebeu mídia nem coordenadas."
    RedEventType.WEB_PERMISSION_DENIED -> "O navegador negou a permissão web; o recurso não deve ser considerado disponível."
    RedEventType.WEB_PERMISSION_CANCELLED -> "O pedido de permissão web foi cancelado."
    RedEventType.WEB_PERMISSION_UNAVAILABLE -> "A API de permissão não estava disponível neste contexto do navegador. Nenhuma concessão deve ser presumida."
    RedEventType.WEB_PERMISSION_REVOKED -> "A permissão web deixou de estar concedida; confira as configurações do navegador."
    RedEventType.NAVIGATION -> "Confira a URL e o destino antes de seguir. Uma página familiar não garante que o domínio seja legítimo."
    RedEventType.REDIRECT -> "Redirecionamentos podem levar a destinos inesperados. Verifique a URL final antes de inserir qualquer dado."
    RedEventType.FORM_SUBMITTED -> "O formulário de demonstração foi enviado ao portal local. Não reutilize senhas pessoais em portais Wi-Fi."
    RedEventType.FORM_REJECTED -> "O envio foi rejeitado pelo laboratório. Uma falha ou erro não deve ser confundido com acesso liberado."
    RedEventType.FILE_PICKER_OPENED -> "O seletor do sistema permite ao usuário escolher o item, em vez de liberar a biblioteca inteira."
    RedEventType.FILE_SELECTED -> "Antes de compartilhar um arquivo, confirme o destino e o conteúdo escolhido."
    RedEventType.RESOURCE_PREVIEW_STARTED -> "A prévia ocorreu localmente no aparelho participante. O evento recebido não contém o fluxo de mídia."
    RedEventType.RESOURCE_PREVIEW_STOPPED -> "A prévia foi encerrada. Revise permissões e indicadores de uso do recurso."
    RedEventType.HOTSPOT_FAILED, RedEventType.PORTAL_ERROR, RedEventType.ERROR -> "Ocorreu uma falha observada. Não trate uma tentativa como ação concluída."
    else -> "Compare o evento com a ação realizada e mantenha o escopo da demonstração."
    }
}
