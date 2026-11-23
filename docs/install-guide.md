# Guia de instalação

> **Estado:** o app **ainda não foi validado em aparelho físico** (ver `ROADMAP.md`). Este guia descreve o comportamento implementado; a primeira instalação real é o teste decisivo (PRoot em `filesDir` com `targetSdk 35`).

## Requisitos

| Item | Requisito |
|---|---|
| Android | 15 ou superior (API 35; `minSdk = 35`) |
| Arquitetura | ARM64 (`arm64-v8a`) |
| Root | Não necessário |
| Rede | Wi-Fi recomendado no primeiro uso (download do RootFS e do PRoot) |
| Espaço | Reserve alguns GB livres. O tamanho exato do RootFS é informado no manifesto `distribution/manifests/release/rootfs-arm64.json` (`archive_bytes`/`rootfs_bytes`) |

## Obter o APK

- **Release assinado:** na página de releases do repositório (a partir da 1.0.0; ver `docs/release.md`). Confira o SHA-256 publicado junto ao APK antes de instalar.
- **Build local:** `./gradlew :android:app:assembleDebug` gera um APK de depuração (assinado com a chave de depuração do Android, apenas para testes).

## Instalar

1. Copie o APK para o aparelho e abra-o. O Android pedirá permissão para instalar de "fontes desconhecidas" para o aplicativo usado (navegador/gerenciador de arquivos).
2. Confirme a instalação.
3. Abra **KhaLinux** e **permita notificações** quando solicitado (a instalação e as sessões usam notificação persistente de serviço em primeiro plano).

## Primeira abertura

O app instala o ambiente sozinho, com barra de 0–100% (detalhes em `docs/installation-flow.md`):

1. PRoot (0–3%) → 2. download do RootFS (3–58%) → 3. verificação SHA-256 (58–60%) → 4. extração (60–97%) → 5. finalização (97–100%).

- O download é **retomável**: se a rede cair, reabra o app ou toque em **Tentar novamente**; o arquivo parcial é aproveitado.
- **Cancelar instalação** interrompe o processo; o parcial é mantido para retomar.
- Cada artefato é verificado contra o hash fixado no app; divergência apaga o arquivo e a instalação falha em vez de continuar.
- Não feche o app à força durante a extração; se isso ocorrer, a recuperação (`docs/recovery.md`) trata resíduos na próxima abertura.

## Conferir se funcionou

1. Na tela principal aparece "Runtime Linux instalado".
2. Toque em **Diagnóstico** e aguarde. Itens esperados: PRoot executa, shell no RootFS (`aarch64`), DNS, rede, PTY. Rotação e segundo plano são manuais.
3. Se algum item falhar, use **Copiar relatório** e anexe-o ao reportar o problema (sem dados pessoais).

## Atualizar

Instale o APK mais novo **por cima** do atual (mesma assinatura); os dados do RootFS, pacotes, scripts e progresso ficam no armazenamento privado do app e são preservados. Mudanças de esquema do banco têm migração (Room 2→3 já prevista). A atualização com preservação de dados ainda não foi testada em aparelho.

## Desinstalar

Desinstalar o app **apaga todos os dados** (RootFS, home, scripts, relatórios, progresso). Antes, salve em um local externo o que quiser guardar (o app não faz backup: `allowBackup=false`).

## Problemas comuns

| Sintoma | O que fazer |
|---|---|
| "PRoot executa" falha no Diagnóstico | Provável bloqueio de execução em `filesDir` no Android 15+. É o risco conhecido (ver `docs/diagnostics.md`, plano B `libproot.so` não empacotado). Reporte com o relatório copiado. |
| Instalação para em X% | Verifique rede e espaço livre; toque em **Tentar novamente**. |
| Sem notificação / sessão encerra | Permita notificações e desative a otimização agressiva de bateria para o app. |
| Pouco espaço | **Arquivos → Limpar resíduos de instalação** (ver `docs/storage.md`). |
| Falha de hash | Não ignore: o artefato foi corrompido ou alterado. Tente de novo em outra rede; se persistir, reporte. |

## Limitações do Android sem root

PRoot não é uma máquina virtual nem um sandbox forte; o `/proc` do Android é montado, mas `/sys` não (ADR-0005 revisada), então algumas ferramentas que dependem do kernel falham ou ficam limitadas. Veja `SECURITY.md`.
