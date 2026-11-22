# Nmap (nmap, nping)

- **Grupo:** reconhecimento · **Pacote:** `nmap` · **Comandos:** `nmap`, `nping`
- **Verificação:** `nmap --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Descoberta de hosts, varredura de portas e detecção de serviços em alvos autorizados.

## Uso autorizado

**Exige confirmação de escopo** antes do uso (ver `docs/toolkit.md`).

## Limitações sem root

- Sem root: apenas connect scan (-sT) e -Pn; SYN (-sS), detecção de SO (-O), ARP e ICMP raw falham.
- nping em modos raw exige privilégios indisponíveis.

## Exemplos seguros (alvo próprio ou autorizado)

```text
nmap -sT -Pn -p 22,80,443 <alvo-autorizado>
nmap --version
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install nmap` no RootFS. A versão do catálogo foi lida em packages.debian.org (bookworm) e ainda precisa ser conferida no snapshot APT fixado.
