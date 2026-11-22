# wfuzz

- **Grupo:** web · **Pacote:** `wfuzz` · **Comandos:** `wfuzz`
- **Verificação:** `wfuzz --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Fuzzing de URLs e parâmetros Web (substituição de FUZZ) em alvos autorizados.

## Uso autorizado

**Exige confirmação de escopo** antes do uso (ver `docs/toolkit.md`). Use apenas contra sistemas que você possui ou para os quais tem autorização explícita; os exemplos abaixo usam um laboratório local (`localhost`).

## Limitações sem root

- Ferramenta intrusiva: apenas alvos próprios ou autorizados; gera muitas requisições.
- Wordlists não acompanham o pacote; depende de python3-pycurl; não verificado em ARM64/PRoot.

## Exemplos seguros (alvo próprio ou autorizado)

```text
wfuzz -c -z file,<wordlist-propria> --hc 404 http://localhost:8080/FUZZ
wfuzz --version
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install wfuzz` no RootFS. Versão 3.1.0-2 (bookworm). O tracker mostra 3.1.0-6 em unstable; a do bookworm é 3.1.0-2. A versão do catálogo foi lida em páginas de pacotes do Debian e ainda precisa ser conferida no snapshot APT fixado (5.6).
