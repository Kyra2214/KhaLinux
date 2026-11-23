# LAB-0 — Auditoria e inventário técnico

**Data:** 2026-11-23 · **Branch:** `plan/laboratorio-operacional` · **HEAD auditado:** `94b6cad77a71e45788caad51dcaac45042dbe38a`  
**Árvore:** 301 arquivos versionados, listagem recursiva completa.  
**Estado:** concluído em código/documentação; validação acumulada para LAB-10.

## Estrutura e classificação

| Área | Localização | Classificação |
|---|---|---|
| Aplicativo | `android/app` | Kotlin, Compose, ViewModels, Service, persistência e interfaces |
| Runtime | `android/runtime` | PRoot, PTY/JNI, sessões, pacotes, toolkit e utilitários |
| Distribuição | `distribution` | RootFS Debian ARM64, scripts, manifestos e catálogos |
| RED legado | `android/app/.../red`, `distribution/red` | Protótipo real de hotspot/portal e fluxo demonstrativo |
| BLUE legado | `android/app/.../blue` | Visualização de sessões/evidências RED, sem detecção/resposta |
| Exercícios | `android/runtime/.../labs`, `distribution/labs`, `docs/labs` | Conteúdo complementar, não motor operacional |
| CI/release | `.github/workflows`, Gradle | Validação/empacotamento, reservados à fase LAB-10 |

## Infraestrutura reutilizável

- `LinuxRuntime` e `CommandRequest` definem a fronteira app/processos Linux.
- `SessionManager` inicia processos PRoot e fornece stdout/stderr, código de saída, timeout e cancelamento.
- PTY/JNI suporta shell interativo, entrada e redimensionamento.
- `KhaLinuxRuntimeService` é a fronteira Binder entre Activity e runtime.
- `LinuxRuntimePackageCommandRunner` executa comandos no RootFS, coleta saída de forma síncrona e limita o buffer.
- `ToolkitService`, descritores e catálogo APT fornecem base para verificar/lançar ferramentas instaladas.

O LAB-1 deve orquestrar o runtime existente, sem criar outro launcher. A sessão de processo já existe; faltam fila por operação, persistência, evidência, normalização e integração RED/BLUE.

## Ferramentas catalogadas

- Rede/reconhecimento: `iproute2`, `iputils-ping`, `dnsutils`, `nmap`, `whois`, `traceroute`, `curl`, `openssh-client`.
- Web: `whatweb`, `sqlmap`, `wfuzz`.
- Forense: `sleuthkit`, `binwalk`, `hashdeep`, `exiftool`.
- Auditoria local: `lynis`, `chkrootkit`, `rkhunter`.

O catálogo APT descreve pacotes instaláveis. Não prova que estejam instalados nem que executem corretamente em ARM64/PRoot. A disponibilidade deve ser consultada no estado real do gerenciador.

## RED/BLUE existentes

| Componente | Evidência | Classificação |
|---|---|---|
| Hotspot | `RedHotspotController` | API Android LocalOnlyHotspot real, limitada à rede local |
| Portal | `RedPortalServer` | Servidor HTTP real; observa somente requisições destinadas a ele |
| Formulário | `RedPortalServer`, `RedViewModel` | Entrada sintética local, não autenticação de serviço externo |
| Permissões | `RedParticipantScreen` | Chamadas Android reais no participante; operador recebe somente tipo/estado |
| Histórico | `RedSessionStore` | Persistência do protótipo |
| Evidência ao vivo | `RedLiveEvidenceStore` | Compartilhamento em memória |
| BLUE | `BlueViewModel`, `BlueScreen` | Consulta sessões/evidências; sem regras ou resposta operacional |
| Etapas e formulário | `STEP_OPENED`, `STEP_COMPLETED`, `DEMO_FORM_COMPLETED` | Eventos demonstrativos, não execução nem detecção |

A Operação 01 em `distribution/red/operations.json` é roteiro/protótipo. O novo catálogo operacional fica separado em `distribution/lab/operation-catalog.json`.

## Matriz de reutilização e lacunas

| Capacidade | Base reaproveitável | Lacuna |
|---|---|---|
| Execução | `LinuxRuntime`, `SessionManager`, PTY | Orquestração, fila, persistência, eventos comuns e evidências |
| Ativos | snapshots runtime e estado hotspot | inventário persistente e adaptadores Android/ADB/rede |
| Ferramentas | APT, `ToolkitService`, `tools.json` | pré-condições por capacidade e resultados estruturados |
| RED Mobile | permissões e APIs Android | ADB, pacotes, manifests, componentes e análise dinâmica |
| RED Network | `iproute2`, `nmap`, `dnsutils`, `curl` | descoberta integrada e evidências associadas a ativos |
| RED Wi-Fi | LocalOnlyHotspot e portal HTTP | topologia externa, DNS, HTTP/TLS e captura compatível |
| RED Web | toolkit e fixtures locais | perfis de alvo, execução estruturada e repetibilidade |
| BLUE | sessões/eventos RED | ingestão normalizada, regras, investigação e resposta |
| PURPLE | IDs de sessão/eventos | correlação, replay e cobertura |
| Relatórios | `ReportBuilder` e exportação RED | relatório por operação/ativo com evidências do motor |

## Contrato operacional LAB-0

Implementado em `android/runtime/.../operations/LabOperationModels.kt`:
- operação versionada com domínio, alvo, executor, vetor de argumentos, dependências, capacidades, parâmetros e campos de saída;
- catálogo com schema e IDs únicos;
- modelos comuns de ativo, sessão, execução, evento e referência de evidência;
- estados explícitos e severidade de evento.

Implementado em `android/app/.../operations/OperationCatalogJson.kt`:
- parser estrito do catálogo embarcado;
- validação delegada aos modelos de domínio;
- sem execução de comandos ou criação de resultados.

O catálogo inicial declara quatro consultas locais de leitura: identidade do runtime, interfaces, rotas e configuração DNS. A execução pertence ao LAB-1. A declaração de `iproute2` não prova instalação.

## Limitações técnicas registradas

- PRoot não é VM nem fronteira forte de isolamento.
- O processo Linux não recebe privilégios de root do Android.
- LocalOnlyHotspot é rede local e não deve ser apresentado como rede com internet.
- Android não expõe lista universal de clientes, modo monitor ou captura geral por API comum.
- A visibilidade depende do ponto de observação; TLS não é conteúdo inspecionado sem ponto de inspeção real.
- ADB Wireless Debugging e inventário remoto ainda não possuem adaptador implementado.
- Descritor APT não prova instalação ou compatibilidade no aparelho.

## Estado LAB-0

- [x] Auditoria da árvore, módulos e pontos de entrada.
- [x] Separação de infraestrutura, conteúdo e protótipos.
- [x] Matriz de reutilização, dependências e lacunas.
- [x] Contratos comuns de operação, ativo, sessão, execução, evento e evidência.
- [x] Catálogo versionado e parser estrito.
- [x] Inventário de ferramentas e capacidades declaradas.
- [x] Separação do catálogo operacional do roteiro RED legado.
- [x] Validações transferidas para LAB-10.

**Resultado:** LAB-0 concluído em código e documentação. Nenhum teste/build/validação foi executado.
