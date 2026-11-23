package com.khalinux.android.red

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

@Composable
fun RedParticipantScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var operatorUrl by remember { mutableStateOf("http://192.168.43.1:8080/") }
    var sessionToken by remember { mutableStateOf("") }
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var cameraPreviewActive by remember { mutableStateOf(false) }
    var microphoneGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var locationGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    ) }
    var microphoneActive by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableStateOf(0f) }
    var locationSummary by remember { mutableStateOf("Ainda não consultada neste aparelho.") }
    var fileSummary by remember { mutableStateOf("Nenhum arquivo selecionado.") }
    var pendingReport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reportMessage by remember { mutableStateOf<String?>(null) }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        cameraPreviewActive = granted
        pendingReport = "camera" to if (granted) "granted" else "denied"
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
        microphoneActive = granted
        pendingReport = "microphone" to if (granted) "granted" else "denied"
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        locationGranted = results.values.any { it }
        pendingReport = "location" to if (locationGranted) "granted" else "denied"
        if (locationGranted) readParticipantLocation(
            context = context,
            onResult = { locationSummary = it }
        )
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileSummary = "Um arquivo foi escolhido pelo seletor do Android. O conteúdo e o nome não foram lidos nem enviados."
            pendingReport = "file" to "selected"
        } else {
            fileSummary = "Seleção cancelada."
            pendingReport = "file" to "cancelled"
        }
    }

    LaunchedEffect(microphoneActive) {
        if (!microphoneActive || !microphoneGranted) {
            audioLevel = 0f
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val sampleRate = 16_000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBuffer.coerceAtLeast(2048)
            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.getOrNull()
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                withContext(Dispatchers.Main) { reportMessage = "O Android não disponibilizou a entrada de áudio." }
                return@withContext
            }
            try {
                recorder.startRecording()
                val samples = ShortArray(1024)
                while (isActive) {
                    val count = recorder.read(samples, 0, samples.size)
                    if (count > 0) {
                        var sum = 0.0
                        for (index in 0 until count) {
                            val value = samples[index].toDouble()
                            sum += value * value
                        }
                        val rms = sqrt(sum / count) / Short.MAX_VALUE
                        withContext(Dispatchers.Main) { audioLevel = rms.toFloat().coerceIn(0f, 1f) }
                    }
                }
            } catch (_: SecurityException) {
                withContext(Dispatchers.Main) { reportMessage = "A permissão de microfone foi revogada pelo Android." }
            } finally {
                runCatching { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                recorder.release()
                withContext(Dispatchers.Main) { audioLevel = 0f }
            }
        }
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MODO PARTICIPANTE", style = MaterialTheme.typography.headlineSmall)
        Text("Este aparelho executa as ações localmente. O operador recebe somente o estado informado abaixo, nunca áudio, vídeo, coordenadas ou conteúdo de arquivos.")
        OutlinedTextField(
            value = operatorUrl,
            onValueChange = { operatorUrl = it.take(180) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Endereço do portal no aparelho operador") }
        )
        OutlinedTextField(
            value = sessionToken,
            onValueChange = { sessionToken = it.take(24).uppercase() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Código da sessão exibido no RED") }
        )

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Câmera", style = MaterialTheme.typography.titleMedium)
                Text(if (cameraGranted) "Permissão concedida neste aparelho." else "Permissão ainda não concedida.")
                Button(onClick = {
                    if (cameraGranted) {
                        cameraPreviewActive = true
                        pendingReport = "camera" to "preview_started"
                    } else cameraPermission.launch(Manifest.permission.CAMERA)
                }) { Text(if (cameraGranted) "Mostrar prévia local" else "Solicitar acesso à câmera") }
                if (cameraGranted && cameraPreviewActive) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        factory = { viewContext ->
                            PreviewView(viewContext).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                controller = cameraController
                            }
                        },
                        update = { it.controller = cameraController }
                    )
                    DisposableEffect(cameraController, lifecycleOwner) {
                        runCatching { cameraController.bindToLifecycle(lifecycleOwner) }
                        onDispose { runCatching { cameraController.unbind() } }
                    }
                }
                if (cameraGranted) {
                    OutlinedButton(onClick = {
                        cameraPreviewActive = !cameraPreviewActive
                        pendingReport = "camera" to if (cameraPreviewActive) "preview_started" else "preview_stopped"
                    }) {
                        Text(if (cameraPreviewActive) "Parar prévia local" else "Iniciar prévia local")
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Microfone", style = MaterialTheme.typography.titleMedium)
                Text(if (microphoneGranted) "Permissão concedida neste aparelho." else "Permissão ainda não concedida.")
                Button(onClick = {
                    if (microphoneGranted) microphoneActive = !microphoneActive
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text(if (!microphoneGranted) "Solicitar acesso ao microfone" else if (microphoneActive) "Parar medidor local" else "Iniciar medidor local")
                }
                Text("Nível de áudio local: ${(audioLevel * 100).toInt()}%")
                Text("O áudio bruto é descartado após o cálculo do nível; não há gravação nem transmissão.")
                if (microphoneGranted) OutlinedButton(onClick = {
                    pendingReport = "microphone" to if (microphoneActive) "preview_started" else "preview_stopped"
                }) { Text("Registrar estado do medidor") }
            }
        }

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Localização", style = MaterialTheme.typography.titleMedium)
                Text(if (locationGranted) locationSummary else "Permissão ainda não concedida.")
                Button(onClick = {
                    if (locationGranted) readParticipantLocation(context) { locationSummary = it }
                    else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(if (locationGranted) "Consultar localização neste aparelho" else "Solicitar acesso à localização") }
                Text("As coordenadas, quando disponíveis, aparecem somente neste aparelho.")
            }
        }

        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Arquivos", style = MaterialTheme.typography.titleMedium)
                Text(fileSummary)
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Abrir seletor de arquivos do Android") }
                Text("A seleção é feita pelo usuário. O conteúdo e o nome do arquivo não são enviados ao operador.")
            }
        }

        pendingReport?.let { (kind, result) ->
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resultado pronto para registrar: $kind · $result")
                    Button(onClick = {
                        val base = Uri.parse(operatorUrl.trim())
                        if (base.scheme != "http" || base.host.isNullOrBlank() || base.port != 8080 || sessionToken.length < 8) {
                            reportMessage = "Informe o endereço HTTP local na porta 8080 e o código da sessão."
                        } else {
                            val eventUri = base.buildUpon()
                                .encodedPath("/participant/event")
                                .clearQuery()
                                .appendQueryParameter("token", sessionToken)
                                .appendQueryParameter("kind", kind)
                                .appendQueryParameter("result", result)
                                .build()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, eventUri))
                                reportMessage = "Abrindo o registro local no navegador. Volte a este aparelho após a confirmação."
                            }.onFailure { reportMessage = "Não foi possível abrir o navegador: ${it.message}" }
                        }
                    }) { Text("Registrar resultado no painel do instrutor") }
                }
            }
        }
        reportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
    }
}

private fun readParticipantLocation(context: android.content.Context, onResult: (String) -> Unit) {
    val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    val provider = runCatching {
        manager.getProviders(true).firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: manager.getProviders(true).firstOrNull()
    }.getOrNull()
    if (provider == null) {
        onResult("Nenhum provedor de localização está habilitado.")
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                onResult(location?.toDisplayString() ?: "O Android não retornou uma posição atual.")
            }
        }.onFailure { onResult("Não foi possível consultar a localização: ${it.message}") }
    } else {
        val location: Location? = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        onResult(location?.toDisplayString() ?: "O Android não disponibilizou uma posição recente.")
    }
}

private fun Location.toDisplayString(): String =
    "Latitude: ${latitude} · Longitude: ${longitude} · Precisão: ${accuracy.toInt()} m (somente neste aparelho)."
