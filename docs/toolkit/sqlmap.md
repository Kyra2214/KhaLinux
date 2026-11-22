# sqlmap

- **Grupo:** web · **Pacote:** `sqlmap` · **Comandos:** `sqlmap`
- **Verificação:** `sqlmap --version` · **Estado:** não verificado em ARM64/PRoot (sem evidência registrada)

Teste automatizado de injeção de SQL em aplicações Web autorizadas.

## Uso autorizado

**Exige confirmação de escopo** antes do uso (ver `docs/toolkit.md`). Use apenas contra sistemas que você possui ou para os quais tem autorização explícita; os exemplos abaixo usam um laboratório local (`localhost`).

## Limitações sem root

- Ferramenta intrusiva: apenas alvos próprios ou com autorização escrita e escopo definido.
- Depende de python3; não verificado em ARM64/PRoot.

## Exemplos seguros (alvo próprio ou autorizado)

```text
sqlmap -u "http://localhost:8080/item?id=1" --batch --level=1 --risk=1
sqlmap --version
```

## Instalação

Pelo gerenciador de pacotes (tela Pacotes) ou `apt-get install sqlmap` no RootFS. Versão 1.7.2-1 (bookworm, arquitetura all). A versão do catálogo foi lida em páginas de pacotes do Debian e ainda precisa ser conferida no snapshot APT fixado (5.6).
