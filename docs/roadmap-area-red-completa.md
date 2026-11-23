# KhaLinux — Roadmap mestre do laboratório operacional

**Revisão:** 2026-11-23  
**Branch desta revisão:** `plan/laboratorio-operacional`  
**Base:** `main`, após o merge do PR #18 (`b14812e934d10fa81af890be9aea461badc4ce69`).  
**Direção oficial:** o laboratório operacional RED/BLUE/PURPLE é o produto principal. O Linux ARM64, o runtime Android, o terminal, o gerenciador de pacotes e o toolkit são a infraestrutura que permite executar o laboratório.

> Esta revisão substitui a direção anterior que tratava a Área RED como uma sequência de demonstrações didáticas. A implementação atual será preservada como ponto de partida, mas telas, formulários fictícios, eventos artificiais e exercícios guiados não contam como operações reais.

---

## 1. Visão do produto

O KhaLinux será um ambiente Linux de cibersegurança executado localmente no Android, sem root, com ferramentas reais e um laboratório integrado.

- **RED:** prepara e executa operações técnicas reais, usando ferramentas do Linux, APIs Android e adaptadores de dispositivo/rede.
- **BLUE:** observa os mesmos ativos e operações, coleta telemetria disponível, identifica comportamentos, investiga eventos e executa respostas técnicas.
- **PURPLE:** correlaciona a execução RED com a telemetria BLUE, mede o que foi detectado e permite repetir a operação para comparar resultados.

O aplicativo não deve ser um curso com botões cenográficos. Uma ação apresentada como operação precisa chamar uma ferramenta, API ou processo real, devolver seu resultado verdadeiro e registrar evidências verificáveis. Conteúdo didático pode existir como documentação auxiliar, nunca como substituto da operação.

## 2. Arquitetura de produto

```text
KhaLinux Android
    |
    +-- KhaLinux Core
    |     Debian ARM64 / PRoot / shell / APT / filesystem
    |
    +-- Lab Engine
    |     sessões / ativos / escopo / execução / processos / resultados
    |
    +-- Adaptadores
    |     Android / ADB / Wi-Fi / rede / aplicações / serviços web
    |
    +-- RED
    |     operações reais e ferramentas
    |
    +-- BLUE
    |     coleta / eventos / regras / investigação / resposta
    |
    +-- PURPLE
          correlação / replay / cobertura / relatório
```

### Contrato de execução

Toda operação deve possuir:
1. identificador e versão;
2. alvo ou conjunto de alvos selecionados no inventário;
3. pré-requisitos técnicos detectados antes da execução;
4. ferramenta, API ou comando real utilizado;
5. estado de execução: aguardando, executando, concluído, falhou ou cancelado;
6. saída original e código de retorno, quando aplicável;
7. evidências produzidas e origem de cada evidência;
8. eventos disponibilizados ao BLUE;
9. encerramento e limpeza dos processos iniciados.

A interface não gera sucesso por conta própria. Não há evento de conclusão fictício, resultado inventado ou operação que seja apenas uma animação.

## 3. Reclassificação do que já existe

| Componente atual | Classificação correta |
|---|---|
| Debian ARM64, PRoot, shell e PTY | Infraestrutura Linux implementada; validação física ainda pendente |
| Serviço Android, sessões e terminal | Infraestrutura do aplicativo |
| APT/catálogo e Cyber Toolkit | Base para instalar e executar ferramentas; compatibilidade por ferramenta ainda precisa ser verificada no aparelho |
| Motor de exercícios da Fase 6 | Conteúdo complementar; não é o laboratório operacional |
| RED Wi-Fi atual | Protótipo de fluxo local/portal e painel; não representa ainda uma suíte operacional de avaliação de dispositivos e redes |
| BLUE atual | Primeira visualização de evidências compartilhadas; precisa evoluir para coleta, detecção e resposta reais |
| PURPLE | Integração operacional ainda a construir |
| Guias, aulas e tutoriais | Documentação de apoio, não critério de conclusão técnica |

