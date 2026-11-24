# Inventário de baseline RED/BLUE

**Branch:** `recovery/red-portal-integration`  
**Commit-base da retomada:** `cda26062cb220720b2fd6bde4f8e5569be22bbd2`  
**Estado:** inventário documental e de código concluído para RED-00. Não representa validação funcional.

## Topologia confirmada

- **A:** operador único. Executa KhaLinux, cria hotspot, hospeda o portal e concentra RED/BLUE, eventos, relatórios e observabilidade.
- **B:** navegador participante. Conecta ao Wi-Fi, abre o portal, faz login de laboratório e responde ao aviso de cookies e às solicitações web legítimas. Não instala nem executa KhaLinux.

## Arquivos de código RED examinados

| Arquivo | Responsabilidade observada | Estado no baseline |
|---|---|---|
| `RedModels.kt` | Estados, sessões, eventos, observações HTTP e catálogo interno | Existe; enum de evento ampliado para consentimento de cookies |
| `RedCatalogJson.kt` | Parser estrito do catálogo embarcado | Existe |
| `RedHotspotController.kt` | Reserva `LocalOnlyHotspot`, callbacks e leitura de configuração | Existe; descoberta de interface ainda depende de nomes e não está validada |
| `RedPortalServer.kt` | Servidor HTTP local, rotas, formulário, redirecionamento e portal | Existe; endpoint `POST /consent` e banner de cookies adicionados |
| `RedLabInputValidator.kt` | Allowlist e validação de formulários | Existe; validação de consentimento por categoria adicionada |
| `RedViewModel.kt` | Coordenação de sessão, hotspot, portal, eventos e histórico | Existe; callback de consentimento ligado à linha do tempo |
| `RedScreen.kt` | Painel do operador, estado de rede, requisições e relatório | Existe; fluxo de modo participante removido e escolha de cookies exibida |
| `RedSessionStore.kt` | Persistência de metadados e eventos | Existe; eventos tipados são persistidos sem corpo HTTP |
| `RedLiveEvidenceStore.kt` | Evidências efêmeras para RED/BLUE | Existe |
| `AndroidManifest.xml` | Permissões e componentes Android | Permissões de câmera/microfone removidas do app operador |
| `android/app/build.gradle.kts` | Dependências e configuração Android | Dependências CameraX removidas |

O antigo `RedParticipantScreen.kt` foi removido porque o B não executará o aplicativo. O endpoint/token de eventos nativos do participante também foi removido.

## Documentação examinada e alinhada

- `README.md`
- `ROADMAP.md` (roadmap geral do KhaLinux)
- `CHANGELOG.md`
- `AGENTS.md`
- `docs/roadmap-red-blue-laboratorio.md`
- `docs/red-area.md`
- `docs/red-operation-01.md`
- `docs/red-instructor-guide.md`
- `docs/security-audit.md`
- `docs/final-validation.md`

## Pontos de implementação identificados

1. O hotspot usa API pública `LocalOnlyHotspot`; a identificação da interface AP ainda precisa ser tratada em RED-03/RED-04.
2. O servidor HTTP faz bind em IPv4 selecionado e limita tamanho de cabeçalho/corpo e conexões. A correlação da interface e o ciclo de vida continuam pendentes de implementação/validação.
3. O formulário de login aceita apenas `demo_id` e `demo_code`; não é autenticação de conta externa.
4. A submissão de formulário mantém valores de teste em memória na sessão ativa e não os inclui no histórico/relatório.
5. O aviso de cookies registra escolha e categorias em `POST /consent`; o A recebe um resumo sem valores dos cookies.
6. Os cabeçalhos Cookie e Authorization não fazem parte da allowlist de cabeçalhos observados.
7. O portal está em HTTP por IP local. APIs web sensíveis podem ficar indisponíveis por exigirem contexto seguro; RED-04/RED-07 precisam tratar essa limitação sem contorno.
8. A UI do operador ainda contém rótulos legados de recursos/permissões; revisar durante RED-01/RED-10.
9. O modelo e o relatório ainda contêm categorias antigas de eventos nativos por compatibilidade com histórico legado. Não significam que o B use o app.

## Atividades transferidas para a etapa final

Nenhum teste, build, compilação, lint, auditoria de validação ou teste físico foi executado durante esta implementação. As verificações exigidas pelo roadmap permanecem pendentes para RED-13/RED-14, conforme instrução de execução.

## Próximo submódulo

**RED-01 — Modelo de domínio e estados reais.** Consolidar estados, origem de evidências, eventos de cookies e estados de permissões web, removendo ambiguidades legadas sem alterar funcionalidades não relacionadas.
