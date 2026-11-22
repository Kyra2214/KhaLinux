# Validação da Fase 5 — Cyber Toolkit

## O que foi executado (host, esta sessão)

- Suíte do runtime via `bash android/runtime/test.sh` (kotlinc 1.9.24, JUnit 4, JDK 21): **88 testes, todos aprovados**, incluindo:
  - `ToolkitServiceTest` (guarda de escopo, expiração, comandos fora da ferramenta/metacaracteres, verificação e registro);
  - `ToolkitDataTest` (os arquivos reais `catalog.json` e `tools.json` passam pelo `PackageCatalogParser`/`ToolkitRegistryParser`; docs existem; todas as ferramentas que interagem com terceiros exigem confirmação de escopo; nenhuma vem marcada como verificada).
- Primeira execução real da suíte da Fase 4: 5 falhas em `DependencyResolverTest`, causadas por nomes de pacote de uma letra nos dados de teste (o regex de nome APT exige 2+ caracteres, como a política do Debian). Dados de teste corrigidos; código de produção inalterado.

## O que NÃO foi validado

- Módulo `:android:app` (ViewModel, tela, serviço, adaptador org.json): não compilado aqui (sem Android SDK); depende do CI (`assembleDebug`, `:android:app:testDebugUnitTest`).
- Nenhuma ferramenta foi executada em ARM64/PRoot. Todas continuam `unverified`/`known-limited`. A tela **Ferramentas → Verificar** executa o `check_command` no RootFS do aparelho e registra o resultado (evidência de execução, não prova de funcionalidade completa).
- Versões do catálogo foram lidas em páginas do Debian; não foram conferidas contra o snapshot APT `20251122T000000Z` (esse snapshot não pôde ser consultado nesta sessão; `apt-get install nome=versão` falha se divergirem).
- Teste de ViewModel/Room e teste físico continuam pendentes.

## Como validar no aparelho

1. Instalar o ambiente; abrir **Pacotes** e instalar a ferramenta; abrir **Ferramentas → Verificar**.
2. Para ferramentas com escopo: **Confirmar escopo** (24 h) antes de qualquer uso contra alvo.
3. Registrar o resultado em `verification_evidence` de `distribution/toolkit/tools.json` somente com evidência real.
