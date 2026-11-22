# Remoção de pacotes (submódulo 4.5)

`PackageRemover` em `android/runtime/.../packages/PackageRemover.kt`.

- Remove apenas pacotes presentes no registro (`InstalledPackageStore`).
- Recusa: pacotes protegidos (`apt`, `bash`, `coreutils`, `ca-certificates`, `dpkg`) e pacotes com dependentes instalados (mensagem lista os dependentes).
- `debian-apt`: `apt-get remove -y '<nome>'`; sem `purge` e sem `autoremove`. Origem direta: apaga `/opt/khalinux/<nome>` após conferir o caminho canônico.
- O registro só é atualizado depois da remoção bem-sucedida.

## Suposições

- Pacotes do RootFS base (curl, git, etc.) não estão no registro, logo não são removíveis pelo gerenciador até serem instalados por ele; decisão conservadora.
- Dependências órfãs não são removidas automaticamente.
- Nenhum teste executado ou criado; nada compilado.
