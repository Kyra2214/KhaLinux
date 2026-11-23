# RED — fechamento da implementação e pendências finais

**Branch:** plan/area-red-wifi-publico  
**Escopo:** laboratório local de dois aparelhos Android, RED + BLUE.  
**Estado:** implementação RED-0 a RED-12 concluída em código/documentação (100%). RED-13 automatizada aprovada no HEAD `49648c9a7bed05fddce2053aae28ebab0aa9902c` pelo workflow `45138571275`; validação física com os dois aparelhos permanece pendente.  
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

## Validação automatizada do HEAD atual

GitHub Actions: https://github.com/Kyra2214/KhaLinux/actions/runs/45138571275  
Commit: `49648c9a7bed05fddce2053aae28ebab0aa9902c`  
Resultado: **success**, em 2026-11-23.

Etapas aprovadas:
- Testes unitários dos módulos runtime e app Android.
- Compilação do APK debug e upload do artefato de inspeção.
- Verificação de configuração RootFS e sintaxe shell.
- Auditoria estática de segurança.
- Teste JNI/PTy no host.
- Smoke test ARM64 RootFS com PRoot e binds de dispositivo.

Esse resultado cobre o HEAD indicado. Não equivale a teste físico em aparelhos Android.

## Pendências físicas da validação RED-13

- Testar criação/encerramento LocalOnlyHotspot em aparelho A.
- Conectar aparelho B e abrir o portal local na porta 8080.
- Exercitar login sintético e acesso direto; conferir requisições no painel.
- Testar redirecionamento e sequência de navegação.
- Testar câmera, microfone, localização e SAF no aparelho B, incluindo recusa/revogação.
- Confirmar relay autenticado de eventos entre aparelhos.
- Alternar RED ↔ BLUE durante sessão ativa.
- Verificar histórico, relatório, limpeza e recuperação.
- Regressão das áreas existentes e compatibilidade por fabricante/versão.
- Corrigir falhas físicas e repetir os testes afetados.

## Declaração de integridade do estado

Nenhum teste ou build foi executado durante a implementação. A implementação está 100% concluída (RED-0 a RED-12), mas a validação RED-13 ainda não foi executada para o HEAD atual devido à indisponibilidade de execução de workflows/checkout local nesta sessão. Testes físicos e compatibilidade permanecem para o fim. O projeto não deve ser declarado concluído até resolver essas pendências.
