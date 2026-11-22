#!/usr/bin/env python3
"""Valida a separação Activity -> Service -> runtime sem exigir Android SDK."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
manifest = ROOT / "android/app/src/main/AndroidManifest.xml"
activity = ROOT / "android/app/src/main/kotlin/com/khalinux/android/MainActivity.kt"
service = ROOT / "android/app/src/main/kotlin/com/khalinux/android/KhaLinuxRuntimeService.kt"
settings = ROOT / "settings.gradle.kts"

root = ET.parse(manifest).getroot()
ns = {"a": "http://schemas.android.com/apk/res/android"}
services = root.findall(".//service")
activities = root.findall(".//activity")
assert any(s.attrib.get(f"{{{ns['a']}}}exported") == "false" for s in services), "Service deve ser privado"
assert any(a.attrib.get(f"{{{ns['a']}}}exported") == "true" for a in activities), "Activity launcher ausente"
activity_text = activity.read_text()
service_text = service.read_text()
assert "bindService" in activity_text, "Activity não faz bind no Service"
assert "ProcessBuilder" not in activity_text and "ProotLauncher" not in activity_text, "UI acoplando execução ao runtime"
assert "class LocalBinder" in service_text and "onBind" in service_text, "Binder do Service ausente"
assert "SessionManager" in service_text and "startShell" in service_text, "Service não hospeda runtime"
assert 'include(":android:app")' in settings.read_text(), "módulo app ausente"
assert 'include(":android:runtime")' in settings.read_text(), "módulo runtime ausente"
print("phase3_structure=ok activity_service_runtime_separated=true")
