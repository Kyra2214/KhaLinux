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

**Estado:** primeira reconstrução real no GitHub Actions concluída com sucesso. Debian Bookworm ARM64 passou pela verificação de hash canônico, verificação do arquivo e smoke test ARM64 via QEMU/chroot. O pacote, manifesto e lock de pacotes foram publicados em [rootfs-bookworm-20261122-v1](https://github.com/Kyra2214/KhaLinux/releases/tag/rootfs-bookworm-20261122-v1). A base está entregue; reprodutibilidade bit-a-bit entre duas reconstruções independentes ainda não foi comprovada.

- Preparar build ARM64.
- Construir Debian ARM64 minimal e gerar RootFS.
- Configurar Bash, coreutils, utilitários de rede, APT, usuários e diretórios.
- Criar scripts de inicialização e manifesto versionado.
- Gerar e validar SHA-256.
- Testar comandos básicos e integridade da imagem.

**Critério:** RootFS reproduzível, inicializável e identificado corretamente.

## Fase 2 — Runtime Android

**Estado:** launcher Kotlin, sessões, PTY JNI, entrada/resize/cancelamento e grupos de processos implementados. Testes host, JNI/PTy e APK Debug passaram no CI. O RootFS ARM64 agora está publicado. PRoot 5.1.107.102 foi compilado com Android NDK 27/API 35, validado como ELF AArch64/Bionic e publicado em [release v1](https://github.com/Kyra2214/KhaLinux/releases/tag/proot-android-5.1.107.102-v1). O instalador no app foi integrado ao Service/UI, valida manifesto e hashes fixados, extrai um único ELF ARM64/Bionic e passou nos testes unitários e no build APK Debug do CI. A execução no Android ainda não foi validada.

- Implementar launcher PRoot, PTY, sessões e gerenciamento de processos.
- Tratar stdout/stderr, timeout, cancelamento e erros.
- Validar Android 15+, ARM64, armazenamento privado e ciclo de vida.

**Critério:** executar Linux real no Android sem root, dentro das limitações documentadas.

## Fase 3 — Aplicativo Android

**Estado:** Activity/Service, Compose/MVVM, PTY JNI, instaladores RootFS/PRoot, binds mínimos, emulador VT/ANSI básico, histórico local Room e configurações persistentes do terminal implementados. O emulador cobre UTF-8 incremental, cursor, SGR, cores 16/256/RGB, erase e scrollback, sem alegar compatibilidade xterm completa. Histórico mantém até 100 comandos e expõe os 12 mais recentes; fonte e dimensões do terminal são persistidas com migração Room. CI executa validação host, smoke PRoot/QEMU, testes unitários do runtime e do repositório de histórico, além da compilação APK Debug. Ainda faltam reforçar ciclo de vida em segundo plano com foreground service/notificação, auditoria final e testes físicos finais.

- Concluir projeto Kotlin com Jetpack Compose, MVVM, Clean Architecture e Coroutines.
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
