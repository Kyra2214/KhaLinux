# Web: injeção de SQL em app de brinquedo

Trilha: web · Dificuldade: avancado · Ferramentas: curl · Pré-requisitos: web-reflexao

Lab avançado (exige `web-reflexao`). Login de brinquedo que concatena entrada em SQL (propositalmente), com SQLite em memória e uma única instrução por consulta. Todo o ataque acontece contra 127.0.0.1 dentro do RootFS; a defesa é usar consultas parametrizadas. Não use estas técnicas contra sistemas que você não possui.

## Serviços

- `login-local`: `python3 /opt/khalinux-labs/web/login_server.py 8092` (127.0.0.1:8092)

O código do serviço está em `distribution/labs/fixtures/web/` e deve estar em `/opt/khalinux-labs/web/` no RootFS (cópia feita pelo app em 6.8; até lá, copie manualmente). Inicie num terminal e deixe rodando; encerre com Ctrl+C.

## Exercícios

- `login-visitante` — Login legítimo (iniciante)
- `senha-errada` — Credencial inválida (intermediario)
- `erro-aspa` — A aspa quebra a consulta (intermediario)
- `bypass-admin` — Entrar como admin (avancado)
- `defesa-sql` — A defesa correta (avancado)

## Escopo

Escuta só em 127.0.0.1 (o código fixa o endereço); nenhum alvo externo. Respostas conferidas por SHA-256 (ver `docs/labs.md`). Todas as respostas foram conferidas no host executando os serviços e `curl`.
