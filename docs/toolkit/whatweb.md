# WhatWeb

- **Grupo:** web · **Pacote:** `whatweb` · **Comandos:** `whatweb`
- **Verificação:** `whatweb --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Identificação de tecnologias Web (CMS, servidor, bibliotecas) a partir das respostas HTTP de alvos autorizados.

## Uso autorizado

**Exige confirmação de escopo** antes do uso (ver `docs/toolkit.md`). Use apenas contra sistemas que você possui ou para os quais tem autorização explícita; os exemplos abaixo usam um laboratório local (`localhost`).

## Limitações sem root

- Gera requisições HTTP ao alvo: use só em alvos próprios ou autorizados.
- Depende de Ruby e bibliotecas do APT; não verificado em ARM64/PRoot.

## Exemplos seguros (alvo próprio ou autorizado)

```text
whatweb -a 1 http://localhost:8080
whatweb --version
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install whatweb` no RootFS. Versão 0.5.5-1 (bookworm, arquitetura all). Há versão mais nova só em testing/unstable; não usar. A versão do catálogo foi lida em páginas de pacotes do Debian e ainda precisa ser conferida no snapshot APT fixado (5.6).
