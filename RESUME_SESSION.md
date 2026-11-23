# RESUME_SESSION

- Último ZIP: KhaLinux_CHECKPOINT_047.zip (corrige extração de symlinks do RootFS + UI nova; NÃO compilado nesta sessão — rodar `:android:runtime` testes e `assembleDebug` no CI)
- Concluídos: **implementação 47/47 (Fases 0–9 em código)**. Validação **parcial** (`docs/final-validation.md`): runtime 166/166 testes no host, auditoria 0 erros, PTY nativo OK.
- Próximo: no CI rodar `:android:app:testDebugUnitTest`/`assembleDebug`, commitar `android/app/schemas`, gerar o release assinado (`docs/release.md`, R8), conferir `kotlin-test` real; no aparelho: Diagnóstico (PRoot em `filesDir`), instalação limpa, atualização, recuperação, toolkit, labs e medições. Só então promover `1.0.0-rc.1` → `1.0.0`.
- Como reproduzir a suíte local: compilador embutido do Gradle (`kotlin-compiler-embeddable` 2.0.21, `-language-version 1.9`) + JUnit/commons do `lib/` do Gradle + shim de `kotlin.test` (descrito em `docs/final-validation.md`); ou, com kotlinc 1.9.24, `SKIP_NATIVE_PTY=1 bash android/runtime/test.sh`.
- Suposições: nameservers 1.1.1.1/8.8.8.8; percentual de extração aproximado; plano B (`libproot.so`) não empacotado; `/proc` montado, `/sys` não (ADR-0005 revisada); hashes pinados nas releases `-v1`; fixtures dos labs em `/opt/khalinux-labs`; serviços de lab iniciados manualmente; no máximo 4 abas/sessões; tutor sem modelo (ADR-0006).
- Pendências abertas: nenhuma ferramenta do toolkit verificada em aparelho; commitar esquemas Room; testes de ViewModel/Room (precisam de Android); teste físico via Diagnóstico; revisão de `native_pty.c`; reduzir RootFS/downloads exige nova build no CI.
- Bloqueios: PRoot em filesDir (targetSdk 35) só em aparelho físico; `kotlinc`/rede ausentes; o ZIP de entrada não contém `.git` (sem histórico Git nesta sessão).
