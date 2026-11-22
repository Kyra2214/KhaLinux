# KhaLinux 1.0.0-rc.1 (candidata a release)

> **Leia primeiro:** esta é uma **candidata**, não a versão estável. O código das Fases 0–9 está completo, mas o app **ainda não foi executado em aparelho físico** e parte da suíte de testes Kotlin nunca foi executada (ver "Estado de validação"). A 1.0.0 estável só deve ser publicada depois da validação final registrada em `docs/final-validation.md`.

## O que é

Ambiente Linux (Debian Bookworm ARM64) executado localmente no Android 15+ (ARM64, sem root) via PRoot, para aprendizado, diagnóstico, auditoria autorizada e prática de cibersegurança.

## Recursos

- **Instalação automática** de PRoot e RootFS com progresso 0–100%, download retomável e hashes pinados.
- **Terminal** com teclas extras, emulador VT/ANSI básico, histórico, favoritos, 5 temas e até 4 abas.
- **Gerenciador de pacotes** com catálogo, dependências, instalação/remoção/atualização e histórico auditável.
- **Cyber Toolkit** (reconhecimento/rede, Web, forense, auditoria) com política de uso autorizado e confirmação de escopo.
- **Laboratórios**: 12 laboratórios e 51 exercícios (Linux, rede, Web, forense), só em `127.0.0.1`, com pontuação e progresso.
- **Scripts, gerenciador de arquivos, relatórios, notas de saída e tutor offline** baseado em regras.
- **Otimização e recuperação**: higiene de armazenamento, limites de memória, inicialização preguiçosa, ciclo de processos e recuperação de instalação interrompida.
- **Auditoria estática** (`distribution/scripts/audit-security.py`) integrada ao CI e ao workflow de release.

## Requisitos

Android 15+ (API 35), ARM64, rede no primeiro uso, alguns GB livres. Detalhes em `docs/install-guide.md`.

## Estado de validação

| Item | Estado |
|---|---|
| Runtime (Fases 3–5), PTY nativo no host | executado anteriormente no host (88 testes) |
| Runtime Kotlin (Fases 2–8), 166 testes no host | **aprovado** (compilador 2.0.21 em modo 1.9 + shim `kotlin.test`; repetir no CI) |
| Módulo `:android:app` | só compilável no CI; não conferido |
| APK release (R8) e assinatura | configurado; **nunca gerado** |
| Execução em aparelho físico (PRoot em `filesDir`, targetSdk 35) | **não realizada** |
| Auditoria estática | executada: 0 erros (`docs/final-validation.md`) |

## Limitações conhecidas

- PRoot não é sandbox nem VM; `/proc` e `/sys` do Android não são montados (ADR-0005). Ferramentas que dependem deles ficam limitadas.
- Risco principal: bloqueio de execução de binários em `filesDir` no Android 15+; plano B (`libproot.so`) **não** está empacotado (`docs/diagnostics.md`).
- Nenhuma ferramenta do toolkit foi verificada em aparelho; o catálogo marca "Não verificado em ARM64/PRoot".
- RootFS/downloads ainda não foram reduzidos (exige nova build no CI).
- Terminal: emulador VT/ANSI básico, sem compatibilidade xterm completa.
- Tutor offline por regras, não é IA generativa.

## Verificar o APK

```bash
sha256sum -c KhaLinux-1.0.0-rc.1.apk.sha256
apksigner verify --print-certs KhaLinux-1.0.0-rc.1.apk
```

## Segurança

Relate vulnerabilidades em privado (`SECURITY.md`). Use ferramentas e laboratórios apenas em sistemas que você possui ou tem autorização explícita para avaliar.
