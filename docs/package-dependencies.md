# Resolução de dependências (submódulo 4.3)

`DependencyResolver(catalog, arquitetura)` em `android/runtime/.../packages/DependencyResolver.kt`.

- `resolve(requested, installed)` devolve `ResolutionPlan(toInstall, alreadySatisfied)`; `toInstall` vem em ordem topológica (dependências primeiro).
- Erros (`DependencyResolutionException`, sem plano parcial): pacote ausente, ciclo (mostra o caminho), versão abaixo da mínima, arquitetura incompatível.
- Pacote instalado satisfaz a dependência se a versão instalada ≥ versão mínima.
- `dependents(name, installed)`: pacotes instalados que dependem do alvo (base da remoção segura em 4.5).
- `PackageVersion`: ordenação estilo dpkg (epoch, `~`, revisão); implementação própria simplificada, não valida contra `dpkg --compare-versions`.

## Suposições

- Uma versão por pacote no catálogo; a versão do catálogo é a candidata única.
- Dependências de bibliotecas do APT são resolvidas pelo próprio APT; o resolvedor cobre apenas dependências declaradas entre manifestos do catálogo.
- Nenhum teste executado ou criado; nada compilado.
