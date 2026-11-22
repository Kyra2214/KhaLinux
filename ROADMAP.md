# Roadmap do KhaLinux

Este roadmap é a referência oficial de evolução do projeto. Uma fase só pode ser considerada concluída quando seus critérios de aceite forem atendidos com implementação, testes executados, evidência e documentação.

## Fase 0 — Fundação

**Estado:** em preparação/concluída como scaffold documental.

- Repositório público e documentação inicial.
- Estruturas `distribution/`, `android/` e `docs/decisions/`.
- Convenções de contribuição, segurança e decisões técnicas.
- Templates de issues e pull requests.

**Critério:** repositório organizado e sem arquivos fictícios apresentados como funcionais.

## Fase 1 — Linux Base

**Estado:** build e smoke tests validados no sandbox x86_64 por emulação ARM64, usando snapshot Debian fixo e lock de pacotes; validação em aparelho Android permanece pendente.

- Preparar build ARM64.
- Construir Debian ARM64 minimal e gerar RootFS.
- Configurar Bash, coreutils, utilitários de rede, APT, usuários e diretórios.
- Criar scripts de inicialização e manifesto versionado.
- Gerar e validar SHA-256.
- Testar comandos básicos e integridade da imagem.

**Critério:** RootFS reproduzível, inicializável e identificado corretamente.

## Fase 2 — Runtime Android

**Estado:** núcleo Kotlin de launcher PRoot e sessões validado no sandbox com quatro testes; integração Android/PTY e aparelho real pendentes.

- Implementar launcher PRoot, PTY, sessões e gerenciamento de processos.
- Tratar stdout/stderr, timeout, cancelamento e erros.
- Validar Android 15+, ARM64, armazenamento privado e ciclo de vida.

**Critério:** executar Linux real no Android sem root, dentro das limitações documentadas.

## Fase 3 — Aplicativo Android

**Estado:** integração inicial Activity/Service e scaffold Gradle implementados; SDK Android, APK e dispositivo real ainda pendentes.

- Criar projeto Kotlin com Jetpack Compose, MVVM, Clean Architecture e Coroutines.
- Implementar navegação, dashboard, instalação, terminal, configurações, Room e histórico.

**Critério:** APK funcional integrado ao ambiente Linux.

## Fase 4 — Gerenciador de pacotes

- Catálogo, pesquisa, categorias, dependências, instalação, remoção, atualização e histórico.
- Manifestos com arquitetura, origem, versão, tamanho, verificação e limitações.

**Critério:** instalar e remover ferramentas de maneira confiável e auditável.

## Fase 5 — Cyber Toolkit

- Integrar ferramentas de reconhecimento, rede, Web, forense e auditoria.
- Validar individualmente ARM64 e limitações sem root.
- Criar testes automatizados e documentação por ferramenta.

**Critério:** ferramentas funcionais e verificadas; nenhuma compatibilidade presumida.

## Fase 6 — Laboratórios

- Motor de exercícios, laboratórios Linux/rede/Web/forense, desafios progressivos, pontuação e progresso persistido.
- Vulnerabilidades operando em localhost por padrão.

**Critério:** exercícios executáveis localmente e com escopo controlado.

## Fase 7 — Experiência avançada

- Múltiplas sessões, personalização, favoritos, scripts, gerenciamento de arquivos, relatórios e integração entre ferramentas.
- Assistente didático local, se viável e seguro.

## Fase 8 — Otimização

- Reduzir RootFS e downloads; otimizar memória, inicialização, bateria, processos e recuperação.

**Critério:** estabilidade em dispositivos compatíveis.

## Fase 9 — Auditoria e publicação

- Auditorias de segurança, testes unitários, integração, E2E, aparelho real, instalação limpa, atualização e recuperação.
- APK release, documentação de instalação e primeira versão estável.

## Regras transversais

Não implementar todas as fases de uma vez; não marcar testes sem execução real; não publicar binários gigantes no Git; manter commits pequenos; documentar limitações do Android sem root; preservar dados do usuário; e nunca executar avaliações contra terceiros sem autorização e escopo explícito.
