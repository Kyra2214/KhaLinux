# Web: entrada refletida sem escape

Trilha: web · Dificuldade: intermediario · Ferramentas: curl · Pré-requisitos: web-http-basico

Lab intermediário (exige `web-http-basico`). A busca reflete `q` no HTML sem escapar (propositalmente). O exercício é observar o comportamento com `curl` e entender a correção: escapar a saída (`&lt;`, `&gt;`, `&amp;`). Nada é executado em navegador.

## Serviços

- `busca-local`: `python3 /opt/khalinux-labs/web/reflect_server.py 8091` (127.0.0.1:8091)

O código do serviço está em `distribution/labs/fixtures/web/` e deve estar em `/opt/khalinux-labs/web/` no RootFS (cópia feita pelo app em 6.8; até lá, copie manualmente). Inicie num terminal e deixe rodando; encerre com Ctrl+C.

## Exercícios

- `refletida` — A entrada volta? (iniciante)
- `tag-refletida` — Tag preservada (intermediario)
- `entidade-lt` — Escape do menor-que (intermediario)

## Escopo

Escuta só em 127.0.0.1 (o código fixa o endereço); nenhum alvo externo. Respostas conferidas por SHA-256 (ver `docs/labs.md`). Todas as respostas foram conferidas no host executando os serviços e `curl`.
