# Instalação de pacotes (submódulo 4.4)

`PackageInstaller` + `InstalledPackageStore` + `PackageCommandRunner` em `android/runtime/.../packages/`.

1. Resolve o plano (`DependencyResolver`) considerando os pacotes já registrados.
2. Por pacote, na ordem: `debian-apt` → `apt-get update` (uma vez) e `apt-get install -y --no-install-recommends 'nome=versão'`; origem direta → download HTTPS retomável, tamanho e SHA-256, extração protegida (tar.gz) em `/opt/khalinux/<nome>`.
3. Só depois do sucesso o pacote é gravado no registro (arquivo TAB, gravação atômica). Falha interrompe o plano; pacotes anteriores já concluídos permanecem registrados.

## Suposições

- Artefatos diretos são `tar.gz`; o conteúdo vai para `/opt/khalinux/<nome>` (sem integração automática ao `PATH` ainda).
- `apt-get` precisa de rede e do repositório configurado no RootFS; o `sources` atual aponta para o snapshot fixo, e a versão exata do catálogo deve existir nele. Não validado em aparelho.
- O adaptador `LinuxRuntime` → `PackageCommandRunner` (execução real no PRoot) será criado com a UI em 4.7.
- Pacotes APT já presentes no RootFS base não são registrados automaticamente; o registro nasce vazio e a instalação pelo APT é idempotente.
- Nenhum teste executado ou criado; nada compilado.
