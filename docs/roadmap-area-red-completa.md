# Área RED — Plano mestre da aba completa

**Status:** planejamento; implementação ainda não iniciada.  
**Branch:** plan/area-red-wifi-publico  
**Repositório:** Kyra2214/KhaLinux  
**Documento complementar:** docs/roadmap-area-red-estagio-01-wifi-publico.md

---

## 1. Visão da Área RED

A aba RED será o centro de operações práticas ofensivas do KhaLinux/Brain Lab Security. Ela organiza aulas em operações reproduzíveis, com preparação do laboratório, execução guiada, evidências e relatório.

A Área RED ensina o lado ofensivo por meio de simulações controladas em dispositivos, contas e redes de laboratório autorizados. A Área BLUE será uma área separada, responsável por investigação, detecção, mitigação e defesa. Não inserir aulas de defesa dentro dos roteiros RED.

**Princípio:** cada operação precisa ser funcional dentro do escopo de laboratório definido. Não basta apresentar texto, imagens ou botões sem comportamento.

## 2. Estrutura visual e navegação da aba RED

### 2.1 Tela principal RED

A tela principal deve conter:

- Cabeçalho e identidade visual RED.
- Texto curto explicando o propósito da área.
- Indicador de estado do laboratório (inativo, preparando, em execução).
- Catálogo de operações disponíveis.
- Filtros por categoria e dificuldade.
- Progresso do aluno por operação.
- Acesso a operações em andamento e histórico.
- Aviso permanente de uso somente em ambiente próprio/autorizado.

### 2.2 Cartão de operação

Cada operação apresenta:

- Número e título.
- Categoria (por exemplo, redes, web, engenharia social simulada, análise local).
- Nível de dificuldade.
- Resumo do cenário.
- Pré-requisitos e recursos necessários.
- Estado: disponível, em andamento ou concluída.
- Botão para abrir a ficha da operação.

### 2.3 Ficha da operação

Cada operação terá:

1. Objetivo de aprendizagem.
2. Cenário e limites do laboratório.
3. Pré-requisitos e materiais.
4. Modo de preparação.
5. Execução guiada por etapas.
6. Painel de eventos/evidências.
7. Tutorial escrito completo.
8. Critérios de conclusão.
9. Relatório da sessão.
10. Link para a aula BLUE correspondente, quando ela existir, sem misturar os conteúdos.

### 2.4 Navegação interna

- RED → catálogo → ficha → preparação → execução → resultado.
- A operação pode ser pausada e retomada quando tecnicamente seguro.
- Ação explícita para encerrar a sessão.
- Histórico local de sessões.
- Retorno ao catálogo sem perder progresso concluído.
- Estados e mensagens de erro claros; nunca mostrar sucesso se uma etapa não ocorreu.

## 3. Catálogo inicial de operações

### Operação 01 — Nunca Conecte à Wi-Fi Pública

**Estado:** planejada em documento próprio.  
**Categoria:** redes / ponto de acesso / portal cativo simulado.  
**Dispositivos:** dois aparelhos Android em laboratório autorizado.

Resumo: o aparelho operador prepara um hotspot de laboratório e hospeda um portal local. O segundo aparelho conecta-se e percorre cenários fictícios de portal público, autenticação sintética e engenharia social. O operador observa eventos sintéticos e gera um relatório.

O escopo funcional e as seis fases estão descritos em docs/roadmap-area-red-estagio-01-wifi-publico.md.

### Operações futuras

A aba deve suportar novas operações por catálogo, sem exigir reescrita da navegação. Não cadastrar operações fictícias como se já estivessem definidas. Novas operações só entram no catálogo quando tiverem objetivo, escopo, requisitos, roteiro, implementação e critérios de conclusão especificados.

## 4. Componentes compartilhados da Área RED

### 4.1 Catálogo e metadados

Criar um modelo de operação com, no mínimo:

- id estável;
- título e resumo;
- categoria e dificuldade;
- objetivos;
- requisitos e pré-requisitos;
- tipo de ambiente (local, dois dispositivos, RootFS);
- permissões/capacidades Android necessárias;
- etapas do tutorial;
- estado de disponibilidade;
- referência para documentação;
- critérios de conclusão;
- referência opcional à operação BLUE relacionada.

