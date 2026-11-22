# ADR-0001: Separação entre Core Linux e aplicativo Android

- **Status:** aceito para a Fase 0
- **Data:** 2026-11-22

## Contexto

O projeto precisa entregar um Linux real no Android sem root, sem misturar responsabilidades de construção do RootFS, runtime e apresentação visual.

## Decisão

Separar o projeto em dois componentes: `distribution/` para o KhaLinux Core e `android/` para o KhaLinux Android. O runtime Android será acessado por uma abstração `LinuxRuntime`; a UI usará casos de uso e interfaces de domínio, nunca comandos diretamente nas telas. PRoot será o primeiro mecanismo de execução.

## Consequências

A separação facilita testes, evolução do runtime e documentação de limitações. Também exige contratos explícitos entre o app e o ambiente Linux, além de validação de compatibilidade em ARM64. PRoot não será tratado como isolamento forte.

## Ainda não decidido

Biblioteca PTY, pipeline final de build/distribuição do RootFS, catálogo de repositórios e matriz de aparelhos serão definidos em ADRs próprios antes da implementação correspondente.
