# Forense: integridade com hashes

Trilha: forense · Dificuldade: iniciante · Pré-requisitos: nenhum

Lab iniciante. Calcule SHA-256 com `sha256sum` (coreutils do RootFS) e compare. Lição: um único caractere alterado muda o hash inteiro; hashes iguais indicam conteúdo idêntico.

## Evidências

Geradas de forma determinística por `distribution/labs/fixtures/forense/gerar_evidencias.py`, previsto para `/opt/khalinux-labs/forense/` no RootFS (cópia pelo app em 6.8; até lá, manual). O script só grava arquivos novos no diretório informado.

## Exercícios

- `hash-tamanho` — Tamanho do SHA-256 (iniciante)
- `hash-prefixo` — Prefixo do hash (iniciante)
- `hash-duplicados` — Cópias idênticas (iniciante)
- `hash-distintos` — Hashes distintos (iniciante)

## Escopo

Sem serviços e sem rede; só arquivos didáticos locais. Respostas conferidas no host com `sha256sum`, `od`, `wc` e `grep`. Hashes de resposta SHA-256 (ver `docs/labs.md`).
