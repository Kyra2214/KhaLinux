# RED-01 — Operação Wi-Fi de laboratório

**Revisão:** 2026-11-23  
**Classificação atual:** protótipo existente; precisa evoluir para operação de rede real.  
**Roadmap mestre:** [roadmap-area-red-completa.md](roadmap-area-red-completa.md)  
**Prioridade:** LAB-5, depois de concluir motor de execução, inventário e adaptadores de dispositivo/rede.

## 1. Objetivo operacional

Transformar o cenário Wi-Fi atual em uma operação técnica reproduzível. O laboratório deve criar ou utilizar uma rede de teste, conectar o aparelho participante, observar o comportamento real da conexão e executar análises RED. O BLUE deve receber telemetria da mesma sessão e realizar detecção/resposta. O PURPLE deve correlacionar a execução com os resultados defensivos.

O objetivo não é apresentar uma aula guiada de portal. O portal é apenas um componente de um cenário de rede que também envolve descoberta, DNS, HTTP, TLS, serviços, conexões e telemetria.

## 2. Topologias suportadas

### Topologia A — rede local criada pelo Android
- Aparelho A executa KhaLinux e solicita o hotspot por API Android compatível.
- Aparelho B conecta-se à rede criada.
- O KhaLinux identifica os parâmetros e interfaces que o Android realmente expõe.
- O servidor de laboratório inicia na interface e porta selecionadas.
- A operação registra requisições e conexões destinadas aos serviços do laboratório.

### Topologia B — ponto de acesso/roteador externo
- Aparelho A executa o KhaLinux como estação operadora.
- Aparelho B é o dispositivo de teste.
- Um AP/roteador dedicado fornece a rede e, quando necessário, acesso à internet.
- O KhaLinux executa descoberta e análise a partir do ponto de observação disponível.
- Quando a topologia exigir visibilidade de tráfego que o Android não fornece, usar um ponto de captura compatível, como gateway instrumentado ou espelhamento de porta.

A interface deve identificar a topologia em uso. LocalOnlyHotspot não deve ser apresentado como uma rede com internet nem como captura geral do tráfego do participante.

## 3. Operações RED

### RED-WIFI-01 — Preparação e inventário
- Detectar interfaces, endereços, rotas, DNS e gateway.
- Criar ou selecionar a topologia do laboratório.
- Registrar SSID, banda, canal e parâmetros quando disponíveis.
- Identificar os dispositivos presentes por métodos de descoberta efetivamente executados.
- Exibir o método e a origem de cada identificação.

### RED-WIFI-02 — Descoberta e enumeração
- Descobrir hosts ativos.
- Enumerar portas e serviços.
- Identificar versões e banners disponíveis.
- Consultar DNS e resolução de nomes.
- Produzir resultados exportáveis e evidências de comando.

### RED-WIFI-03 — Portal e DNS
- Hospedar portal local com páginas de teste configuráveis.
- Implementar DNS de laboratório quando a topologia permitir.
- Testar resolução, redirecionamento e comportamento de abertura do portal.
- Registrar requisições HTTP e respostas reais.
- Exibir URLs, métodos, rotas, códigos, horários e origem observável.
- Permitir cenários com acesso direto, aceite de termos e autenticação local de laboratório.

### RED-WIFI-04 — Análise HTTP/TLS
- Inspecionar cabeçalhos e metadados HTTP efetivamente recebidos.
- Consultar certificados e parâmetros TLS dos serviços selecionados.
- Comparar HTTP e HTTPS com resultados reais.
- Registrar falhas de certificado, redirecionamentos e destinos.
- Não afirmar que o conteúdo de uma sessão TLS foi observado quando não houve ponto de inspeção apropriado.

### RED-WIFI-05 — Análise de tráfego
- Integrar ferramentas de captura e leitura de PCAP compatíveis com o ambiente.
- Identificar protocolos, fluxos, endpoints, DNS e metadados.
- Suportar importação de PCAP para análise offline.
- Registrar origem da captura e limitações da interface utilizada.
- Não confundir tráfego destinado ao portal com todo o tráfego do aparelho B.

### RED-WIFI-06 — BLUE e resposta
- Receber eventos de conexão, DNS, HTTP, TLS e descoberta produzidos pelos adaptadores.
- Correlacionar eventos com o ativo e a sessão.
- Aplicar regras de detecção com evidências e origem.
- Executar ações de resposta que o adaptador de rede realmente suporte.
- Registrar antes/depois da resposta.

### RED-WIFI-07 — PURPLE
- Correlacionar operações RED com detecções BLUE.
- Medir eventos detectados, não detectados e sem telemetria.
- Repetir o cenário após alteração defensiva.
- Comparar resultados e gerar relatório.

## 4. Painéis operacionais

**RED**
- Topologia e interfaces.
- Ativos detectados.
- Ferramentas e operações disponíveis.
- Processos em execução, saída e código de retorno.
- Resultados de descoberta, serviços, DNS, HTTP, TLS e PCAP.
- Evidências originais e exportação.

**BLUE**
- Eventos recebidos por fonte.
- Linha do tempo por ativo.
- Regras acionadas e evidências que as sustentam.
- Investigação e resposta aplicada.
- Estado anterior e posterior à resposta.

**PURPLE**
- Operação RED correspondente.
- Eventos BLUE correlacionados.
- Cobertura de detecção.
- Resultado antes/depois.
- Relatório comparativo.

## 5. Requisitos técnicos

- Operações devem usar o motor comum de execução LAB-1.
- Ferramentas devem vir do catálogo APT/Toolkit e ter arquitetura e versão verificadas.
- Adaptadores Android devem informar capacidades e limitações detectadas.
- Cada resultado deve conter ferramenta/API, parâmetros, horário, status e evidência.
- A sessão deve permitir cancelar e encerrar processos.
- A captura deve identificar a interface e o ponto de observação.
- A operação deve funcionar sem internet quando o cenário não exigir serviços externos.

## 6. Critérios de aceite

- Criar ou selecionar uma topologia e identificar suas interfaces.
- Conectar o aparelho B e confirmar a atividade por evidência real.
- Descobrir ativos e serviços com ferramentas reais.
- Executar portal/DNS e receber requisições reais.
- Analisar HTTP/TLS e PCAP conforme o ponto de captura disponível.
- Disponibilizar os eventos ao BLUE com origem rastreável.
- Aplicar pelo menos uma resposta técnica real e verificar seu efeito.
- Correlacionar operação e resposta no PURPLE.
- Exportar relatório reproduzível.

## 7. Estado do protótipo atual

O código atual já possui criação de LocalOnlyHotspot, servidor HTTP local, portal, rotas, formulário de demonstração, linha do tempo e compartilhamento de alguns eventos com BLUE. Esses componentes são reaproveitáveis, mas não satisfazem sozinhos os critérios operacionais deste documento.

O fluxo atual não equivale a inventário de rede, descoberta de hosts/serviços, captura geral do tráfego do aparelho, inspeção de sessões TLS, detecção BLUE baseada em telemetria de rede, resposta de rede ou correlação PURPLE. Esses itens permanecem no roadmap e não devem ser marcados como concluídos por causa da existência do portal ou do painel atual.
