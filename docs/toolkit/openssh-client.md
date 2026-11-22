# OpenSSH Client (ssh, scp, ssh-keygen)

- **Grupo:** rede · **Pacote:** `openssh-client` · **Comandos:** `ssh`, `scp`, `ssh-keygen`
- **Verificação:** `ssh -V` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Acesso remoto seguro e transferência de arquivos a sistemas que você administra ou tem autorização para acessar.

## Uso autorizado

**Exige confirmação de escopo** antes do uso (ver `docs/toolkit.md`).

## Limitações sem root

- Chaves ficam em /home/khalinux (persistente fora do RootFS); proteger a passphrase.

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install openssh-client` no RootFS.
