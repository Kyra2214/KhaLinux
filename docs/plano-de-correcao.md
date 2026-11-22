# Plano de correção (código primeiro, depois teste físico)

> Origem: auditoria estática de 2026-11-22. Nada aqui foi compilado ou executado em aparelho.
> Ordem: instalação automática com progresso, bugs de código, terminal, release/CI e ponto de teste físico.
> Cada fase termina com o CI verde.

## Fase 1: Instalação automática com porcentagem (prioridade) — CONCLUÍDA em 3.1 (CHECKPOINT_001)

**Comportamento desejado:** na primeira abertura o app instala tudo sozinho e mostra uma barra única de 0 a 100%. Nas aberturas seguintes não repete nada.

1. **Máquina de estados persistida** (`InstallState`): `CHECKING → DOWNLOADING_PROOT → DOWNLOADING_ROOTFS → VERIFYING → EXTRACTING → FINALIZING → READY | FAILED(motivo)`. Salva em arquivo; reabrir o app retoma em vez de recomeçar.
2. **Duas porcentagens reais:**
   - Download: `baixado / archive_bytes` (já vem do manifesto).
   - Instalação: `bytes extraídos / rootfs_bytes`. `extractSafely` já soma `extractedBytes`; falta emitir o callback.
   - A UI mostra fase, porcentagem, MB e velocidade. Ex.: "Baixando Debian… 62% (217/350 MB)".
3. **Foreground service** (tipo `dataSync`) durante a instalação, com notificação de progresso, para o download de ~350 MB não morrer ao sair do app. Pedir `POST_NOTIFICATIONS` na primeira abertura.
4. **Download retomável:** `Range` com arquivo `.part`, SHA-256 validado só no final. 3 tentativas com backoff. Botões "Tentar novamente" e "Cancelar".
5. **Ordem:** PRoot primeiro (pequeno, falha rápido), depois RootFS. Uma única barra ponderada pelo tamanho.
6. **Pós-instalação (`FINALIZING`):** criar `/etc/resolv.conf`, `/home/khalinux` e os pontos de bind; rodar `proot -- /bin/true` de verificação. O resultado entra no estado final.
7. **Espaço em disco:** considerar o pico real (arquivo + staging + backup antigo) antes de começar.
8. **Reparo:** "Reinstalar" sem perder o home (ver fase 2).

## Fase 2: Bugs de código sem aparelho

- **Rotação:** comparar a instância do Service em `attachRuntime` (não o gateway) e reconectar a sessão viva em vez de declarar "interrompida".
- **Histórico:** não gravar entrada de prompts sem eco nem texto digitado no shell interativo; só comandos do modo "Comando Bash".
- **Concorrência:** `runtime` como `@Volatile`; fechar o fd do PTY só depois de encerrar a leitura; tirar o `cancel` bloqueante da thread principal.
- **Remover** `installRootfs(url, sha)` e o `TerminalOutputBuffer` morto.
- **PRoot:** ajustar `PROOT_TMP_DIR` e `--kill-on-exit`; avaliar bind de `/proc`.
- **Home persistente** fora do RootFS (bind de `filesDir/home`).
- **UI:** `systemBarsPadding` e `imePadding`; textos em `strings.xml`; esconder o UUID da sessão.

## Fase 3: Terminal utilizável

- Barra de teclas extras: Ctrl, Alt, Tab, ESC, setas e Ctrl+C como bytes brutos (`0x03` etc.).
- Conflar a saída: atualizar o snapshot no máximo a cada ~16–33 ms e limitar a fila de eventos.

## Fase 4: Release, CI e documentação

- Tags imutáveis (uma por build), sem `overwrite_files`; hashes atualizados no app por PR.
- Commitar o manifesto completo do RootFS e o `proot-arm64.json`.
- Gradle wrapper, `exportSchema = true`, `LICENSE` Apache-2.0 completo.
- Trocar `validate-phase3.py` por testes reais; consolidar tudo no Gradle.
- Atualizar README, ARCHITECTURE e android/runtime/README.
- Novos testes: máquina de instalação, download com retomada, ViewModel (rotação), migração Room.

## Fase 5: Ponto de teste físico

Tela **"Diagnóstico"** que roda sozinha e mostra OK/falha por item:

1. O app consegue executar o `proot`? (`--version`). Teste decisivo do possível bloqueio de execução em `filesDir`.
2. Um shell entra no RootFS (`uname -m`, `id`).
3. DNS e rede funcionam (`getent hosts debian.org`, `curl`).
4. PTY funciona (resize e Ctrl+C).
5. A sessão sobrevive à rotação e a sessão longa em segundo plano.

Se o item 1 falhar, o plano B (PRoot como `libproot.so` em `jniLibs`) fica previsto numa abstração `ProotLocator`, sem reescrever o resto.

## Entrega em pacotes (cada um compilável)

- **A:** Fase 1 completa (instalação automática com porcentagem).
- **B:** Fase 2 + tela de Diagnóstico.
- **C:** Fases 3 e 4.

## Riscos registrados na auditoria

1. Execução do PRoot a partir de `filesDir` com `targetSdk 35` (provável bloqueio SELinux; não confirmado).
2. Manifesto versionado do RootFS diverge do que o app exige; `proot-arm64.json` ausente do repo.
3. Rebuild com `overwrite_files` na mesma tag quebra os hashes pinados no APK.
4. Rotação de tela orfana a sessão ativa.
5. Sem `/etc/resolv.conf` no RootFS.
6. Entrada interativa é gravada no histórico (possíveis senhas).
