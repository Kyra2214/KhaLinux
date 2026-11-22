# Rede: sockets e HTTP local

Trilha: rede · Dificuldade: intermediario · Ferramentas: iproute2, curl · Pré-requisitos: rede-loopback

Lab intermediário (exige `rede-loopback`). Serviço `http-local` (`python3 -m http.server 8080 --bind 127.0.0.1`, já presente no RootFS base) escutando só em loopback. Inicie-o no terminal, inspecione com `ss -ltn` e requisite com `curl`. O serviço serve o diretório atual do shell: rode-o num diretório vazio.

## Serviços

- `http-local`: `python3 -m http.server 8080 --bind 127.0.0.1` (127.0.0.1:8080)

## Exercícios

- `ss-opcao` — Só sockets em escuta (intermediario)
- `ss-estado` — Estado do servidor (intermediario)
- `http-404` — Caminho inexistente (intermediario)
- `curl-head` — Só cabeçalhos (intermediario)
- `bind-loopback` — Acesso externo (intermediario)

## Escopo

Só loopback; nenhum alvo externo. Nenhum laboratório usa `nmap` (exigiria confirmação de escopo). Respostas conferidas por SHA-256 da resposta normalizada (ver `docs/labs.md`).