**Importante:** o marco RED-0 a RED-12 anterior registra a implementação do protótipo descrito naquele roadmap. Ele não significa que o laboratório operacional completo esteja pronto. A validação física com dois aparelhos continua pendente.

## 4. Ordem de desenvolvimento

### LAB-0 — Congelamento da direção e inventário técnico
- Auditar o estado atual do repositório e mapear o que pode ser reutilizado.
- Separar infraestrutura, conteúdo didático e operações reais.
- Criar catálogo de operações com contratos de entrada/saída.
- Definir o modelo comum de ativos, sessões, processos, eventos e evidências.
- Identificar ferramentas já presentes no RootFS e dependências ausentes.
- Remover do caminho operacional telas e eventos que simulem execução.

**Estado:** CONCLUÍDO em código e documentação. Inventário, matriz de reutilização, contrato de domínio e catálogo operacional versionado implementados. Ver [`lab-0-inventory.md`](lab-0-inventory.md).

**Submódulos LAB-0**
- [x] LAB-0.1 — auditoria da árvore, módulos e pontos de entrada.
- [x] LAB-0.2 — classificação de infraestrutura, protótipos e conteúdo complementar.
- [x] LAB-0.3 — matriz de reutilização, dependências e lacunas.
- [x] LAB-0.4 — modelos comuns e contrato de operação.
- [x] LAB-0.5 — catálogo operacional versionado e parser estrito.
- [x] LAB-0.6 — inventário de ferramentas e capacidades declaradas.
- [x] LAB-0.7 — transferência de validações para LAB-10.

**Aceite:** atendido em documentação e código de contrato/catálogo. A execução das quatro operações iniciais será implementada no LAB-1; ainda não é declarada pronta.

### LAB-1 — Motor de execução do laboratório
- Criar serviço central de operações com fila, cancelamento, timeout e estado.
- Executar processos reais no RootFS por meio do runtime/PTY existente.
- Capturar stdout, stderr, código de saída, duração e sinais.
- Persistir metadados de execução e referências às evidências.
- Expor operações ao RED e eventos ao BLUE por contratos comuns.
- Permitir execução pelo terminal e pela interface, usando o mesmo backend.
- Controlar concorrência e encerrar processos filhos ao fechar a sessão.

**Estado:** PARCIALMENTE IMPLEMENTADO. O núcleo de execução, persistência, evidências, cancelamento, timeout, CLI do terminal e ponte dos ViewModels RED/BLUE estão no código. Ver [`lab-1-engine.md`](lab-1-engine.md).

**Submódulos LAB-1**
- [x] LAB-1.1 — motor central com fila limitada, concorrência configurável e ciclo de vida.
- [x] LAB-1.2 — execução RootFS, pré-verificação do executável, stdout/stderr, exit code e duração.
- [x] LAB-1.3 — persistência de sessões/execuções/eventos e evidências com SHA-256.
- [x] LAB-1.4 — integração no Service Android e CLI `khalab` no terminal.
- [x] LAB-1.5 — contratos compartilhados e conexão dos ViewModels RED/BLUE.
- [ ] LAB-1.6 — código do sinal de término do processo; pendente de extensão do backend nativo porque `Process.waitFor()` não expõe o sinal.
- [ ] LAB-1.7 — pré-condições completas de pacote/capacidade; por enquanto há verificação real de disponibilidade do executável, não inventário de todas as dependências transitivas.

**Aceite:** execução real, estado, cancelamento, timeout, saída e evidências estão integrados. Os itens 1.6 e 1.7 permanecem registrados e não são declarados concluídos. As telas finais de operação e acompanhamento serão consolidadas no LAB-9.

### LAB-2 — Inventário e conexão de dispositivos

<badge color="success">Implementação concluída</badge> **Validação final pendente.** Detalhes: [`lab-2-device-connectivity.md`](lab-2-device-connectivity.md).

