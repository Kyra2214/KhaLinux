package com.khalinux.android.red

import android.graphics.Bitmap
import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun RedScreen(state: RedUiState, viewModel: RedViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val reportContent = remember { androidx.compose.runtime.mutableStateOf("") }
    var sessionPendingDelete by remember { mutableStateOf<String?>(null) }
    val exportReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(reportContent.value.toByteArray(Charsets.UTF_8)) } }
    }
    var hotspotPermissionState by remember(context) { mutableStateOf(readHotspotPermissionState(context)) }
    var hotspotPermissionMessage by remember { mutableStateOf<String?>(null) }
    var hotspotPermissionPending by rememberSaveable { mutableStateOf(false) }
    val previousPermissionState = remember { mutableStateOf(hotspotPermissionState) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hotspotPermissionPending = false
                val current = readHotspotPermissionState(context)
                val previous = previousPermissionState.value
                if (previous == RedAndroidPermissionState.GRANTED &&
                    current in setOf(RedAndroidPermissionState.DENIED, RedAndroidPermissionState.PERMANENTLY_DENIED)
                ) {
                    viewModel.recordStructuredEvent(
                        RedEventType.ANDROID_PERMISSION_REVOKED,
                        "A permissão de localização usada pelo fluxo de hotspot foi revogada nas configurações do Android.",
                        source = "android-permission"
                    )
                }
                val resolved = if (previous == RedAndroidPermissionState.GRANTED && current in setOf(RedAndroidPermissionState.DENIED, RedAndroidPermissionState.PERMANENTLY_DENIED)) RedAndroidPermissionState.REVOKED else current
                previousPermissionState.value = resolved
                hotspotPermissionState = resolved
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hotspotPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hotspotPermissionPending = false
        val current = readHotspotPermissionState(context)
        hotspotPermissionState = current
        previousPermissionState.value = current
        val granted = current == RedAndroidPermissionState.GRANTED
        viewModel.recordStructuredEvent(
            if (granted) RedEventType.ANDROID_PERMISSION_GRANTED else RedEventType.ANDROID_PERMISSION_DENIED,
            if (granted) "O Android concedeu permissão de localização para o fluxo de hotspot."
            else "O Android não concedeu permissão de localização para o fluxo de hotspot.",
            source = "android-permission"
        )
        if (granted) {
            hotspotPermissionMessage = null
            viewModel.startNetworkLab()
        } else {
            hotspotPermissionMessage = "Permissão não concedida. O hotspot não foi solicitado."
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("ÁREA RED", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
                Text("Operações práticas em laboratório autorizado.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = viewModel::refreshHistory) { Text("Histórico") }
            }
        }
        val operation = state.selectedOperationId?.let(RedCatalog::find)
        if (operation == null) {
            Text("Catálogo de operações", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.category == null, onClick = { viewModel.filter(null, state.difficulty) }, label = { Text("Todas") })
                FilterChip(selected = state.category == RedCategory.NETWORKS, onClick = { viewModel.filter(RedCategory.NETWORKS, state.difficulty) }, label = { Text("Redes") })
                FilterChip(selected = state.category == RedCategory.WEB, onClick = { viewModel.filter(RedCategory.WEB, state.difficulty) }, label = { Text("Web") })
                FilterChip(selected = state.category == RedCategory.SOCIAL_ENGINEERING, onClick = { viewModel.filter(RedCategory.SOCIAL_ENGINEERING, state.difficulty) }, label = { Text("Eng. social") })
                FilterChip(selected = state.category == RedCategory.LOCAL_ANALYSIS, onClick = { viewModel.filter(RedCategory.LOCAL_ANALYSIS, state.difficulty) }, label = { Text("Análise local") })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.difficulty == null, onClick = { viewModel.filter(state.category, null) }, label = { Text("Todos níveis") })
                FilterChip(selected = state.difficulty == RedDifficulty.BEGINNER, onClick = { viewModel.filter(state.category, RedDifficulty.BEGINNER) }, label = { Text("Iniciante") })
                FilterChip(selected = state.difficulty == RedDifficulty.INTERMEDIATE, onClick = { viewModel.filter(state.category, RedDifficulty.INTERMEDIATE) }, label = { Text("Intermediário") })
                FilterChip(selected = state.difficulty == RedDifficulty.ADVANCED, onClick = { viewModel.filter(state.category, RedDifficulty.ADVANCED) }, label = { Text("Avançado") })
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(RedCatalog.operations.filter { (state.category == null || it.category == state.category) && (state.difficulty == null || it.difficulty == state.difficulty) }, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("OPERAÇÃO ${item.number.toString().padStart(2, '0')} · ${item.title}", style = MaterialTheme.typography.titleMedium)
                            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.category.name} · ${item.difficulty.name}", style = MaterialTheme.typography.labelSmall)
                            state.history.firstOrNull { it.operationId == item.id }?.let { latest ->
                                Text("Última sessão: ${latest.state} · ${latest.completedSteps.size}/${item.steps.size} etapas", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { viewModel.select(item.id) }) { Text("Abrir operação") }
                        }
                    }
                }
                item {
                    Text("Histórico recente", style = MaterialTheme.typography.titleMedium)
                    state.history.take(100).forEach { session ->
                        Text("${session.operationId} · ${session.state} · ${session.portalMode} · ${session.events.size} eventos", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(viewModel.report(session))) }) { Text("Copiar relatório") }
                            TextButton(onClick = { reportContent.value = viewModel.report(session); exportReport.launch("relatorio-red.md") }) { Text("Exportar") }
                            TextButton(onClick = { sessionPendingDelete = session.id }) { Text("Excluir") }
                        }
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Roteiro rápido de dois aparelhos", style = MaterialTheme.typography.titleSmall)
                    Text("Aparelho A: inicie a sessão, escolha o modo do portal, ative o hotspot e acompanhe o painel.", style = MaterialTheme.typography.bodySmall)
                    Text("Aparelho B: conecte-se ao Wi-Fi, abra o endereço no navegador, faça o login de teste e responda ao aviso de cookies. Se a aula incluir permissões web, use os botões separados da página Permissões do navegador. Toda a observabilidade fica neste painel A.", style = MaterialTheme.typography.bodySmall)
                    Text("BLUE: alterne para a análise defensiva sem encerrar a sessão RED. Volte ao RED para continuar a operação.", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.session?.let { session ->
                Text("Sessão: ${session.state} · ${session.completedSteps.size}/${operation.steps.size} etapas")
                Text(
                    when (state.networkState) {
                        RedNetworkState.IDLE -> "Rede: inativa."
                        RedNetworkState.REQUESTING_HOTSPOT -> "Rede: solicitação enviada ao Android; aguardando resposta."
                        RedNetworkState.HOTSPOT_READY -> "Rede: hotspot confirmado pelo Android."
                        RedNetworkState.DISCOVERING_INTERFACE -> "Rede: identificando interface e IPv4 do hotspot."
                        RedNetworkState.STARTING_SERVER -> "Portal: iniciando servidor na interface identificada."
                        RedNetworkState.WAITING_PARTICIPANT -> "Portal: ativo; aguardando a primeira requisição do navegador."
                        RedNetworkState.PARTICIPANT_ACTIVE -> "Participante: atividade do navegador observada pelo portal HTTPS."
                        RedNetworkState.STOPPING -> "Rede e portal: encerrando recursos."
                        RedNetworkState.ERROR -> "Rede/portal: ocorreu uma falha; consulte os detalhes e a ação de recuperação."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                state.hotspotSnapshot?.let { snapshot ->
                    Text(snapshot.message, style = MaterialTheme.typography.bodySmall)
                    snapshot.ssid?.let { Text("SSID: $it", style = MaterialTheme.typography.bodySmall) }
                    snapshot.interfaceName?.let { Text("Interface: $it", style = MaterialTheme.typography.bodySmall) }
                    snapshot.ipv4Address?.let { Text("IPv4 detectado: $it", style = MaterialTheme.typography.bodySmall) }
                    if (snapshot.ssid == null) Text("SSID não disponibilizado pela API do Android.", style = MaterialTheme.typography.bodySmall)
                }
                if (state.networkRunning) {
                    if (state.systemHotspotMode) {
                        Text("Hotspot do Android: modo de compartilhamento do sistema.", style = MaterialTheme.typography.labelLarge)
                        state.upstreamMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (state.upstreamInternetValidated) {
                            Text("Internet validada no aparelho A. O encaminhamento até B é gerenciado pelo Android/operadora e não é confirmado individualmente pelo KhaLinux.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.portalEndpoint?.let { endpoint ->
                        Text("Portal: ${endpoint.url}", style = MaterialTheme.typography.bodySmall)
                        RedQrCode(endpoint.url)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(endpoint.url)) }) { Text("Copiar endereço do portal") }
                        Text("Servidor HTTPS local · porta ${endpoint.port}", style = MaterialTheme.typography.labelLarge)
                        Text("Interface: ${endpoint.interfaceName ?: "não identificada"}", style = MaterialTheme.typography.bodySmall)
                        state.tlsPreparationUrl?.let { caUrl ->
                            Text("Preparação da CA pública: $caUrl", style = MaterialTheme.typography.bodySmall)
                            RedQrCode(caUrl)
                            TextButton(onClick = { clipboard.setText(AnnotatedString(caUrl)) }) { Text("Copiar endereço de preparação") }
                        }
                        state.tlsCaFingerprint?.let { fingerprint ->
                            Text("Impressão digital SHA-256 da CA: $fingerprint", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (state.tlsBrowserTrustObserved) "Conexão HTTPS observada: handshake TLS concluído pelo navegador B."
                            else "Confiança do navegador: aguardando a primeira conexão HTTPS válida.",
                            color = if (state.tlsBrowserTrustObserved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("HTTPS local · sem internet", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                Text("O portal participante usa TLS na rede local. Antes de abrir o endereço HTTPS no B, instale a CA pública do laboratório e confira a impressão digital SHA-256 exibida aqui.", style = MaterialTheme.typography.bodySmall)
                                Text("Visibilidade: o painel observa requisições destinadas a este portal. Não captura tráfego externo nem fornece uma lista universal de clientes Wi-Fi.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } ?: Text(
                        if (state.networkState == RedNetworkState.ERROR) {
                            "O portal foi encerrado ou não pôde iniciar. A reserva do hotspot ainda pode estar ativa; use Parar para liberá-la."
                        } else {
                            "Hotspot solicitado. Aguardando identificação inequívoca da interface Wi-Fi e do endereço IPv4 antes de iniciar o portal."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.systemHotspotMode && state.portalEndpoint == null) {
                        OutlinedButton(onClick = viewModel::detectSystemHotspot) { Text("Detectar hotspot do Android novamente") }
                    }
                    state.hotspotInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        when (state.networkState) {
                            RedNetworkState.REQUESTING_HOTSPOT -> if (state.systemHotspotMode) "Aguardando retorno das Configurações do hotspot do Android." else "Aguardando o callback real do Android; o hotspot ainda não foi confirmado."
                            RedNetworkState.DISCOVERING_INTERFACE -> "Aguardando identificação inequívoca da interface e do endereço IPv4."
                            RedNetworkState.STARTING_SERVER -> "Interface identificada; servidor em inicialização."
                            RedNetworkState.WAITING_PARTICIPANT -> "Portal HTTPS ativo; nenhuma requisição do navegador B foi recebida ainda."
                            RedNetworkState.PARTICIPANT_ACTIVE -> "Requisição do navegador recebida; atividade confirmada."
                            RedNetworkState.STOPPING -> "Aguardando encerramento do hotspot e dos sockets."
                            RedNetworkState.ERROR -> "Ação de recuperação: use Parar para liberar eventual reserva restante ou tente novamente."
                            RedNetworkState.IDLE -> "Rede inativa."
                            RedNetworkState.HOTSPOT_READY -> if (state.systemHotspotMode) "Interface do hotspot do sistema identificada." else "Hotspot confirmado pelo Android."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Requisições recentes do portal HTTPS: ${state.requests.size}/100", style = MaterialTheme.typography.bodySmall)
                    state.participantObservation?.let { peer ->
                        Text(
                            "Última origem observada: " + (peer.remoteAddress ?: "não identificada") + " · " + peer.requestCount + " requisições desta origem",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    state.requests.asReversed().take(12).forEach { request ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${request.method} ${request.path} · HTTP ${request.statusCode}", style = MaterialTheme.typography.labelLarge)
                                Text("Hora: ${request.timestamp} · ${request.bodyBytes} bytes · ${request.contentType ?: "tipo não informado"}", style = MaterialTheme.typography.bodySmall)
                                Text("Origem: ${request.remoteAddress ?: "não identificada"}", style = MaterialTheme.typography.bodySmall)
                                Text("Resposta HTTP: ${if (request.responseSent) "enviada" else "não confirmada"}", style = MaterialTheme.typography.bodySmall)
                                request.rejectionReason?.let { Text("Rejeição: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                if (request.serverFailure) Text("Falha interna do servidor", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                request.safeHeaders.forEach { (name, value) ->
                                    Text("$name: $value", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = viewModel::stopNetworkLab) {
                        Text(if (state.systemHotspotMode) "Parar portal (hotspot do Android permanece ativo)" else "Parar hotspot e portal")
                    }
                } else {
                    if (state.systemHotspotMode) {
                        Text("Hotspot do sistema selecionado. Volte das Configurações do Android e detecte a interface para iniciar o portal.", style = MaterialTheme.typography.bodySmall)
                        state.upstreamMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(onClick = viewModel::startSystemHotspotLab, enabled = state.networkState == RedNetworkState.IDLE || state.networkState == RedNetworkState.REQUESTING_HOTSPOT || state.networkState == RedNetworkState.ERROR) { Text("Abrir configurações do hotspot") }
                        OutlinedButton(onClick = viewModel::detectSystemHotspot) { Text("Detectar hotspot do Android") }
                        Text("Para voltar ao modo local isolado, desligue primeiro o hotspot do sistema nas Configurações.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = viewModel::resetSystemHotspotMode) { Text("Voltar para hotspot local isolado") }
                    } else {
                    Text("Modo do portal", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.portalConfiguration.mode == RedPortalMode.LOCAL_LOGIN,
                            onClick = { viewModel.setPortalMode(RedPortalMode.LOCAL_LOGIN) },
                            label = { Text("Login local") }
                        )
                        FilterChip(
                            selected = state.portalConfiguration.mode == RedPortalMode.OPEN_ACCESS,
                            onClick = { viewModel.setPortalMode(RedPortalMode.OPEN_ACCESS) },
                            label = { Text("Acesso direto") }
                        )
                    }
                    Text("Permissão do Android para hotspot: ${hotspotPermissionState.permissionLabel()}", style = MaterialTheme.typography.bodySmall)
                    Text("O Android pode exigir permissão de localização para disponibilizar o hotspot. O KhaLinux não consulta nem registra coordenadas.", style = MaterialTheme.typography.bodySmall)
                    hotspotPermissionMessage?.let {
                        Text(
                            it,
                            color = if (hotspotPermissionPending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(onClick = {
                        if (hotspotPermissionState == RedAndroidPermissionState.GRANTED) {
                            viewModel.startNetworkLab()
                        } else if (hotspotPermissionState in setOf(RedAndroidPermissionState.PERMANENTLY_DENIED, RedAndroidPermissionState.REVOKED)) {
                            hotspotPermissionMessage = "Permissão não disponível. Ative a permissão necessária nas Configurações e retorne ao KhaLinux."
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            hotspotPermissionPending = true
                            hotspotPermissionMessage = "Aguardando a resposta do diálogo de permissão do Android."
                            context.getSharedPreferences("red_permission_state", Context.MODE_PRIVATE)
                                .edit().putBoolean("hotspot_location_asked", true).apply()
                            viewModel.recordStructuredEvent(
                                RedEventType.ANDROID_PERMISSION_REQUESTED,
                                "Solicitando ao Android permissão de localização necessária ao fluxo de hotspot.",
                                source = "android-permission"
                            )
                            hotspotPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                    }, enabled = !hotspotPermissionPending && state.networkState != RedNetworkState.REQUESTING_HOTSPOT) {
                        Text(
                            when {
                                hotspotPermissionPending -> "Aguardando permissão do Android…"
                                state.networkState == RedNetworkState.REQUESTING_HOTSPOT -> "Solicitando hotspot…"
                                else -> "Iniciar hotspot e portal local"
                            }
                        )
                    }
                    if (state.networkState == RedNetworkState.ERROR) {
                        OutlinedButton(onClick = viewModel::stopNetworkLab) { Text("Limpar erro e encerrar rede") }
                    }
                    OutlinedButton(
                        onClick = viewModel::startSystemHotspotLab,
                        enabled = state.networkState == RedNetworkState.IDLE && !hotspotPermissionPending
                    ) {
                        Text("Usar hotspot do Android (internet depois)")
                    }
                    Text(
                        "Para manter B na mesma rede ao ativar internet depois: desative os dados móveis, comece com o hotspot do sistema desligado, ligue-o, conecte B e só então ative os dados móveis no A. Não desligue o hotspot. LocalOnlyHotspot é isolado e não pode receber internet posteriormente.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    }
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
                        Text("Último formulário recebido", style = MaterialTheme.typography.titleMedium)
                        state.latestSubmission?.let { submission ->
                            Text(
                                if (submission.accepted) "Recebido e validado localmente" else "Envio rejeitado",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (submission.accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(submission.message, style = MaterialTheme.typography.bodySmall)
                            submission.fields.forEach { (key, value) ->
                                Text("$key: $value", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "Valores visíveis somente nesta sessão e mantidos em memória; não entram no histórico nem no relatório.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(onClick = viewModel::clearSensitivePanelData) { Text("Limpar valores temporários") }
                        } ?: Text("Nenhum formulário foi recebido nesta sessão.", style = MaterialTheme.typography.bodySmall)
                        Text("Consentimento de cookies do navegador", style = MaterialTheme.typography.titleMedium)
                        val cookieEvent = session.events.lastOrNull { it.type == RedEventType.COOKIE_CONSENT_RECORDED }
                        Text(cookieEvent?.detail ?: "Nenhuma escolha de cookies recebida nesta sessão.", style = MaterialTheme.typography.bodySmall)
                        Text("Permissões web do navegador", style = MaterialTheme.typography.titleMedium)
                        val webPermissionEvents = session.events.filter {
                            it.type in setOf(
                                RedEventType.WEB_PERMISSION_REQUESTED,
                                RedEventType.WEB_PERMISSION_GRANTED,
                                RedEventType.WEB_PERMISSION_DENIED,
                                RedEventType.WEB_PERMISSION_CANCELLED,
                                RedEventType.WEB_PERMISSION_UNAVAILABLE,
                                RedEventType.WEB_PERMISSION_REVOKED
                            )
                        }
                        if (webPermissionEvents.isEmpty()) {
                            Text("Nenhum resultado de permissão web foi recebido nesta sessão. O navegador exige contexto seguro e confiança no certificado HTTPS; confira o estado do handshake TLS e a disponibilidade de cada API.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            webPermissionEvents.takeLast(12).forEach { event ->
                                Text(
                                    "${event.webPermissionResource ?: "recurso"}: ${event.webPermissionState ?: event.type} · ${event.detail}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text("Eventos da sessão", style = MaterialTheme.typography.titleMedium)
                        session.events.asReversed().forEach { Text("${it.sequence}. ${it.timestamp}: ${it.detail}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(viewModel.report(session))) }) { Text("Copiar relatório") }
                    OutlinedButton(onClick = { reportContent.value = viewModel.report(session); exportReport.launch("relatorio-red.md") }) { Text("Exportar arquivo") }
                    Button(onClick = viewModel::finishSession) { Text("Encerrar sessão") }
                }
            } ?: run {
                Text("Tutorial de execução", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(operation.steps, key = { it.id }) { step ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(step.title, style = MaterialTheme.typography.titleSmall)
                                Text(step.instructions, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Text("Critérios de conclusão", style = MaterialTheme.typography.titleMedium)
                        operation.completionCriteria.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Text("A sessão de estudo não ativa recursos de rede. O hotspot só será solicitado após a ação explícita do operador.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::startSession) { Text("Iniciar sessão") }
            }
            TextButton(onClick = viewModel::backToCatalog) { Text("Voltar ao catálogo") }
        }
        state.message?.let {
            Text(
                it,
                color = if (state.networkState == RedNetworkState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    sessionPendingDelete?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("Excluir sessão do histórico?") },
            text = { Text("Os metadados e o relatório gerado a partir desta sessão serão removidos do KhaLinux. Arquivos que já foram exportados permanecem no local escolhido.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(sessionId)
                    sessionPendingDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}


private fun readHotspotPermissionState(context: Context): RedAndroidPermissionState {
    val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (granted) return RedAndroidPermissionState.GRANTED
    val askedBefore = context.getSharedPreferences("red_permission_state", Context.MODE_PRIVATE)
        .getBoolean("hotspot_location_asked", false)
    val activity = context.findActivity()
    val canShowRationale = activity?.let {
        it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    } ?: true
    return if (askedBefore && !canShowRationale) RedAndroidPermissionState.PERMANENTLY_DENIED
    else RedAndroidPermissionState.DENIED
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun RedAndroidPermissionState.permissionLabel(): String = when (this) {
    RedAndroidPermissionState.UNKNOWN -> "não consultada"
    RedAndroidPermissionState.GRANTED -> "concedida"
    RedAndroidPermissionState.DENIED -> "negada ou não concedida"
    RedAndroidPermissionState.PERMANENTLY_DENIED -> "negada permanentemente; exige Configurações"
    RedAndroidPermissionState.REVOKED -> "revogada nas Configurações"
}

@Composable
private fun RedQrCode(value: String) {
    val bitmap = androidx.compose.runtime.remember(value) {
        runCatching {
            val matrix = com.google.zxing.MultiFormatWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 420, 420)
            Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888).also { image ->
                for (x in 0 until 420) for (y in 0 until 420) {
                    image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }.getOrNull()
    }
    bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code do portal local", modifier = Modifier.fillMaxWidth().padding(12.dp)) }
}
