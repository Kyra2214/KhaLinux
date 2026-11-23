# Área BLUE — monitoramento, detecção e resposta

**Direção revisada:** 2026-11-23  
**Roadmap operacional:** [roadmap-area-red-completa.md](roadmap-area-red-completa.md)  
**Estado:** o BLUE já recebe eventos operacionais persistidos e ao vivo do motor compartilhado, incluindo operações ADB do LAB-2. Regras de detecção, investigação e resposta continuam pendentes do LAB-7.

## Função

BLUE é a camada defensiva do laboratório. Ela recebe telemetria real dos adaptadores Android, rede, aplicações e motor de execução; correlaciona eventos por ativo e sessão; aplica regras de detecção; e executa respostas que os adaptadores suportam.

Não deve ser apenas uma tela com explicações sobre o que o usuário deveria fazer. Deve monitorar os eventos efetivamente disponíveis no laboratório e permitir investigação e resposta técnica.

## Fontes de telemetria

- Eventos do motor de operações: comando, ferramenta, processo, resultado, código de saída e evidências persistidas.
- ADB Wireless debugging: pareamento/conexão, estado do dispositivo, inventário, propriedades, interfaces e rotas.
- Android/ADB: inventário, logs e alterações de estado disponibilizadas pelo dispositivo.
- Rede: descoberta, DNS, conexões, HTTP/TLS e captura conforme a interface e o ponto de observação.
- Aplicações: eventos e logs produzidos por alvos instrumentados.
- Serviços locais: requisições, respostas, falhas e estado dos processos.

Cada evento mantém fonte, horário, ativo, sessão, operação e evidência de origem.

## Capacidades BLUE

1. Ingestão e normalização de eventos reais.
2. Linha do tempo por ativo, sessão e operação.
3. Regras de detecção configuráveis, com justificativa e evidências.
4. Agrupamento de eventos relacionados e contexto do ativo.
5. Investigação até a saída original da ferramenta ou fonte.
6. Resposta operacional: encerrar processos do laboratório, finalizar sessões, bloquear fluxos por adaptador compatível e aplicar ações suportadas pelo Android/serviço.
7. Registro do estado anterior e posterior à resposta.
8. Classificação de detecção, hipótese, falso positivo e ausência de telemetria.

## Integração RED

RED e BLUE compartilham o mesmo identificador de ativo, sessão e operação. Uma operação RED publica eventos à medida que acontecem. BLUE os recebe pela fonte correspondente, sem gerar detecções artificiais.

A existência de um evento no RED não significa automaticamente que o BLUE o detectou. A detecção precisa ser produzida por uma regra e apontar para evidência.

## Integração PURPLE

PURPLE correlacionará cada operação RED com os eventos e respostas BLUE, medindo cobertura, lacunas de telemetria e resultado antes/depois de ajustes defensivos.

## Próxima etapa

Após LAB-0 a LAB-6, implementar LAB-7: pipeline de telemetria, regras, investigação e respostas reais. O painel atual pode ser reaproveitado visualmente, mas seus dados e estados devem vir das fontes operacionais.
