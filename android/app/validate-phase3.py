#!/usr/bin/env python3
"""Valida a separação Activity -> Compose/ViewModel -> domínio/dados -> Service."""
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
assert "bindService" in activity_text and "unbindService" in activity_text, "Activity não gerencia binding do Service"
assert "setContent" in activity_text and "ComponentActivity" in activity_text, "Activity Compose ausente"
assert "ServiceTerminalRuntimeGateway" in activity_text, "Activity não usa o adaptador de dados"
assert "ProcessBuilder" not in activity_text and "ProotLauncher" not in activity_text, "Activity acoplando execução ao runtime"
assert "class TerminalViewModel" in view_model_text and "MutableStateFlow" in view_model_text, "ViewModel/StateFlow ausente"
assert "KhaLinuxRuntimeService" not in view_model_text, "ViewModel acoplado ao Service Android"
assert "TerminalRuntimeGateway" in view_model_text and "TerminalUseCases" in view_model_text, "ViewModel sem abstração/casos de uso"
assert "interface TerminalRuntimeGateway" in domain_text, "porta de domínio ausente"
assert "StartTerminalCommandUseCase" in use_cases_text and "CancelTerminalSessionUseCase" in use_cases_text, "casos de uso ausentes"
assert "TerminalRuntimeGateway" in data_text and "KhaLinuxRuntimeService" in data_text, "adaptador Service/dados ausente"
assert "collectAsStateWithLifecycle" in screen_text and "OutlinedTextField" in screen_text, "tela Compose lifecycle-aware ausente"
assert "animateScrollTo" in screen_text, "saída do terminal não acompanha novos eventos"
assert "compose = true" in build_text and "kotlinCompilerExtensionVersion" in build_text, "Compose não habilitado no Gradle"
assert "class LocalBinder" in service_text and "onBind" in service_text, "Binder do Service ausente"
assert "SessionManager" in service_text and "startShell" in service_text, "Service não hospeda runtime"
assert 'include(":android:app")' in settings.read_text(), "módulo app ausente"
assert 'include(":android:runtime")' in settings.read_text(), "módulo runtime ausente"
assert buffer.is_file(), "buffer limitado de saída ausente"
assert "TerminalOutputBuffer()" in view_model_text, "ViewModel não usa buffer limitado"

result = subprocess.run(
    ["bash", str(test_script)],
    check=True,
    capture_output=True,
    text=True,
)
assert "terminal_output_buffer=ok cases=6" in result.stdout, "testes do buffer não concluíram"
print(result.stdout.strip())
print("phase3_structure=ok compose_mvvm_domain_data_service_separated=true transcript_bounded=true")