- [x] LAB-2.1 — Registro persistente de ativos e inventário local Android: fabricante/modelo, versão, SDK, ABI, interfaces, endereços, gateway, DNS e capacidades disponíveis. Não coleta IMEI, Android ID, MAC ou SSID.
- [x] LAB-2.2 — Descoberta mDNS dos serviços `_adb-tls-pairing._tcp` e `_adb-tls-connect._tcp`, com alternativa de endpoint manual para redes que bloqueiam multicast.
- [x] LAB-2.3 — ADB ARM64 34.0.5, com suporte a `adb pair`, incluído no RootFS novo e no catálogo. O instalador de pacotes provisiona a suite Debian backports fixada também em RootFS existente.
- [x] LAB-2.4 — Servidor ADB persistente em sessão PRoot dedicada (porta 5038); chave do host permanece no home persistente. Ciclo de pareamento, conexão e desconexão sem transporte USB.
- [x] LAB-2.5 — Operações reais ADB para listar dispositivos, consultar propriedades, interfaces, rotas, pacotes de terceiros, logcat e estado da bateria.
- [x] LAB-2.6 — Ativos remotos identificados por hash estável do serial, estado de conexão atualizado e dispositivos ausentes marcados como `not-listed`.
- [x] LAB-2.7 — Operações ADB executadas pelo mesmo `LabOperationEngine`; stdout/stderr, exit code, duração e evidências chegam ao RED e os eventos persistidos chegam ao BLUE.
- [x] LAB-2.8 — Estado de disponibilidade, versão, suporte a pareamento e existência da chave do host expostos ao RED.

**Decisão sobre componente auxiliar Android:** as operações de inventário e conexão previstas nesta fase são cobertas por ADB e APIs públicas do operador. Não foi criado APK auxiliar sem requisito concreto; se um módulo posterior exigir uma API indisponível via ADB, essa dependência será implementada e registrada naquele módulo.

**Aceite de implementação:** descoberta, pareamento, conexão, inventário e consultas são operações reais com resultados do Android, sem eventos fixos de sucesso. A interface final fica no LAB-9. A validação do pacote ARM64/PRoot e os testes físicos com dois aparelhos ficam no LAB-10.

### LAB-3 — RED Mobile: avaliação operacional Android
- Inventariar pacotes instalados, versões, SDK, arquitetura e origem quando disponível.
- Consultar permissões concedidas, recusadas e configurações relevantes.
- Inspecionar manifests e componentes declarados de APKs fornecidos ao laboratório.
- Analisar componentes exportados, intents, deep links e superfícies de interação.
- Consultar logs do sistema e da aplicação disponíveis pelo canal conectado.
- Instalar, atualizar, iniciar, parar e remover APKs de teste.
- Executar verificações dinâmicas sobre aplicativos e serviços de teste.
- Verificar armazenamento, backups, configurações de rede e superfícies de exposição disponíveis.
- Integrar ferramentas reais do toolkit e registrar comandos, versões e resultados.
- Organizar cada avaliação em operações independentes, repetíveis e comparáveis.

**Aceite:** o RED produz inventário, achados e evidências originados do aparelho e das ferramentas, sem preencher resultados de exemplo.

### LAB-4 — RED Network: reconhecimento e análise de rede
- Inventariar interfaces, rotas, DNS, gateway e endereços.
- Descobrir hosts e serviços presentes na rede do laboratório.
- Executar descoberta e enumeração com Nmap e ferramentas compatíveis.
- Identificar serviços, versões e configurações expostas.
- Consultar DNS, HTTP, TLS e certificados dos alvos selecionados.
- Implementar captura/análise de tráfego por adaptador compatível com Android.
- Registrar fluxos, metadados, protocolos e limitações da fonte de captura.
- Integrar resultados de ferramentas ao inventário de ativos.

