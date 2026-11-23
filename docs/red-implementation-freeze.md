# RED — fechamento da implementação e pendências finais

**Branch:** plan/area-red-wifi-publico  
**Escopo:** laboratório local de dois aparelhos Android, RED + BLUE.  
**Estado:** implementação de RED-0 a RED-12 registrada como concluída em código/documentação; RED-13 (validação final) pendente.  
**Regra de entrega:** ZIP/checkpoints ignorados por instrução do usuário; todas as alterações permanecem nesta branch.

## Implementado

1. RED-0 — escopo, arquitetura e limites documentados.
2. RED-1 — modelos de estado/evento, catálogo e persistência compatível.
3. RED-2 — LocalOnlyHotspot, dados disponíveis do Android e estado de atividade do participante por requisição real.
4. RED-3 — servidor HTTP local porta 8080, bind IPv4 Wi-Fi/AP, limites e timeout.
5. RED-4 — portal fictício natural, login local e acesso direto.
6. RED-5 — POST de formulário com allowlist estrita de dados sintéticos; valores somente em memória.
7. RED-6 — páginas, links, navegação e redirect 302 local.
8. RED-7 — modo participante Android, permissões reais, prévia local de câmera, medidor de microfone, localização local e seletor SAF; relay de estado autenticado por token temporário.
9. RED-8 — painel de observabilidade, requisições, cabeçalhos filtrados, eventos e limpeza de dados temporários.
10. RED-9 — histórico limitado, recuperação de sessão e relatório sem valores sensíveis.
11. RED-10 — Área BLUE integrada às mesmas evidências e alternância sem interromper RED.
12. RED-11 — tutorial, guia de instrutor, roteiros e troubleshooting.
13. RED-12 — integração/documentação consolidada, ciclo de encerramento idempotente e congelamento da implementação.

## Arquivos principais

- RED: android/app/src/main/kotlin/com/khalinux/android/red/
- BLUE: android/app/src/main/kotlin/com/khalinux/android/blue/
- Catálogo: distribution/red/operations.json
- Documentos: docs/roadmap-area-red-completa.md, docs/roadmap-area-red-estagio-01-wifi-publico.md, docs/red-area.md, docs/red-operation-01.md, docs/blue-area.md, docs/red-instructor-guide.md, docs/red-implementation-freeze.md.
- Integração: MainActivity.kt, KhaLinuxTerminalScreen.kt, HomeScreen.kt, AndroidManifest.xml, android/app/build.gradle.kts.

## Limitações técnicas assumidas/documentadas

- Android controla parâmetros do LocalOnlyHotspot; disponibilidade de SSID, senha, banda e interface depende da versão/fabricante.
- Não há API universal para listar clientes do LocalOnlyHotspot. A presença do participante é confirmada quando chega uma requisição HTTP ao servidor.
- O portal é HTTP sem TLS na porta 8080; não é HTTPS.
- Câmera, microfone e localização são demonstrados no modo participante Android no aparelho B, não concedidos pelo portal web.
- Câmera/microfone são prévias locais; áudio bruto, vídeo, coordenadas e arquivos não são enviados ao aparelho A.
- O formulário recebe apenas identificador/código de laboratório sintéticos com padrões restritos; valores são efêmeros e não persistidos.
- O servidor vincula socket a IPv4 de interface Wi-Fi/AP identificada; se não conseguir identificar, não faz fallback para todas as interfaces.
- A abertura automática do portal não é garantida; URL e QR Code são alternativas.

## Pendências da validação RED-13

- Compilação do módulo Android com CameraX e Compose.
- Testes unitários e integração dos modelos, parser, store, ViewModels e server.
- Testes do ciclo de vida LocalOnlyHotspot e bind na interface real.
- Testes HTTP porta 8080, limites, rotas, formulário sintético, redirect e headers.
- Testes do modo participante: câmera, microfone, localização, SAF e revogação.
- Teste de relay de eventos autenticado entre dois aparelhos.
- Teste de alternância RED ↔ BLUE mantendo a sessão ativa.
- Testes de histórico, relatório, exclusão e recuperação.
- Teste físico final com os dois Android e regressão das áreas existentes.
- Corrigir falhas e repetir testes afetados.

## Declaração de integridade do estado

Nenhum teste, build ou teste físico foi executado durante a implementação. Não declarar compatibilidade ou funcionamento validado até a conclusão de RED-13. O percentual de implementação (93%) e o estado de validação devem permanecer separados.
