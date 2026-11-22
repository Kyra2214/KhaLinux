# Rede: DNS, portas e TCP

Trilha: rede · Dificuldade: avancado · Ferramentas: dnsutils, iproute2 · Pré-requisitos: rede-sockets

Lab avançado (exige `rede-sockets`). Conceitos que não dependem de rede: portas conhecidas, tipos de registro DNS e o handshake TCP. Use `dig`/`ss` apenas para explorar; as respostas não exigem consultas externas.

## Serviços

Nenhum.

## Exercícios

- `dns-porta` — Porta do DNS (avancado)
- `dns-aaaa` — Registro IPv6 (avancado)
- `dns-mx` — Servidor de e-mail (avancado)
- `tcp-handshake` — Handshake de três vias (avancado)
- `portas-conhecidas` — Portas privilegiadas (avancado)

## Escopo

Só loopback; nenhum alvo externo. Nenhum laboratório usa `nmap` (exigiria confirmação de escopo). Respostas conferidas por SHA-256 da resposta normalizada (ver `docs/labs.md`).