**Aceite:** descoberta e análise são executadas contra a rede selecionada e cada achado aponta para saída ou evidência técnica real. O app não afirma observar tráfego que a interface Android não disponibiliza.

### LAB-5 — RED Wi-Fi: hotspot, portal e comportamento de rede
- Evoluir a Operação Wi-Fi atual para um laboratório de rede real, não apenas páginas locais.
- Criar e encerrar a rede de laboratório por adaptador Android compatível.
- Identificar SSID, banda, endereço, gateway e parâmetros efetivamente disponíveis.
- Suportar topologia local-only e topologia com ponto de acesso/roteador externo de laboratório.
- Implementar portal cativo local, DNS de laboratório, rotas, redirecionamentos e páginas de teste.
- Observar requisições HTTP reais e metadados de conexão.
- Analisar comportamento de DNS, HTTP, HTTPS/TLS e certificados conforme o ponto de observação.
- Detectar conexões, redirecionamentos e alterações de configuração observáveis.
- Permitir comparar o comportamento do aparelho antes, durante e depois da conexão.
- Produzir uma linha do tempo correlacionada com o inventário de dispositivos.

**Aceite:** o cenário é reproduzível em rede de laboratório, com requisições e tráfego observados de verdade. O modo LocalOnlyHotspot é identificado como rede local sem internet; cenários que exigem internet usam infraestrutura de AP/roteador apropriada.

### LAB-6 — RED Web e aplicações
- Disponibilizar alvos web locais instaláveis no RootFS ou em containers/processos compatíveis.
- Criar perfis de teste para aplicações deliberadamente vulneráveis e aplicações do próprio laboratório.
- Integrar WhatWeb, Nmap, sqlmap, wfuzz e ferramentas adicionais após verificar arquitetura e execução.
- Implementar reconhecimento, enumeração, análise de configuração e testes de aplicação.
- Capturar requisições, respostas, parâmetros, erros e evidências relevantes.
- Separar claramente teste passivo, teste ativo e exploração controlada.
- Permitir repetir operações com os mesmos parâmetros para comparar resultados.

**Aceite:** cada ferramenta executa contra o alvo selecionado, gera saída verificável e seus achados podem ser revisados no BLUE/PURPLE.

### LAB-7 — BLUE: monitoramento, detecção e resposta
- Consumir eventos reais do motor de operações e dos adaptadores.
- Coletar logs Android/ADB, resultados de ferramentas, eventos de rede e dados dos serviços instrumentados.
- Construir linha do tempo por ativo, sessão e operação.
- Criar regras de detecção baseadas em eventos e indicadores observáveis.
- Exibir origem, horário, severidade, evidência e justificativa de cada detecção.
- Implementar ações de resposta disponíveis no ambiente: interromper processo do laboratório, encerrar sessão, bloquear fluxo pelo adaptador compatível, isolar serviço de teste e aplicar recomendações de configuração.
- Diferenciar detecção, hipótese, falso positivo e evento sem visibilidade.
- Permitir investigar um achado até a evidência original.

**Aceite:** o BLUE detecta apenas o que a telemetria sustenta e as respostas alteram de fato o estado do laboratório.

### LAB-8 — PURPLE: correlação e replay
- Associar cada execução RED a eventos BLUE pelo identificador de sessão/operação.
- Exibir linha do tempo lado a lado: ação, efeito, evento detectado e resposta.
- Medir cobertura de detecção por técnica e fonte de telemetria.
- Identificar operações não detectadas e fontes de dados ausentes.
- Reexecutar operações em condições equivalentes.
- Comparar resultados antes/depois de uma regra ou configuração defensiva.
- Gerar relatório técnico de cobertura e melhoria.

**Aceite:** o PURPLE demonstra com evidências quais operações foram observadas, detectadas, bloqueadas ou não cobertas.