Validar ids únicos, referências existentes e valores obrigatórios. Conteúdo deve ser versionado em assets/documentação e não embutido em lógica de UI.

### 4.2 Motor de execução

- Máquina de estados comum para operações.
- Eventos tipados e ordenados por horário.
- Identificador aleatório de sessão.
- Cancelamento e encerramento explícitos.
- Recuperação segura após fechamento do app, quando aplicável.
- Limites de tempo e tamanho para filas/logs.
- Separação entre UI, domínio, adaptadores Android e armazenamento.
- Cada operação declara seus recursos e capacidades; o motor não concede permissões automaticamente.

### 4.3 Persistência e histórico

- Persistir progresso, estado final, timestamps e relatório.
- Guardar somente dados mínimos necessários.
- Não persistir senhas, tokens, cookies, conteúdo privado ou dados pessoais desnecessários.
- Permitir apagar histórico e dados da operação.
- Aplicar migrações versionadas se o esquema Room existente precisar mudar.
- Não misturar histórico de comandos do terminal com eventos da Área RED.

### 4.4 Tutorial e conteúdo

- Tutorial disponível antes e durante a execução.
- Etapas numeradas com instruções objetivas.
- Pré-requisitos e limitações por versão Android.
- Solução de problemas.
- Marcação de etapa concluída baseada em evento real.
- Conteúdo RED descreve preparação e execução da simulação; conteúdo defensivo permanece na Área BLUE.

### 4.5 Relatórios e evidências

- Relatório local por sessão.
- Resumo de etapas executadas, eventos e resultado.
- Separar fatos observados de mensagens explicativas.
- Exportação manual pelo usuário, sem envio automático.
- Dados sintéticos e técnicos mínimos; nenhuma credencial real.
- Limpeza de arquivos temporários ao encerrar.

## 5. Segurança e limites obrigatórios

A Área RED deve funcionar em laboratórios próprios, com autorização explícita dos participantes e limites técnicos definidos por operação.

- Não desenvolver captura de senhas reais, tokens, cookies ou sessões de terceiros.
- Não criar páginas que se passem por serviços reais para coletar credenciais.
- Não integrar login real de Google, Facebook ou Instagram em páginas de simulação.
- Credenciais como xxx@gmail.com e 123 são apenas exemplos sintéticos; não são contas para autenticação externa. Preferir armazenar somente o evento “formulário de demonstração concluído”, sem persistir a senha digitada.
- Não interceptar tráfego privado de terceiros nem contornar criptografia.
- Não acessar câmera, localização, arquivos ou outros recursos de outro aparelho pela simples conexão Wi-Fi.
- Permissões reais só podem ser solicitadas no próprio aparelho, para uma função legítima, com explicação e consentimento explícito.
- O hotspot e o portal devem ser limitados à sessão de laboratório; não fazer varredura ou exploração automática de dispositivos conectados.
- Toda tela de armadilha deve exibir identificação inequívoca de simulação.
- O aplicativo não pode prometer isolamento forte: PRoot não é uma VM e não deve ser usado como fronteira de segurança.

## 6. Arquitetura de integração com o KhaLinux existente

A Área RED será integrada como módulo Android independente das telas atuais de Terminal, Ferramentas e Laboratórios.

Diretrizes:

- Reutilizar padrões existentes de Kotlin, Jetpack Compose, ViewModel/StateFlow, casos de uso e persistência.
- Não executar lógica de rede diretamente em composables.
- Não colocar lógica de operação no MainActivity.
- Criar contratos de domínio para catálogo, sessão, eventos e relatório.
- Isolar APIs Android específicas atrás de adaptadores.
- Manter o runtime Linux atual independente; não exigir RootFS para a Operação 01 se ela puder funcionar nativamente no Android.
- Se uma operação futura usar ferramentas Linux, integrá-la por uma interface explícita ao runtime existente, com escopo e autorização.
- Não alterar o comportamento do terminal, toolkit ou laboratórios existentes.
- Preservar compatibilidade e migrações da base Room.

