#!/usr/bin/env python3
"""Valida camadas do app e transporte PTY sem alegar build ou teste Android."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
manifest = ROOT / "android/app/src/main/AndroidManifest.xml"
activity = ROOT / "android/app/src/main/kotlin/com/khalinux/android/MainActivity.kt"
view_model = ROOT / "android/app/src/main/kotlin/com/khalinux/android/TerminalViewModel.kt"
screen = ROOT / "android/app/src/main/kotlin/com/khalinux/android/KhaLinuxTerminalScreen.kt"
domain_gateway = ROOT / "android/app/src/main/kotlin/com/khalinux/android/domain/TerminalRuntimeGateway.kt"
use_cases = ROOT / "android/app/src/main/kotlin/com/khalinux/android/domain/TerminalUseCases.kt"
data_adapter = ROOT / "android/app/src/main/kotlin/com/khalinux/android/data/ServiceTerminalRuntimeGateway.kt"
service = ROOT / "android/app/src/main/kotlin/com/khalinux/android/KhaLinuxRuntimeService.kt"
app_build = ROOT / "android/app/build.gradle.kts"
settings = ROOT / "settings.gradle.kts"
buffer = ROOT / "android/app/src/main/java/com/khalinux/android/TerminalOutputBuffer.java"
test_script = ROOT / "android/app/test-output-buffer.sh"
runtime_launcher = ROOT / "android/runtime/src/main/kotlin/com/khalinux/runtime/ProotLauncher.kt"
runtime_config = ROOT / "android/runtime/src/main/kotlin/com/khalinux/runtime/ProotConfig.kt"
runtime_manager = ROOT / "android/runtime/src/main/kotlin/com/khalinux/runtime/SessionManager.kt"
pty_process = ROOT / "android/runtime/src/main/kotlin/com/khalinux/runtime/AndroidPtyProcess.kt"
process_group = ROOT / "android/runtime/src/main/kotlin/com/khalinux/runtime/ProcessGroupManagedProcess.kt"
adr_process_group = ROOT / "docs/decisions/ADR-0004-process-group-lifecycle.md"
pty_native = ROOT / "android/runtime/src/main/cpp/native_pty.c"
pty_build = ROOT / "android/runtime/build.gradle.kts"
adr = ROOT / "docs/decisions/ADR-0003-native-pty.md"

root = ET.parse(manifest).getroot()
ns = {"a": "http://schemas.android.com/apk/res/android"}
services = root.findall(".//service")
activities = root.findall(".//activity")
assert any(s.attrib.get(f"{{{ns['a']}}}exported") == "false" for s in services), "Service deve ser privado"
assert any(a.attrib.get(f"{{{ns['a']}}}exported") == "true" for a in activities), "Activity launcher ausente"
activity_text = activity.read_text()
view_model_text = view_model.read_text()
screen_text = screen.read_text()
domain_text = domain_gateway.read_text()
use_cases_text = use_cases.read_text()
data_text = data_adapter.read_text()
service_text = service.read_text()
build_text = app_build.read_text()
launcher_text = runtime_launcher.read_text()
config_text = runtime_config.read_text()
manager_text = runtime_manager.read_text()
pty_text = pty_native.read_text()
pty_process_text = pty_process.read_text()
process_group_text = process_group.read_text()
pty_build_text = pty_build.read_text()
assert "bindService" in activity_text and "unbindService" in activity_text, "Activity não gerencia binding do Service"
assert "setContent" in activity_text and "ComponentActivity" in activity_text, "Activity Compose ausente"
assert "ServiceTerminalRuntimeGateway" in activity_text, "Activity não usa o adaptador de dados"
assert "ProcessBuilder" not in activity_text and "ProotLauncher" not in activity_text, "Activity acoplando execução ao runtime"
assert "class TerminalViewModel" in view_model_text and "MutableStateFlow" in view_model_text, "ViewModel/StateFlow ausente"
assert "withContext(Dispatchers.IO)" in view_model_text, "inicialização do processo deve sair da thread principal"
assert "isStarting: Boolean" in view_model_text and "isStarting || sessionId != null" in view_model_text, "estado de inicialização não bloqueia comandos duplicados"
assert "createdSessionId" in view_model_text and "completedSessionsBeforeStartReturn" in view_model_text, "corrida entre início e saída rápida da sessão não está protegida"
assert "CancellationException" in view_model_text and "NonCancellable + Dispatchers.IO" in view_model_text, "cancelamento durante spawn pode deixar sessão órfã"
send_input_text = view_model_text.split("fun sendInput()", 1)[1].split("fun resizeTerminal", 1)[0]
assert "withContext(Dispatchers.IO)" in send_input_text, "escrita no PTY pode bloquear a thread principal"
assert "isSendingInput" in view_model_text and "isSendingInput" in screen_text, "envio duplicado de linhas não é bloqueado"
assert "KhaLinuxRuntimeService" not in view_model_text, "ViewModel acoplado ao Service Android"
assert "TerminalRuntimeGateway" in view_model_text and "TerminalUseCases" in view_model_text, "ViewModel sem abstração/casos de uso"
assert "interface TerminalRuntimeGateway" in domain_text, "porta de domínio ausente"
assert "StartTerminalCommandUseCase" in use_cases_text and "CancelTerminalSessionUseCase" in use_cases_text, "casos de uso ausentes"
assert "startInteractive" in domain_text and "writeInput" in domain_text and "resize" in domain_text, "porta interativa incompleta"
assert "TerminalRuntimeGateway" in data_text and "KhaLinuxRuntimeService" in data_text, "adaptador Service/dados ausente"
assert "collectAsStateWithLifecycle" in screen_text and "OutlinedTextField" in screen_text, "tela Compose lifecycle-aware ausente"
assert "animateScrollTo" in screen_text, "saída do terminal não acompanha novos eventos"
assert "Abrir shell" in screen_text and "Enviar" in screen_text, "ações de shell interativo ausentes"
assert "compose = true" in build_text and "kotlinCompilerExtensionVersion" in build_text, "Compose não habilitado no Gradle"
assert "class LocalBinder" in service_text and "onBind" in service_text, "Binder do Service ausente"
assert "SessionManager" in service_text and "startShell" in service_text, "Service não hospeda runtime"
assert "startInteractiveShell" in service_text and "AndroidPtyProcessLauncher" in service_text, "Service não seleciona PTY para shell"
assert "TerminalProcessLauncher" in launcher_text and "interactiveProcessLauncher" in launcher_text, "launcher PTY não está separado dos pipes"
assert "ProcessBuilder" in launcher_text and "execve(" in pty_text and "forkpty(" in pty_text, "execução estruturada/PTY ausentes"
assert "/system/bin/setsid" in launcher_text and "ProcessGroupManagedProcess" in launcher_text, "comandos Android não isolam grupo de processos"
assert "NativePty.signal" in launcher_text and "delegate.pid()" in process_group_text, "cancelamento de grupo não usa PID/ponte JNI"
assert "process-group" in adr_process_group.read_text(), "ADR-0004 não registra o ciclo de vida do grupo de processos"
assert "writeInput" in manager_text and "streamPumpsFinished.await()" in manager_text, "entrada/drenagem de streams ausentes"
assert "ResizableProcess" in pty_process_text and "NativePty.resize" in pty_process_text, "controle de tamanho do PTY ausente"
assert "externalNativeBuild" in pty_build_text and "minSdk = 35" in pty_build_text, "integração CMake/minSdk ausente"
assert "forkpty" in adr.read_text() and "VT/ANSI" in adr.read_text(), "ADR-0003 ou limite VT/ANSI ausente"
assert 'include(":android:app")' in settings.read_text(), "módulo app ausente"
assert 'include(":android:runtime")' in settings.read_text(), "módulo runtime ausente"
assert buffer.is_file(), "buffer limitado de saída ausente"
assert "TerminalOutputBuffer()" in view_model_text, "ViewModel não usa buffer limitado"

result = subprocess.run(["bash", str(test_script)], check=True, capture_output=True, text=True)
assert "terminal_output_buffer=ok cases=6" in result.stdout, "testes do buffer não concluíram"
pty_result = subprocess.run(
    ["bash", str(ROOT / "android/runtime/test-pty-native.sh")],
    check=True,
    capture_output=True,
    text=True,
)
assert "pty_native=ok" in pty_result.stdout, "teste JNI/PTy host-side não concluiu"
print(result.stdout.strip())
print(pty_result.stdout.strip())
print("phase3_structure=ok compose_mvvm_domain_data_service_separated=true transcript_bounded=true interactive_pty=true")
