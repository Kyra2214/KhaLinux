#!/usr/bin/env python3
"""Auditoria de segurança estática do KhaLinux (Fase 9.1).

Somente biblioteca padrão. Não acessa a rede nem modifica arquivos.
Uso: python3 distribution/scripts/audit-security.py [--json] [--raiz DIR]
Saída: lista de achados; código de saída 1 se houver qualquer achado ERRO.
Regras documentadas em docs/security-audit.md.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

# Permissões que o app declara de forma intencional (ver docs/security-audit.md).
PERMISSOES_PERMITIDAS = {
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.INTERNET",
    # RED/BLUE: hotspot local, observação da rede e permissões demonstradas no aparelho participante.
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
}

PADROES_SEGREDO = [
    ("chave privada", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----")),
    ("token GitHub", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b")),
    ("chave AWS", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("chave Google", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("token Slack", re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}\b")),
    ("atribuição de segredo", re.compile(
        r"(?i)\b(?:senha|password|passwd|secret|api[_-]?key|storePassword|keyPassword)\b\s*[:=]\s*[\"'][^\"'$\s]{6,}[\"']")),
]

APIS_PROIBIDAS_KOTLIN = [
    ("addJavascriptInterface", re.compile(r"\baddJavascriptInterface\b")),
    ("WebView com JavaScript", re.compile(r"\bsetJavaScriptEnabled\b")),
    ("modo de arquivo mundial", re.compile(r"\bMODE_WORLD_(?:READABLE|WRITEABLE)\b")),
    ("TrustManager próprio", re.compile(r"\bX509TrustManager\b|\bHostnameVerifier\b|\bALLOW_ALL_HOSTNAME_VERIFIER\b")),
    ("SSL sem verificação", re.compile(r"\bSSLContext\.getInstance\b")),
    ("printStackTrace", re.compile(r"\.printStackTrace\(\)")),
    ("Runtime.exec", re.compile(r"Runtime\.getRuntime\(\)\.exec\b")),
    ("log do Android", re.compile(r"\bLog\.[vdiwe]\(")),
]

# ProcessBuilder só é aceito nestes arquivos (lançadores centralizados).
PROCESSBUILDER_PERMITIDO = {
    "ProotLauncher.kt",
    "RuntimeDiagnostics.kt",
}


class Auditoria:
    def __init__(self, raiz: Path) -> None:
        self.raiz = raiz
        self.achados: list[dict] = []
        self.verificacoes = 0

    def achado(self, nivel: str, regra: str, local: str, detalhe: str) -> None:
        self.achados.append({"nivel": nivel, "regra": regra, "local": local, "detalhe": detalhe})

    def ok(self) -> None:
        self.verificacoes += 1

    def rel(self, caminho: Path) -> str:
        return str(caminho.relative_to(self.raiz))

    def arquivos(self, *padroes: str) -> list[Path]:
        saida: set[Path] = set()
        for padrao in padroes:
            saida.update(p for p in self.raiz.glob(padrao) if p.is_file() and "/build/" not in p.as_posix())
        return sorted(saida)


def auditar_manifesto(a: Auditoria) -> None:
    caminho = a.raiz / "android/app/src/main/AndroidManifest.xml"
    if not caminho.is_file():
        a.achado("ERRO", "manifesto", "android/app/src/main/AndroidManifest.xml", "arquivo ausente")
        return
    arvore = ET.parse(caminho).getroot()
    app = arvore.find("application")
    if app is None:
        a.achado("ERRO", "manifesto", a.rel(caminho), "elemento <application> ausente")
        return

    def attr(no: ET.Element, nome: str) -> str | None:
        return no.get(ANDROID_NS + nome)

    a.ok()
    if attr(app, "allowBackup") != "false":
        a.achado("ERRO", "backup", a.rel(caminho), "android:allowBackup deve ser \"false\"")
    a.ok()
    if attr(app, "debuggable") == "true":
        a.achado("ERRO", "debuggable", a.rel(caminho), "android:debuggable=\"true\" não pode estar no manifesto")
    a.ok()
    if attr(app, "usesCleartextTraffic") != "false":
        a.achado("ERRO", "cleartext", a.rel(caminho), "declare android:usesCleartextTraffic=\"false\" explicitamente")
    a.ok()
    for no in arvore.findall("uses-permission"):
        nome = attr(no, "name") or ""
        if nome not in PERMISSOES_PERMITIDAS:
            a.achado("ERRO", "permissao", a.rel(caminho), f"permissão fora da lista aprovada: {nome}")
    a.ok()
    for tag in ("activity", "service", "receiver", "provider"):
        for no in app.findall(tag):
            nome = attr(no, "name") or "?"
            exportado = attr(no, "exported")
            tem_filtro = no.find("intent-filter") is not None
            if exportado is None and tem_filtro:
                a.achado("ERRO", "exported", a.rel(caminho), f"{tag} {nome} com intent-filter sem android:exported explícito")
            if exportado == "true" and not (tag == "activity" and nome == ".MainActivity"):
                a.achado("ERRO", "exported", a.rel(caminho), f"{tag} {nome} exportado; somente .MainActivity (LAUNCHER) é permitido")
            if tag != "activity" and exportado is None:
                a.achado("ERRO", "exported", a.rel(caminho), f"{tag} {nome} sem android:exported explícito")
    a.ok()


def auditar_segredos(a: Auditoria) -> None:
    for proibido in a.arquivos("**/*.jks", "**/*.keystore", "**/*.p12", "**/*.pem", "**/*.pfx", "**/.env", "**/.env.*",
                               "**/local.properties", "**/keystore.properties"):
        a.achado("ERRO", "segredo", a.rel(proibido), "arquivo de credencial/chave não deve estar no projeto")
    a.ok()
    sufixos = {".kt", ".java", ".kts", ".gradle", ".xml", ".json", ".yml", ".yaml", ".sh", ".py", ".md", ".properties",
               ".txt", ".tsv", ".c"}
    for arq in sorted(a.raiz.rglob("*")):
        if not arq.is_file() or arq.suffix not in sufixos:
            continue
        pos = arq.as_posix()
        if "/build/" in pos or "/.git/" in pos or "/.gradle/" in pos:
            continue
        if arq.name == "audit-security.py":
            continue
        try:
            texto = arq.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for rotulo, padrao in PADROES_SEGREDO:
            for m in padrao.finditer(texto):
                linha = texto.count("\n", 0, m.start()) + 1
                a.achado("ERRO", "segredo", f"{a.rel(arq)}:{linha}", f"possível {rotulo}")
    a.ok()


def auditar_kotlin(a: Auditoria) -> None:
    for arq in a.arquivos("android/*/src/main/**/*.kt", "android/*/src/main/**/*.java"):
        texto = arq.read_text(encoding="utf-8", errors="replace")
        for rotulo, padrao in APIS_PROIBIDAS_KOTLIN:
            for m in padrao.finditer(texto):
                linha = texto.count("\n", 0, m.start()) + 1
                # Referências em comentários/KDoc são ignoradas.
                inicio = texto.rfind("\n", 0, m.start()) + 1
                prefixo = texto[inicio:m.start()].lstrip()
                if prefixo.startswith(("*", "//", "/*")):
                    continue
                a.achado("ERRO", "api-proibida", f"{a.rel(arq)}:{linha}", rotulo)
        if "ProcessBuilder(" in texto and arq.name not in PROCESSBUILDER_PERMITIDO:
            a.achado("ERRO", "processbuilder", a.rel(arq), "ProcessBuilder fora dos lançadores centralizados")
        for m in re.finditer(r"""["']http://[^"'\s]+""", texto):
            linha = texto.count("\n", 0, m.start()) + 1
            url = m.group(0)[1:]
            if "schemas.android.com" in url:
                continue
            a.achado("AVISO", "http-claro", f"{a.rel(arq)}:{linha}", f"URL sem TLS: {url} (confirmar que é tráfego do convidado/loopback)")
    a.ok()


def auditar_downloads(a: Auditoria) -> None:
    # Manifestos de release: HTTPS e SHA-256 de 64 hex.
    for arq in a.arquivos("distribution/manifests/release/*.json"):
        dados = json.loads(arq.read_text(encoding="utf-8"))
        for chave, valor in dados.items():
            if chave.endswith("_url") and isinstance(valor, str) and not valor.startswith("https://"):
                a.achado("ERRO", "download", a.rel(arq), f"{chave} não usa HTTPS")
            if chave.endswith("sha256") and not (isinstance(valor, str) and re.fullmatch(r"[a-f0-9]{64}", valor)):
                a.achado("ERRO", "hash", a.rel(arq), f"{chave} não é SHA-256 hex de 64 caracteres")
    a.ok()
    # Catálogo: fontes por URL exigem HTTPS e hash.
    catalogo = a.raiz / "distribution/packages/catalog/catalog.json"
    if catalogo.is_file():
        for pacote in json.loads(catalogo.read_text(encoding="utf-8")).get("packages", []):
            fonte = pacote.get("source", {})
            nome = pacote.get("name", "?")
            url = fonte.get("url")
            if url is not None:
                if not str(url).startswith("https://"):
                    a.achado("ERRO", "download", f"catalog.json:{nome}", "fonte com URL não HTTPS")
                sha = pacote.get("verification", {}).get("sha256")
                if not (isinstance(sha, str) and re.fullmatch(r"[a-fA-F0-9]{64}", sha)):
                    a.achado("ERRO", "hash", f"catalog.json:{nome}", "fonte por URL sem SHA-256")
    a.ok()


def auditar_laboratorios(a: Auditoria) -> None:
    for arq in a.arquivos("distribution/labs/fixtures/**/*.py"):
        texto = arq.read_text(encoding="utf-8", errors="replace")
        if re.search(r"""["']0\.0\.0\.0["']|["']{2}\s*,\s*\d+\)""", texto) or "INADDR_ANY" in texto:
            a.achado("ERRO", "escopo-lab", a.rel(arq), "serviço de laboratório não pode escutar fora de 127.0.0.1")
    a.ok()
    hosts_ok = {"127.0.0.1", "localhost", "::1"}
    labs = a.raiz / "distribution/labs/labs.json"
    if labs.is_file():
        for lab in json.loads(labs.read_text(encoding="utf-8")).get("labs", []):
            for servico in lab.get("services", []):
                host = servico.get("host", servico.get("bind", "127.0.0.1"))
                if host not in hosts_ok:
                    a.achado("ERRO", "escopo-lab", f"labs.json:{lab.get('id')}", f"serviço com host {host}")
                comando = servico.get("command", "")
                if "http.server" in comando and not re.search(r"--bind\s+(127\.0\.0\.1|::1|localhost)\b", comando):
                    a.achado("ERRO", "escopo-lab", f"labs.json:{lab.get('id')}", "http.server sem --bind em loopback")
    a.ok()


def auditar_scripts_e_ci(a: Auditoria) -> None:
    for arq in a.arquivos("distribution/**/*.sh", "android/**/*.sh"):
        texto = arq.read_text(encoding="utf-8", errors="replace")
        if "set -" not in texto or "pipefail" not in texto:
            a.achado("AVISO", "shell", a.rel(arq), "sem set -o pipefail/-e")
        if re.search(r"curl[^\n|]*\|\s*(?:sudo\s+)?(?:ba)?sh\b", texto):
            a.achado("ERRO", "shell", a.rel(arq), "curl | sh")
    a.ok()
    for arq in a.arquivos(".github/workflows/*.yml"):
        texto = arq.read_text(encoding="utf-8", errors="replace")
        if "pull_request_target" in texto:
            a.achado("ERRO", "ci", a.rel(arq), "pull_request_target com código de PR")
        if not re.search(r"^permissions:", texto, re.MULTILINE):
            a.achado("ERRO", "ci", a.rel(arq), "workflow sem bloco permissions de menor privilégio")
        for m in re.finditer(r"uses:\s*([\w./-]+)@([^\s#]+)", texto):
            if m.group(2) in {"main", "master", "latest"}:
                a.achado("ERRO", "ci", a.rel(arq), f"ação sem versão fixa: {m.group(1)}@{m.group(2)}")
    a.ok()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="saída em JSON")
    parser.add_argument("--raiz", default=str(Path(__file__).resolve().parents[2]))
    args = parser.parse_args()
    auditoria = Auditoria(Path(args.raiz).resolve())
    for etapa in (auditar_manifesto, auditar_segredos, auditar_kotlin, auditar_downloads,
                  auditar_laboratorios, auditar_scripts_e_ci):
        etapa(auditoria)
    erros = [x for x in auditoria.achados if x["nivel"] == "ERRO"]
    avisos = [x for x in auditoria.achados if x["nivel"] == "AVISO"]
    if args.json:
        print(json.dumps({"verificacoes": auditoria.verificacoes, "erros": len(erros), "avisos": len(avisos),
                          "achados": auditoria.achados}, ensure_ascii=False, indent=2))
    else:
        for x in auditoria.achados:
            print(f"[{x['nivel']}] {x['regra']} — {x['local']}: {x['detalhe']}")
        print(f"Auditoria: {auditoria.verificacoes} grupos de verificação, {len(erros)} erro(s), {len(avisos)} aviso(s).")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main())
