# Forense: assinaturas de arquivo

Trilha: forense · Dificuldade: intermediario · Pré-requisitos: forense-hashes

Lab intermediário (exige `forense-hashes`). Use `od`, `wc` e `grep -a` para ler assinaturas e metadados. Lição: a extensão não prova o formato; confira os bytes iniciais.

## Evidências

Geradas de forma determinística por `distribution/labs/fixtures/forense/gerar_evidencias.py`, previsto para `/opt/khalinux-labs/forense/` no RootFS (cópia pelo app em 6.8; até lá, manual). O script só grava arquivos novos no diretório informado.

## Exercícios

- `magic-hex` — Bytes iniciais (intermediario)
- `tipo-real` — Formato real (intermediario)
- `tamanho-bytes` — Tamanho do arquivo (intermediario)
- `flag-png` — Texto escondido (avancado)

## Escopo

Sem serviços e sem rede; só arquivos didáticos locais. Respostas conferidas no host com `sha256sum`, `od`, `wc` e `grep`. Hashes de resposta SHA-256 (ver `docs/labs.md`).
