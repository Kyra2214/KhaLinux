# Web: HTTP na prática

Trilha: web · Dificuldade: iniciante · Ferramentas: curl · Pré-requisitos: rede-sockets

Lab iniciante (exige `rede-sockets`). Servidor didático sem vulnerabilidades: pratique `curl -I`, `-w '%{http_code}'` e leitura de `robots.txt`. Lição: um caminho listado no robots.txt não está protegido.

## Serviços

- `info-local`: `python3 /opt/khalinux-labs/web/info_server.py 8090` (127.0.0.1:8090)

O código do serviço está em `distribution/labs/fixtures/web/` e deve estar em `/opt/khalinux-labs/web/` no RootFS (cópia feita pelo app em 6.8; até lá, copie manualmente). Inicie num terminal e deixe rodando; encerre com Ctrl+C.

## Exercícios

- `status-raiz` — Status da raiz (iniciante)
- `cabecalho-lab` — Cabeçalho personalizado (iniciante)
- `robots-disallow` — O que o robots.txt esconde (iniciante)
- `flag-segredo` — Página não listada (intermediario)

## Escopo

Escuta só em 127.0.0.1 (o código fixa o endereço); nenhum alvo externo. Respostas conferidas por SHA-256 (ver `docs/labs.md`). Todas as respostas foram conferidas no host executando os serviços e `curl`.
