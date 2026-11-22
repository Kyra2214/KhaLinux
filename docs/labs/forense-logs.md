# Forense: análise de log de acesso

Trilha: forense · Dificuldade: avancado · Pré-requisitos: forense-assinaturas

Lab avançado (exige `forense-assinaturas`). Analise um log de acesso didático com `grep`, `awk`, `sort` e `uniq`. Os IPs vêm das faixas de documentação (RFC 5737) e não representam redes reais.

## Evidências

Geradas de forma determinística por `distribution/labs/fixtures/forense/gerar_evidencias.py`, previsto para `/opt/khalinux-labs/forense/` no RootFS (cópia pelo app em 6.8; até lá, manual). O script só grava arquivos novos no diretório informado.

## Exercícios

- `total-falhas` — Total de falhas (intermediario)
- `ip-top` — IP com mais falhas (avancado)
- `usuario-alvo` — Usuário mais visado (avancado)
- `primeira-falha` — Primeira falha (avancado)

## Escopo

Sem serviços e sem rede; só arquivos didáticos locais. Respostas conferidas no host com `sha256sum`, `od`, `wc` e `grep`. Hashes de resposta SHA-256 (ver `docs/labs.md`).