### LAB-9 — Interface operacional e relatórios
- RED: console de operações, ativos, ferramentas, fila e evidências.
- BLUE: painel de eventos, investigação, detecções e resposta.
- PURPLE: correlação, replay e cobertura.
- Histórico filtrável por ativo, sessão, ferramenta, data e resultado.
- Exportação de relatórios com comandos, versões, parâmetros, resultados e referências às evidências.
- Documentação e tutoriais em seção separada, sem ocupar o fluxo operacional.
- Remover textos de advertência repetitivos; manter somente estados e informações técnicas pertinentes à ação.

**Aceite:** o operador consegue conduzir o laboratório pelas interfaces sem depender de telas didáticas ou de eventos simulados.

### LAB-10 — Validação integrada e release
- Executar testes unitários e de integração do motor e adaptadores.
- Compilar runtime, app e APK release.
- Validar instalação limpa, atualização, recuperação e persistência.
- Validar ADB Wireless Debugging com dois aparelhos Android.
- Validar RED Mobile, RED Network e RED Wi-Fi em condições reais.
- Validar coleta, detecção e resposta BLUE.
- Validar correlação e replay PURPLE.
- Verificar desempenho, memória, bateria e ciclo de vida.
- Testes físicos com os dois aparelhos ficam no fim desta fase.
- Publicar APK release assinado e notas de versão após aprovação.

**Aceite final:** operações reais de ponta a ponta, evidências rastreáveis, respostas verificadas e funcionamento estável no aparelho.

## 5. Sequência de prioridade

1. LAB-0: inventário e correção da direção.
2. LAB-1: motor de execução.
3. LAB-2: conexão real com o segundo Android.
4. LAB-3: RED Mobile, primeira capacidade operacional central.
5. LAB-4: RED Network.
6. LAB-5: RED Wi-Fi e portal.
7. LAB-6: RED Web.
8. LAB-7: BLUE operacional.
9. LAB-8: PURPLE.
10. LAB-9: interface e relatórios.
11. LAB-10: validação integrada, testes físicos e release.

O conteúdo de exercícios da antiga Fase 6 pode ser mantido como complemento, mas não bloqueia nem substitui as fases operacionais acima.

## 6. Regras de execução do roadmap

- Implementar uma fase/submódulo por vez, na ordem definida.
- Antes de alterar código, inspecionar arquivos, arquitetura, dependências e estado Git.
- Reutilizar o runtime, PTY, APT, catálogo e toolkit existentes sempre que tecnicamente adequado.
- Não criar telas antes de definir o contrato e a execução real da operação.
- Não criar dados de sucesso, resultados, eventos ou evidências artificiais.
- Não declarar uma operação concluída só porque o código ou a tela existe.
- Atualizar roadmap, changelog e retomada ao concluir cada submódulo.
- Preservar alterações existentes e não sobrescrever trabalho do usuário.
- Não gerar ZIP/checkpoints.
- Não executar testes/builds intermediários durante a implementação; acumular validações para LAB-10, conforme o processo já definido.
- Não fazer merge para `main` sem autorização explícita.

## 7. Estado inicial desta revisão

| Área | Estado nesta revisão |
|---|---|
| Infraestrutura Linux/Android | Implementada em grande parte; validação integrada e física pendente |
| Toolkit | Catálogo e integração-base existentes; execução por ferramenta precisa de verificação real |
| Exercícios didáticos | Existem como conteúdo complementar |
| RED operacional | Ainda não concluído; protótipos existentes serão auditados e reaproveitados |
| BLUE operacional | Ainda não concluído; integração de evidências atual é ponto de partida |
| PURPLE | Planejado |
| APK release | Configuração existe; build e assinatura release ainda não foram comprovados |
| Testes físicos | Pendentes, reservados ao fim de LAB-10 |

**Próximo passo obrigatório:** LAB-0, auditoria do estado atual e matriz de reutilização. Não iniciar telas novas nem ampliar conteúdo didático antes de fechar essa auditoria.
