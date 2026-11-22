# Atualização de pacotes (submódulo 4.6)

`PackageUpdater` em `android/runtime/.../packages/PackageUpdater.kt`.

- `available()`: pacotes registrados cuja versão no catálogo é maior (comparação estilo dpkg, `PackageVersion`).
- `update(names?)`: atualiza os nomes dados (ou todos os disponíveis). Recusa pacote não instalado ou já atualizado. Usa `PackageInstaller.install(..., forceReinstall)`: o alvo é tratado como não instalado no plano, dependências com versão mínima não atendida entram automaticamente.
- Mesmas garantias da instalação: SHA-256 e tamanho, extração protegida, troca atômica com versão anterior preservada até o sucesso, registro só após conclusão.

## Suposições

- Não há rebaixamento; o catálogo carrega uma versão por pacote.
- Atualização de pacote APT depende de a versão exata existir no repositório configurado no RootFS (não validado em aparelho).
- Nenhum teste executado ou criado; nada compilado.
