# Validação da Fase 6 — Laboratórios

**Estado: concluída em código; suíte NÃO executada nesta sessão.** O ambiente não tinha `kotlinc` (1.9.24 em `/opt/kotlinc`) nem rede para obtê-lo; por isso nada de Kotlin da Fase 6 foi compilado ou testado aqui. O teste dependia de serviço externo indisponível (download do compilador), e a pendência fica registrada em vez de marcada como validada.

## Entregue (6.1–6.8)

- Modelo, catálogo/parser, motor, pontuação (6.1–6.2); progresso persistido (6.3).
- 12 laboratórios/51 exercícios nas trilhas linux, rede, web e forense (6.4–6.7).
- 6.8: asset `labs.json` + `fixtures/` (srcDir `distribution/labs`), `LabsJson`, `KhaLinuxRuntimeService.labs()` (progresso em `runtime/labs/progress.tsv`, cópia das fixtures para `/opt/khalinux-labs` no RootFS), `LabsViewModel`/`LabsScreen`, botão "Laboratórios". Desafios progressivos = pré-requisitos do catálogo (laboratórios bloqueados até concluir os anteriores).
- Testes escritos, **não executados**: `LabsDataTest` (parser com `labs.json` real, ferramentas, docs, fixtures, loopback, chaves de resposta) e `LabProgressTest` (persistência, desbloqueio, reset, linhas inválidas).

## Verificado de fato (no host, fora do Kotlin)

- Fixtures Python executadas; respostas dos laboratórios Web e Forense obtidas com `curl`, `sha256sum`, `od`, `wc`, `grep`, `awk`. Geração das evidências determinística. Limites do modelo (tamanhos, loopback, portas, hashes, pré-requisitos, ferramentas do `tools.json`) conferidos em Python.

## Não verificado

- Compilação do runtime e do módulo `:android:app` (CI); execução dos testes novos e da suíte anterior (regressões); leitura do `labs.json` pelo parser Kotlin/`MiniJson`; cópia das fixtures e serviços no aparelho; respostas sobre `ss`/`ping` (sem as ferramentas no host).
- Serviços de laboratório são iniciados manualmente num terminal; o app não os inicia/encerra (suposição: fora do escopo da fase).

## Para fechar a fase

Com `kotlinc` e rede: `SKIP_NATIVE_PTY=1 PATH=/opt/kotlinc/bin:$PATH bash android/runtime/test.sh`; corrigir falhas; conferir o CI de `:android:app`; só então marcar a Fase 6 como validada.