A localização exata do novo item na navegação e os nomes de pacotes devem ser definidos após leitura do código atual da UI e dos módulos, antes de qualquer edição.

## 7. Roadmap mestre de implementação da aba RED

### RED-0 — Descoberta e desenho de integração
- Ler a navegação atual, telas, ViewModels, módulos, DI e persistência.
- Mapear pontos de extensão sem refatoração desnecessária.
- Definir ADR para arquitetura da Área RED.
- Definir modelo de dados e catálogo versionado.
- Não alterar módulos existentes sem necessidade demonstrada.

### RED-1 — Fundação da aba RED
- Criar entrada RED na navegação principal.
- Criar tela principal, cabeçalho, estado e catálogo.
- Implementar modelos, parser e validação do catálogo.
- Criar estados de operação e navegação para ficha.
- Exibir operação planejada como indisponível até sua implementação completa.

### RED-2 — Ficha, tutorial e progresso
- Criar tela de detalhes da operação.
- Exibir objetivos, pré-requisitos, escopo, recursos e critérios de conclusão.
- Criar visualizador de tutorial por etapas.
- Persistir progresso sem registrar dados sensíveis.

### RED-3 — Motor comum de sessões
- Implementar máquina de estados.
- Implementar eventos tipados e ordenados.
- Implementar cancelamento, encerramento, histórico e recuperação segura.
- Criar relatório local por sessão.
- Aplicar retenção mínima e limpeza.

### RED-4 — Operação 01: Wi-Fi público
- Seguir integralmente o documento específico da Operação 01.
- Integrar hotspot conforme APIs e limitações Android.
- Hospedar portal local de treinamento.
- Implementar armadilhas fictícias e interativas.
- Conectar painel RED, tutorial, eventos e relatório.

### RED-5 — Catálogo extensível e histórico
- Implementar filtros e estados.
- Implementar histórico de sessões.
- Implementar retomada quando suportada.
- Implementar apagar histórico e exportar relatório manualmente.
- Preparar contratos para novas operações, sem inventar conteúdo.

### RED-6 — Integração, documentação e fechamento de implementação
- Integrar a aba ao app sem regressões deliberadas.
- Atualizar documentação de arquitetura, guia do usuário, changelog e roadmap.
- Registrar limitações e pendências reais.
- Gerar checkpoint completo conforme processo do projeto.
- Deixar a validação acumulada para a fase final.

## 8. Critérios de conclusão da aba RED

A aba RED estará implementada quando:

- A entrada RED estiver integrada à navegação principal.
- O catálogo carregar dados reais versionados.
- Filtros, fichas e tutoriais funcionarem.
- O motor de sessões registrar estados e eventos reais.
- Histórico, progresso, encerramento e relatórios funcionarem.
- A Operação 01 estiver implementada de ponta a ponta conforme documento específico.
- O app explicar limitações Android e não fingir capacidades indisponíveis.
- Os dados persistidos forem mínimos e não incluírem credenciais sensíveis.
- Documentação e checkpoint refletirem o estado real.
- Nenhuma aula BLUE estiver indevidamente inserida na Área RED.

## 9. Fora do escopo do plano mestre

- Implementação da Área BLUE.
- Servidor remoto ou painel web central.
- Contas de usuário na nuvem.
- Operações futuras ainda não especificadas.
- Captura de credenciais reais ou exploração de dispositivos/redes de terceiros.
- Testes físicos durante a implementação.

## 10. Processo de execução

- Este plano mestre e o documento da Operação 01 são a referência de escopo.
- A implementação só começa após autorização explícita do usuário para iniciar o roadmap.
- Antes de codificar, o agente deve ler o estado atual do repositório e mapear a integração.
- Implementar os submódulos em sequência, sem parar após cada fase para pedir autorização repetida.
- Não executar testes, builds, validações ou testes físicos durante a implementação do roadmap. Registrar os requisitos no backlog de validação final.
- Testes e validação acumulada serão realizados somente depois que toda a implementação RED estiver concluída.
- Não fazer merge para main sem autorização explícita.
