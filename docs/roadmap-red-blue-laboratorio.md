# Roadmap RED/BLUE — laboratório Android de dois aparelhos

**Branch:** `recovery/red-portal-integration`  
**Escopo:** somente o laboratório com dois aparelhos Android definidos em `AGENTS.md`.  
**Estado deste roadmap:** RED-00 a RED-06, RED-08 a RED-13 e BLUE-01 têm implementação/documentação preparada; RED-07 está implementado em código/documentação e aguarda validação final. RED-14 tem documentação/estabilização preparadas; artefatos de release aguardam os portões finais. RED-15 está em implementação com modo de hotspot do sistema, descoberta de interface e observação de upstream. Testes, builds, compilação e validações ficam acumulados para a etapa final.

Este documento é o plano de execução da Área RED/BLUE nesta branch. O `ROADMAP.md` da raiz continua sendo o roadmap geral do KhaLinux.

## Decisão arquitetural — HTTPS local obrigatório e evolução offline-first

O portal participante deverá operar por **HTTPS local**, sem domínio público, hospedagem externa ou dependência de internet. HTTPS é requisito do RED-07 porque câmera, microfone e geolocalização dependem de uma origem segura nos navegadores modernos; a rede Wi-Fi local, isoladamente, não transforma HTTP em contexto seguro.

- **Primeira fase, totalmente offline:** A cria a rede local, hospeda a preparação HTTP na porta `8080` e o portal HTTPS na porta `8443`; B conecta ao Wi-Fi de A e acessa o portal pelo navegador. Toda a comunicação do portal e dos eventos permanece na rede local.
- **Confiança TLS:** A mantém a chave privada da autoridade certificadora (CA) do laboratório. Somente o certificado público da CA pode ser transferido para B. A impressão digital SHA-256 deve ser conferida por canal independente antes da instalação nas configurações do Android. O certificado do servidor deve conter SAN IP exato do endereço usado pelo B.
- **Permissões web:** cada recurso é solicitado separadamente por ação explícita no B. A registra somente o recurso e o estado efetivamente retornado pelo navegador; não recebe mídia nem coordenadas.
- **Internet depois:** compartilhamento de internet para B não faz parte da primeira entrega. A conectividade local e o portal devem funcionar sem internet. O compartilhamento será uma evolução separada, posterior à estabilização do fluxo offline e sujeita à viabilidade das APIs públicas e às restrições do Android/fabricante.
- **Sem fallback silencioso:** se TLS ou a confiança do certificado falhar, o portal não declara permissões disponíveis nem rebaixa silenciosamente o fluxo sensível para HTTP.

## Progresso da execução

- **Implementação/documentação RED/BLUE:** 17 de 17 submódulos (100%). **Validação final automatizada:** aprovada no workflow `45408044336` (commit `c9f289a`): host checks, auditoria estática, JNI PTY, smoke PRoot, testes unitários Android, `assembleDebug` e `lintDebug`. **Pendências finais:** teste físico A/B e confirmação real de TLS/permissões/continuidade de rede no navegador; não declarar validação física concluída.
- **Concluídos:** RED-00 — [inventário de baseline](red-baseline-inventory.md); RED-01 — [modelo de domínio](red-domain-model.md); RED-02 — [matriz de permissões](red-permissions.md); RED-03 — [ciclo de vida do hotspot](red-hotspot-lifecycle.md); RED-04 — [servidor HTTP local](red-http-server.md); RED-05 — portal local e aviso de cookies; RED-06 — [eventos e evidências](red-event-evidence.md); RED-07 — HTTPS local e permissões web (implementado, não validado); RED-08 — integridade da sessão; BLUE-01 — análise defensiva; RED-09 — [histórico, relatório e exportação](red-session-reports.md); RED-10 — [interface do operador](red-operator-interface.md); RED-11 — [segurança e privacidade](red-security-privacy.md); RED-12 — [plano de validação](red-validation-plan.md); RED-13 — [matriz física](red-physical-validation.md); RED-14 — documentação de estabilização/release (implementada, entrega pendente).
- **Implementado, não validado:** RED-07 — fluxo de permissões web ligado ao HTTPS local em `8443`; CA/servidor X.509 no Android Keystore; preparação HTTP restrita em `8080/`; download da CA, fingerprint, instruções de instalação, evento de handshake após vínculo do participante, cookie Secure, validação Origin HTTPS, renovação do certificado por sessão/IP e limpeza da chave efêmera do servidor. Compatibilidade real da CA e disponibilidade das APIs ficam para validação final.
- **Parcial:** RED-15 — `RedSystemHotspotController` abre Configurações do Android, compara baseline de interfaces/IPv4, detecta um endereço inequívoco e observa upstream do A por callback público; ViewModel, UI e relatórios integrados. Validação de descoberta OEM e continuidade de B ao ativar upstream pendente.
- **RED-14 — documentação/estabilização concluídas; entrega final bloqueada:** notas, matriz de compatibilidade, checklist e comparação da branch atualizados. Revisão integral do diff, validação, APK assinado/hash e PR continuam pendentes.
- **Estado RED-14:** parcela documental concluída; pendências de entrega registradas em `docs/red-release-checklist.md`.
- **Validação:** não iniciada nesta execução; todas as atividades de teste/build/compilação ficam para a etapa final.
- **Pacotes ZIP:** desativados; execução diretamente no repositório conforme instrução do usuário.

---

## 1. Objetivo do produto

Construir uma ferramenta Android de laboratório que permita ao instrutor:

1. Preparar uma rede de teste usando recursos e APIs realmente disponíveis no Android.
2. Exibir os dados efetivamente fornecidos pelo sistema: estado, SSID, credenciais de rede quando disponibilizadas pela API, interface, endereço IP, porta e erros.
3. Hospedar um portal local acessível pelo segundo aparelho, com HTTPS obrigatório para a experiência participante e HTTP restrito à preparação inicial.
4. Observar as requisições que realmente chegam ao servidor, sem inventar clientes, eventos ou telemetria.
5. Demonstrar escolhas de cookies e, quando o contexto seguro permitir, permissões do próprio navegador com solicitações explícitas e visíveis.
6. Comparar a visão operacional RED com a análise defensiva BLUE, usando as mesmas evidências observadas.
7. Encerrar a sessão e produzir um relatório fiel, distinguindo observações, ações do operador, permissões, resultados e indisponibilidades.

O aparelho B participa exclusivamente pelo navegador. O portal não concede permissões nativas do Android nem acessa arquivos arbitrários. Permissões web de câmera, microfone e localização só podem ser solicitadas pelas APIs do navegador em contexto seguro e com ação explícita; HTTP em IP privado normalmente não atende a esse requisito. Não ocultar finalidade, não agrupar permissões nativas sob o botão de cookies e não inventar concessões. O aparelho A concentra a operação, a linha do tempo e o relatório.

## 2. Topologia fixa do laboratório

| Elemento | Função |
|---|---|
| Aparelho A | Operador: executa KhaLinux, solicita a rede, hospeda o servidor local e exibe RED/BLUE. |
| Aparelho B | Participante passivo: conecta-se ao Wi-Fi, abre o portal no navegador, faz o login de teste e responde aos avisos legítimos do navegador. Não instala nem executa o KhaLinux e não opera ferramentas ou painéis. |
| Comunicação | Rede local criada para a sessão; servidor limitado à interface/endereço do laboratório. |
| Dados | Eventos mínimos necessários à aula; nenhum dado pessoal ou segredo deve ser coletado ou persistido. |

Não haverá descoberta, varredura ou teste de terceiros. A aplicação não deve presumir que todo cliente conectado ao hotspot é visível ao Android: a lista de clientes pode não ser disponibilizada a apps comuns. Quando não houver API pública confiável, mostrar **“não disponibilizado pelo Android”**. Requisições recebidas pelo servidor são evidência de atividade HTTP, não uma lista completa de dispositivos conectados.

## 3. Regras de fidelidade e apresentação

- Dado observado deve ter origem, horário e estado identificáveis.
- Dado informado pelo operador deve ser rotulado como configuração, não como leitura do Android.
- Informação não acessível deve aparecer como indisponível/não suportada, com motivo quando conhecido.
- Não usar SSID, senha, IP, cliente, permissão, localização, requisição ou resultado fixo para preencher lacunas.
- Fixtures sintéticas são permitidas exclusivamente em testes automatizados e devem estar claramente identificadas como fixtures.
- A interface de execução não pode misturar fixture com observação real.
- Não armazenar senhas de formulários, tokens, cookies, cabeçalhos Authorization, conteúdo de arquivos, áudio, vídeo ou coordenadas no histórico ou nos relatórios.
- Formulários de aula devem ser próprios do laboratório, identificados como demonstração e não imitar provedores ou serviços reais. Não coletar credenciais de contas.
- O portal não fará proxy transparente, interceptação de tráfego de outros apps, captura de tráfego externo ou redirecionamento de navegação fora do servidor local.
- A interface BLUE deve analisar somente evidências realmente produzidas pela sessão; recomendações devem ser rotuladas como análise, não como detecção observada.

## 4. Estado inicial conhecido e pontos que precisam de auditoria

A documentação existente descreve hotspot `LocalOnlyHotspot`, servidor HTTP, portal local, painel de eventos, modo participante e análise BLUE. Entretanto, a documentação da Área RED contém afirmações divergentes sobre o estado de implementação e sobre quais dados de formulário seriam exibidos. O snapshot ZIP recuperado também contém arquivos incompatíveis entre si e um `RedPortalServer.kt` truncado/contaminado por texto de comando shell.

Por isso, o ponto de partida desta branch é **auditoria do código**, não assumir que os recursos descritos já funcionam.

Itens conhecidos a conferir:

- A versão oficial de `RedPortalServer.kt` no histórico do repositório tem API antiga; a versão exportada tem outro construtor e está truncada.
- `RedViewModel.kt` exportado aparenta continuar chamando a API antiga do servidor.
- `RedCaptureStore.kt` exportado declara um segundo `RedPortalMode` e contém modelo de credenciais. Não integrar armazenamento de senhas.
- `RedNetworkController.kt`, `LocalOnlyHotspotController.kt`, `LocalOnlyHotspotNetworkController.kt` e `TetheringNetworkController.kt` representam abordagens diferentes e precisam de decisão arquitetural antes de integração.
- O controlador exportado que atribui `OPEN` sem confirmar a segurança real do hotspot deve ser corrigido; não inferir segurança.
- A descoberta genérica de interface de rede pode selecionar a interface errada. A interface deve ser correlacionada com a sessão real ou marcada como não identificada.
- `RedTemplateRenderer.kt` exportado contém páginas de imitação de serviços e solicitação de credenciais. Não integrar esse fluxo.
- Assets HTML/CSS exportados precisam de revisão de conteúdo, rotas, formulários e referências.
- A documentação atual menciona capacidades que ainda precisam ser confirmadas no código e por testes.

A auditoria deverá comparar arquivo por arquivo: versão da branch, versão oficial do Git, snapshot exportado e dependências. Nenhum arquivo será copiado em massa.

---

# 5. Fases do roadmap

## RED-00 — Baseline, recuperação e inventário

**Estado:** concluído em inventário documental e inspeção do código RED existente. Ver [red-baseline-inventory.md](red-baseline-inventory.md). Nenhum teste ou build foi executado.

**Objetivo:** saber exatamente o que existe antes de modificar.

Atividades:
- Registrar branch, commit-base e estado do working tree.
- Listar arquivos RED/BLUE existentes no Git e comparar com o snapshot recuperado.
- Inspecionar integralmente modelos, ViewModels, controladores, servidor, telas, manifest, Gradle e assets.
- Identificar classes duplicadas, enums conflitantes, chamadas incompatíveis, código truncado, comandos shell inseridos em Kotlin e referências ausentes.
- Mapear cada recurso documentado para classe, método, estado de implementação e evidência.
- Preservar backup do snapshot e do estado anterior.
- Produzir matriz de recuperação: manter, corrigir, reescrever ou descartar, com justificativa.

**Testes e verificações:**
- Revisão estática e diff; nenhuma alegação de teste de runtime.
- Verificar consistência de pacotes, símbolos, imports, dependências e Manifest.
- Confirmar que o servidor oficial não foi substituído acidentalmente.

**Aceite:** inventário rastreável; nenhuma cópia em massa; lista de bloqueios e decisão arquitetural documentadas.

## RED-01 — Modelo de domínio e estados reais

**Estado:** implementação concluída. Ver [red-domain-model.md](red-domain-model.md). Testes ficam pendentes para a fase final.

**Objetivo:** estabelecer um modelo único para sessão, rede, portal, participante e evidências.

Atividades:
- Definir estados explícitos: parado, preparando, aguardando sistema, ativo, degradado, encerrando e falha.
- Separar configuração solicitada, valor informado pelo Android e observação de rede.
- Definir modelos para interface, endereço, SSID, segurança, porta, participante observado, requisição HTTP, permissão e evento.
- Representar valores desconhecidos como ausência/indisponibilidade, nunca como valores padrão fictícios.
- Remover enums duplicados e tipos de credenciais desnecessários.
- Definir IDs de sessão e eventos, timestamps e origem de cada observação.
- Definir limites de tamanho, quantidade, duração e retenção.

**Testes host/unitários:**
- Transições válidas e inválidas de estado.
- Valores ausentes permanecem ausentes.
- Nenhum valor padrão é apresentado como leitura real.
- Eventos duplicados, fora de ordem e sessão encerrada.
- Limites e retenção.

**Aceite de implementação:** modelos únicos e tipados, estados desconhecidos anuláveis, eventos com origem/tempo e persistência compatível com sessões antigas. Testes e resultados ficam acumulados para a fase final.

## RED-02 — Permissões e capacidades do Android

**Estado:** implementação concluída para o aparelho A; o B não possui permissões de aplicativo. Ver [red-permissions.md](red-permissions.md). Testes de versão/estado ficam para a fase final.

**Objetivo:** declarar e solicitar somente o necessário, usando o sistema real.

Atividades:
- Auditar `AndroidManifest.xml`, `targetSdk`, `minSdk`, permissões normais, perigosas e permissões especiais.
- Criar uma matriz por recurso: API usada, permissão, versão mínima, fluxo de solicitação, retorno concedido/negado/permanentemente negado/revogado/não suportado.
- Usar Activity Result APIs e APIs públicas compatíveis com a versão Android alvo.
- No aparelho A, solicitar fine/coarse somente ao iniciar hotspot e distinguir concessão precisa/aproximada, recusa, recusa permanente e revogação.
- Consultar novamente o estado após retorno de Configurações e ao retomar a Activity.
- Tratar revogação durante uso e interrupção do ciclo de vida.
- Solicitar no aparelho A somente permissões exigidas pela operação de hotspot/rede, no momento em que forem necessárias.
- Não declarar permissões de câmera/microfone para o app operador quando o B usa somente navegador.
- Permissões web no B pertencem ao RED-07 e dependem de contexto seguro, política do navegador e ação explícita.

**Testes automatizados:**
- Testes de mapeamento de estados de permissão.
- Testes de cancelamento, negação, revogação e retorno de Configurações com doubles/fakes claramente limitados a testes.
- Testes de ciclo de vida e limpeza de recursos.

**Aceite de implementação:** o aparelho A solicita permissões somente no momento do hotspot, mostra justificativa, registra concessão/recusa/recusa permanente/revogação e direciona a Configurações quando necessário. Validação por versão Android fica para a etapa final.

## RED-03 — Rede de laboratório e hotspot

**Estado:** implementação concluída em código/documentação; testes e validação de fabricante/versão ficam para a fase final. Ver [red-hotspot-lifecycle.md](red-hotspot-lifecycle.md).

**Objetivo:** iniciar e encerrar a rede usando a API suportada pelo Android.

Atividades:
- Escolher uma única implementação primária baseada em `WifiManager.startLocalOnlyHotspot` quando suportada.
- Não usar reflexão ou APIs privilegiadas/deprecated como caminho padrão sem justificativa técnica documentada.
- Verificar disponibilidade, permissão/condições exigidas pela versão Android e callback real do sistema.
- Obter SSID, senha, banda e demais dados exclusivamente da configuração efetivamente fornecida pela API.
- Não afirmar que o hotspot é aberto ou WPA2 sem leitura confiável.
- Identificar o endereço/interface vinculados à sessão. Correlacionar a interface criada; se isso não for possível, informar indisponibilidade e bloquear a inicialização do servidor que exige bind específico.
- Implementar timeout, cancelamento, callbacks tardios, start duplicado, stop durante start, fechamento e recuperação.
- Exibir limitações de fabricante e versão Android.
- Não chamar fallback de tethering como se fosse equivalente a LocalOnlyHotspot. Qualquer alternativa deve ser recurso separado, explicitamente suportado e testado.

**Testes host/unitários:**
- Máquina de estados do controlador por adaptador.
- Callback de sucesso, falha, timeout e callback tardio.
- Cancelamento e stop idempotente.
- Corridas entre start/stop e prevenção de duas reservas simultâneas.
- Validação de configuração sem fabricar valores do Android.

**Aceite de implementação:** geração de solicitação, timeout, cancelamento, callbacks tardios, stop idempotente e correlação inequívoca de interface/IPv4 implementados; configurações vêm da reserva Android e campos não fornecidos permanecem nulos. Build e testes ficam para a fase final.

## RED-04 — Interface de rede e servidor HTTP local

**Estado:** implementação concluída em código/documentação; testes host e integração ficam para a fase final. Ver [red-http-server.md](red-http-server.md).

**Objetivo:** disponibilizar o portal apenas na interface e endereço de laboratório identificados.

Atividades:
- Vincular socket ao IPv4 específico da interface da sessão, nunca indiscriminadamente a todas as interfaces.
- Confirmar que o endereço pertence à interface selecionada antes de iniciar.
- Tratar interface ausente, endereço alterado, porta ocupada, perda de rede e encerramento do hotspot.
- Implementar limites de cabeçalho, corpo, conexões, tempo de leitura, tamanho de resposta e concorrência.
- Validar método, caminho, query, Content-Type e tamanho antes de processar.
- Rejeitar framing ambíguo, caminhos inválidos, traversal, cabeçalhos duplicados perigosos e métodos não suportados.
- Não registrar Cookie, Authorization, tokens ou corpos que não sejam estritamente necessários.
- Servir apenas assets locais incluídos no app; não buscar páginas remotas.
- Exibir explicitamente que HTTP local não é HTTPS e não oferece criptografia de transporte.

**Testes host/unitários:**
- GET/POST locais, status e cabeçalhos.
- Porta ocupada, endereço inválido, bind errado e interface indisponível.
- Cabeçalhos excessivos, corpo excessivo, timeout, desconexão e concorrência.
- Traversal, encoding inválido, métodos não permitidos e framing malformado.
- Verificar que o servidor não escuta em interfaces externas ou endereço curinga.

**Aceite de implementação:** bind exclusivo no IPv4 privado/compartilhado observado, Host e interface revalidados, limites de request/response/conexão, framing ambíguo rejeitado, eventos únicos inclusive requisições rejeitadas e encerramento ao perder a interface. Testes ficam para a fase final.

## RED-05 — Portal local, aviso de cookies e experiência do navegador

**Estado:** implementação concluída em código/documentação; testes de rotas, consentimento e navegação ficam para a fase final.

**Objetivo:** criar uma página de laboratório clara e funcional, sem imitação de serviços reais.

Atividades:
- Criar landing page local com identificação de laboratório e descrição do exercício.
- Implementar aviso de cookies com categorias necessárias, análise, preferências e personalização, descrevendo apenas o que o portal realmente utiliza.
- Oferecer “Aceitar todos”, “Recusar opcionais” e “Configurar”; registrar no A a escolha e as categorias, sem gravar valores de cookies ou identificadores pessoais.
- O botão de cookies não pode conceder permissões do navegador/Android nem disparar solicitações não descritas.
- Oferecer cenários separados: acesso direto e formulário de demonstração.
- Se houver formulário, usar somente campos neutros próprios do exercício, por exemplo identificador e código de demonstração não reutilizáveis.
- Não pedir e-mail/senha de contas, não imitar Google/Facebook/Instagram ou páginas bancárias e não enviar credenciais a terceiros.
- Exibir claramente o propósito do formulário e a ação de envio.
- Implementar navegação e redirecionamento apenas entre rotas locais previstas.
- Mostrar URL/QR Code derivados do endereço real do servidor.
- Não prometer abertura automática de portal: Android pode não disparar captive portal detection em redes LocalOnlyHotspot.

**Testes automatizados:**
- Renderização dos assets e rotas.
- Formulário aceita somente campos e formatos do exercício.
- Navegação e redirect local.
- Nenhuma URL externa ou marca de terceiro nos assets.
- Nenhum segredo embutido no HTML, JS ou CSS.

**Aceite de implementação:** portal local com aviso de cookies funcional, escolhas persistidas no navegador somente após confirmação do A, eventos tipados por categoria, login exclusivo de demonstração e rotas locais. Testes ficam para a fase final.

## RED-06 — Observabilidade HTTP e linha do tempo

**Objetivo:** exibir somente eventos realmente recebidos.

Atividades:
- Registrar horário, método, rota normalizada, status, tamanho, Content-Type e endereço remoto quando disponível.
- Definir allowlist de cabeçalhos de baixo risco; excluir Cookie, Set-Cookie, Authorization, Proxy-Authorization e tokens.
- Distinguir requisição recebida, requisição rejeitada, resposta enviada e falha do servidor.
- Associar eventos à sessão atual e eliminar dados transitórios ao encerrar.
- Mostrar contagem de requisições recebidas sem inventar participantes ou clientes.
- Registrar escolha de cookies por categoria sem registrar valores de cookies.
- Não inferir que uma conexão Wi-Fi existe só porque uma requisição foi recebida, nem o contrário.
- Não fazer captura de tráfego geral, sniffing, MITM ou inspeção de outros aplicativos.

**Testes automatizados:**
- Evento criado apenas após requisição real ao servidor no teste.
- Campos sensíveis filtrados.
- Timestamps e ordenação.
- Limites de eventos e sessão encerrada.
- Falha do servidor não aparece como resposta bem-sucedida.

**Aceite:** cada linha do painel corresponde a um evento observável e rastreável.

## RED-07 — Demonstrações de permissões no navegador do aparelho B

**Estado:** parcial e bloqueador para a conclusão do portal participante. Fluxo, endpoint e eventos tipados estão implementados; HTTPS local offline, confiança da CA no B e validação do navegador são obrigatórios e ainda estão pendentes. Ver [red-browser-permissions.md](red-browser-permissions.md) e [red-local-tls.md](red-local-tls.md).

**Objetivo:** registrar escolhas e permissões web efetivamente oferecidas pelo navegador do B, sem aplicativo participante instalado.

Atividades:
- Manter o B exclusivamente no navegador; não instalar nem executar KhaLinux nele.
- Implementar solicitações web de câmera/microfone/localização somente quando a origem for segura e o navegador suportar a API; cada solicitação terá descrição e ação próprias.
- Não disparar permissões como efeito colateral de “Aceitar todos” cookies.
- Não capturar, armazenar ou transmitir mídia, coordenadas ou arquivos. Registrar somente o estado que o navegador efetivamente retornar.
- A experiência participante final deve usar HTTPS local com certificado confiável no B. HTTP na porta `8080` fica limitado à preparação e à entrega do certificado público da CA, sem prompts de permissões.
- Exibir no A solicitações, concessões, recusas, cancelamentos e indisponibilidades que tenham sido efetivamente observadas.

**Testes automatizados (transferidos para a etapa final):**
- Origem segura e insegura.
- Estados de permissão permitida, negada e não suportada.
- Evento mínimo sem payload sensível.
- Revogação e encerramento da sessão.

**Implementação RED-07 concluída em código:** geração de CA e certificado de servidor X.509 com SAN IP usando Android Keystore; `SSLServerSocket` TLS 1.2/1.3 com conjuntos modernos no IPv4 da sessão; endpoint HTTP 8080 com página de preparação, download DER público e health; portal HTTPS-only em 8443; fingerprint independente; eventos de download e handshake após vínculo da sessão; cookie Secure e validação Origin HTTPS; chave efêmera removida no encerramento. O handshake comprova aceitação daquela conexão pelo cliente, não concessão das APIs web. Validação física/browser permanece pendente.

**Validação acumulada:** confirmar confiança da CA no navegador/versão Android alvo, ausência de alerta de certificado, SAN IP correto, handshake observado, prompts reais de cada API, falha segura diante de CA não confiável e encerramento/renovação. Não executar até a fase final.

**Aceite obrigatório:** B permanece navegador-only; HTTPS funciona sem internet; o certificado contém SAN IP correspondente ao endereço acessado; a cadeia é aceita pelo navegador sem alerta; a CA pública é conferida por impressão digital; a chave privada da CA permanece em A; câmera, microfone e localização são solicitados individualmente por ação explícita; A recebe somente estados verificáveis e metadados mínimos. RED-07 não pode ser concluído sem esse aceite.

## RED-08 — Segurança da sessão e ciclo de vida

**Estado:** implementação em código/documentação concluída; validação pendente. Ver [red-session-security.md](red-session-security.md).

**Objetivo:** garantir que sessões não fiquem abertas ou reaproveitáveis indevidamente.

Atividades:
- Gerar token aleatório por sessão, manter em memória, comparar de forma segura e invalidar no encerramento.
- Limitar tentativas, validade, origem e rotas autorizadas do endpoint participante.
- Não colocar tokens em URLs, QR Codes públicos ou logs.
- Implementar stop completo: servidor, sockets, hotspot reservation, callbacks, jobs e dados efêmeros.
- Lidar com rotação, processo morto, app em segundo plano, tela bloqueada, falta de memória e reinício.
- Recuperar sessão interrompida como interrompida, nunca reiniciar hotspot automaticamente.
- Aplicar retenção limitada a histórico de metadados não sensíveis, com ação clara de exclusão.

**Testes automatizados:**
- Token expirado/inválido/reutilizado.
- Stop repetido, crash simulado, callback tardio e recuperação.
- Verificar que nenhum serviço/socket permanece após stop.
- Verificar retenção e exclusão.

**Aceite:** nenhuma sessão ou recurso permanece ativo após encerramento esperado; sessão interrompida não é apresentada como concluída.

## BLUE-01 — Painel defensivo baseado nas evidências RED

**Estado:** implementação em código/documentação concluída; validação pendente. Ver [blue-area.md](blue-area.md).

**Objetivo:** transformar os eventos da sessão em explicações defensivas sem inventar detecções.

Atividades:
- Consumir o mesmo repositório de eventos da sessão RED.
- Mostrar fatos observados em uma área e interpretação/recomendação em outra.
- Explicar hotspot, endpoint HTTP restrito de preparação, portal HTTPS local, formulários, redirecionamentos, permissões e limites de visibilidade.
- Indicar riscos reais demonstrados e controles defensivos correspondentes.
- Não classificar uma requisição isolada como ataque ou comprometimento.
- Não inventar alertas, dispositivos, vulnerabilidades ou resultados.
- Permitir alternar RED/BLUE sem duplicar ou alterar evidências.

**Testes automatizados:**
- Mesmo evento/ID aparece nas duas perspectivas.
- Análise não altera eventos originais.
- Recomendações não são exibidas como fatos detectados.
- Ausência de evidência não gera alerta fictício.

**Aceite:** BLUE apresenta a mesma sessão observada com contexto defensivo rastreável.

## RED-09 — Histórico, relatório e exportação

**Estado:** implementação em código/documentação concluída; validação pendente. Ver [red-session-reports.md](red-session-reports.md).

**Objetivo:** preservar evidência útil sem armazenar dados pessoais.

Atividades:
- Definir schema versionado e migrações.
- Persistir apenas metadados necessários: horário, estado, modo, rota, status, erros, consentimento por categoria e estados web mínimos.
- Não persistir campos de formulário, senha/código, cookies, tokens, mídia, coordenadas ou conteúdo de arquivo.
- Exportar relatório com versão do app, versão Android informada pelo sistema, ID da sessão, horário, estado final, limitações e eventos.
- Marcar claramente dados indisponíveis e testes não executados.
- Implementar exclusão de sessão/relatório e limites de retenção.

**Testes automatizados:**
- Migração de schema.
- Serialização e leitura.
- Sanitização de campos sensíveis.
- Exportação e exclusão.
- Sessão interrompida e relatório parcial.

**Aceite:** relatório reproduz o que foi observado e não contém conteúdo sensível.

## RED-10 — Integração da interface do operador e acessibilidade operacional

**Estado:** implementação em código/documentação concluída; validação de estados, rotação, navegação e acessibilidade pendente para a fase final. Ver [red-operator-interface.md](red-operator-interface.md).

**Objetivo:** painel claro para o instrutor e fluxo simples para o participante.

Atividades:
- Integrar estados do ViewModel sem duplicar fontes de verdade.
- Manter toda a observabilidade, os resultados e as explicações no aparelho A; B recebe somente a experiência do navegador.
- Mostrar estado real de hotspot, servidor, interface, endereço, porta e participante.
- Exibir erros e ações possíveis para recuperação.
- Diferenciar botão de solicitar, estado pendente e resultado confirmado.
- Exibir o portal HTTPS local, endpoint de preparação da CA, fingerprint e estado de confiança ainda não confirmado; explicar limites de visibilidade.
- Garantir que tela não apresente sucesso antes do callback real.
- Tratar rotação, processo em segundo plano e navegação RED/BLUE.

**Testes automatizados:**
- ViewModel com estados e fluxos.
- Loading, sucesso, erro, cancelamento e retry.
- Rotação/recriação e navegação.
- Sem estado visual de sucesso antes da confirmação.

**Aceite de implementação:** estados e callbacks reais, dados do hotspot/servidor condicionados a confirmações, erros e recuperação visíveis, A centraliza observabilidade, B permanece navegador-only, RED/BLUE compartilham evidências e interrupção de ciclo de vida é registrada. Testes ficam para a fase final.

## RED-11 — Revisão de segurança e privacidade

**Estado:** hardening de código/documentação concluído; auditoria estática, revisão manual e testes negativos transferidos para a fase final. Ver [red-security-privacy.md](red-security-privacy.md).

**Objetivo:** revisar o conjunto antes de compilar release e testar fisicamente.

Atividades:
- Revisar permissões, exported components, intents, providers e serviços.
- Revisar servidor HTTP, validação, limites, endpoint de consentimento e bind.
- Buscar segredos, credenciais, tokens, dados pessoais e logging de corpos.
- Revisar HTML/JS/CSS e dependências.
- Confirmar que não há coleta de credenciais de serviços, captura de tráfego de terceiros, proxy ou persistência oculta.
- Revisar retenção, exclusão e relatório.
- Registrar riscos residuais e limitações.

**Testes:**
- Auditoria estática automatizada.
- Testes negativos de validação e autenticação.
- Revisão manual com checklist versionado.
- Verificar que erros e bloqueios são reportados, não mascarados.

**Aceite:** zero achado crítico/alto aberto; achados restantes documentados e aceitos antes de release.

## RED-12 — Integração, compilação e testes host/Android automatizados

**Estado:** integração de código e plano de validação documentados; compilação, lint, testes host/Android e auditoria transferidos para a fase final. Ver [red-validation-plan.md](red-validation-plan.md).

**Objetivo:** provar que o código integrado compila e que as partes testáveis funcionam antes de qualquer aparelho físico.

Atividades:
- Integrar arquivos em commits pequenos.
- Executar format/lint disponível.
- Compilar módulos Kotlin e Android com Gradle/SDK compatíveis.
- Executar testes unitários e instrumentados disponíveis em emulador, quando possível.
- Executar testes do servidor em loopback e testes de integração de componentes.
- Verificar manifesto, permissões declaradas, assets e pacote final.
- Registrar comandos, ambiente, commit, resultado e logs relevantes.
- Corrigir falhas e repetir a suíte completa.

**Portão de saída obrigatório:**
- Build Debug concluído.
- Testes unitários concluídos sem falhas.
- Lint/auditoria concluídos sem achados críticos/altos.
- Testes de integração host concluídos.
- APK identificável por hash e commit.
- Nenhum teste físico iniciado antes deste portão.

## RED-13 — Validação física final nos dois aparelhos

**Estado:** matriz física corrigida para B navegador-only; execução nos aparelhos transferida para a fase final, após RED-12. Ver [red-physical-validation.md](red-physical-validation.md).

**Objetivo:** validar o comportamento real no hardware Android definido para o laboratório. Esta fase só começa após RED-12.

### Matriz de testes físicos

| ID | Ação real | Evidência exigida |
|---|---|---|
| PHY-01 | Iniciar sessão no aparelho A | ID, horário e estado inicial registrados. |
| PHY-02 | Solicitar LocalOnlyHotspot | Callback real do Android; SSID/segurança somente se fornecidos. |
| PHY-03 | Identificar interface/IP | Interface e endereço observados e vinculados à sessão. |
| PHY-04 | Iniciar servidor local | Bind no IP específico e porta confirmados. |
| PHY-05 | Conectar B ao Wi-Fi | Conexão manual; nenhuma lista universal de clientes presumida. |
| PHY-06 | Abrir URL/QR no navegador B | Requisição real aparece no painel A. |
| PHY-07 | Fazer login de laboratório | POST com identificador/código sintéticos; nenhum segredo pessoal. |
| PHY-08 | Aceitar cookies | Categorias registradas; nenhum pedido de recurso disparado. |
| PHY-09 | Recusar cookies opcionais | Escolha registrada sem categorias opcionais. |
| PHY-10 | Configurar cookies | Estados customizados por categoria registrados. |
| PHY-11 | Abrir Permissões do navegador | Ações separadas para câmera, microfone e localização. |
| PHY-12 | Solicitar permissões via HTTP/IP privado | UNAVAILABLE quando origem não for segura; nenhum prompt forçado. |
| PHY-13 | Permissões via HTTPS confiável, se disponível | Cada recurso solicitado por clique separado; diálogo real do navegador. |
| PHY-14 | Negar/cancelar permissão web, quando disponível | Estado negado/cancelado registrado sem declarar sucesso. |
| PHY-15 | Conceder/revogar permissão web, quando disponível | Estado real observado; nenhum payload enviado ao A. |
| PHY-16 | Navegar páginas/links/redirecionamentos | Métodos, rotas, status e horários correspondem às requisições reais. |
| PHY-17 | Alternar RED/BLUE | Mesmos IDs e eventos nas duas perspectivas. |
| PHY-18 | Perda de rede/fechamento do B | Erro/ausência de novas requisições apresentados sem inventar desconexão. |
| PHY-19 | Encerrar sessão e revisar relatório | Recursos encerrados; relatório sem cookies, tokens, campos, mídia ou coordenadas. |
| PHY-20 | Repetir ciclo completo | Nova sessão independente, sem estado residual. |

Para cada execução registrar: ID do teste, modelo dos dois aparelhos, versão Android, versão do app, commit, pré-condições, passos, resultado esperado, resultado observado, evidência sem dados pessoais, falha e issue associada. Não publicar IMEI, número de série, MAC completo, localização ou identificadores pessoais nos relatórios.

**Aceite:** todos os testes obrigatórios aprovados ou bloqueios documentados; nenhuma falha crítica/alta; resultados reproduzíveis; relatório final assinado pelo responsável do laboratório.

## RED-14 — Estabilização e entrega da branch

**Estado:** parcial. Notas de release, compatibilidade, checklist e revisão direcionada da branch documentados. Revisão integral do diff, resolução de RED-07, validação, APK/hash e PR permanecem pendentes. Ver [red-branch-stabilization.md](red-branch-stabilization.md) e [red-release-checklist.md](red-release-checklist.md).

**Estado:** parcial. Notas de release, matriz de compatibilidade e checklist preparados; APK/hash, revisão final do diff e PR dependem da validação final. Ver [red-release-checklist.md](red-release-checklist.md). Não fazer merge nem alterar main.

Atividades:
- Corrigir falhas da validação física.
- Reexecutar testes automatizados e testes físicos afetados.
- Atualizar guias, changelog, screenshots autorizados e matriz de compatibilidade.
- Remover logs temporários e artefatos de diagnóstico.
- Marcar versão candidata e produzir APK com hash.
- Revisar diff completo contra a base da branch.
- Preparar PR para integração, sem alterar `main` até decisão explícita.

**Aceite:** documentação, código, testes, APK e evidências apontam para o mesmo commit/versão; branch pronta para revisão.

---

## 6. Estratégia de testes por camada

| Camada | Onde | O que comprova | O que não comprova |
|---|---|---|---|
| Unitários | Host/JVM | Regras, estados, validação, retenção e sanitização. | Permissões ou APIs reais do Android. |
| Integração host | Host/loopback | Protocolo HTTP, rotas, limites, eventos e ciclo do servidor. | Hotspot ou interface Wi-Fi Android. |
| Build Android | Gradle/SDK | Compatibilidade de compilação, recursos e Manifest. | Funcionamento em hardware. |
| Emulador | Android Emulator | Ciclo de Activity, permissões simuladas pelo SO do emulador e parte da UI. | Comportamento do hotspot de fabricante/dispositivo físico. |
| Físico A+B | Dois aparelhos do laboratório | Hotspot, rede, portal, permissões, ciclo de vida e compatibilidade real. | Comportamento em todos os modelos Android existentes. |

Fixtures e fakes devem permanecer na suíte de testes. A interface de produção não pode carregá-los como fallback.

## 7. Registro de evidências e definição de concluído

Cada fase deve manter:
- commit(s) que implementam a fase;
- arquivos alterados;
- comandos de teste executados;
- ambiente e versões;
- contagem de testes e falhas;
- logs/evidências sanitizados;
- limitações conhecidas;
- critérios de aceite marcados individualmente.

Estados permitidos:
- **Não iniciada**
- **Em implementação**
- **Implementada, não validada**
- **Validada em host**
- **Validada em Android/emulador**
- **Validada fisicamente**
- **Bloqueada**

Uma fase só recebe o estado correspondente à evidência disponível. “Código escrito”, “build passou”, “teste host passou” e “teste físico passou” são estados diferentes.

## 8. Ordem de execução

1. RED-00 — baseline e topologia.
2. RED-01 — modelo e estados.
3. RED-02 — permissões Android do operador A.
4. RED-03 — ciclo de vida do hotspot.
5. RED-04 — servidor HTTP local.
6. RED-05 — portal e consentimento de cookies.
7. RED-06 — eventos, persistência e BLUE.
8. RED-07 — implementar e validar HTTPS local offline e concluir permissões web no navegador do aparelho B (requisito obrigatório).
9. RED-08 — segurança e ciclo de vida da sessão.
10. BLUE-01 — painel defensivo baseado nas evidências RED.
11. RED-09 — histórico e relatório.
12. RED-10 — integração da interface do operador.
13. RED-11 — hardening e documentação; auditoria final adiada.
14. RED-12 — integração documentada; build/testes adiados.
15. RED-13 — matriz física corrigida; execução adiada.
16. RED-14 — estabilização e entrega da branch.
17. **RED-15 — hotspot do sistema e upstream posterior:** implementação concluída. O modo alternativo usa o hotspot gerenciado pelo Android, iniciado manualmente nas Configurações. B conecta-se a essa rede enquanto o upstream está ausente; depois o operador ativa dados móveis/upstream sem desligar o hotspot, preservando a associação Wi-Fi de B quando suportado pela OEM/operadora. O modo `LocalOnlyHotspot` continua isolado e não pode ser convertido em tethering em execução. Validação física pendente.

Testes, builds, auditorias de validação e testes físicos só serão executados depois da conclusão da implementação integral. Dependências externas indisponíveis permanecem registradas, sem inventar resultados.
 
## 9. RED-15 — hotspot do sistema e upstream posterior

**Objetivo:** permitir que B permaneça associado ao mesmo hotspot quando o operador disponibilizar internet posteriormente, sem depender de APIs ocultas.

Implementação inicial registrada:
- Modo `LocalOnlyHotspot` preservado para laboratório totalmente isolado.
- Modo alternativo abre as Configurações de tethering do Android; o operador liga o hotspot do sistema manualmente.
- O controlador registra os IPv4 antes da abertura das Configurações e aceita somente um IPv4 novo/inequívoco para vincular o portal.
- O mesmo portal HTTPS/8443 e preparação CA/8080 são hospedados na interface do hotspot do sistema.
- `ConnectivityManager.registerDefaultNetworkCallback` observa se A tem upstream com capacidade INTERNET e se ele foi validado.
- O painel e o relatório distinguem upstream validado em A de acesso externo efetivamente confirmado em B; não inventam sucesso de roteamento.
- As páginas participantes enviam heartbeat same-origin autenticado a cada 20 segundos enquanto visíveis; A registra último contato e evento limitado a uma ocorrência por minuto. Isso comprova alcance local do portal, não internet externa.
- O KhaLinux não usa reflexão, APIs ocultas nem permissões privilegiadas para iniciar tethering. No Android 15, a ativação é feita pelo usuário nas Configurações.

Fluxo recomendado para manter B conectado: ativar o hotspot do sistema sem upstream, conectar B e abrir o portal; depois ativar dados móveis/upstream em A sem desligar o hotspot. A permanência da conexão depende da implementação Android/OEM/operadora e será confirmada na validação física.

**Implementação entregue:** A abre as Configurações, identifica um candidato de interface/IPv4 sem ambiguidade, hospeda o portal HTTPS, observa upstream em A e recebe heartbeat autenticado de B. **Validação final:** confirmar no aparelho alvo que B permanece associado e recebe internet após ativação do upstream; nenhum evento afirma navegação externa de B sem evidência direta.

**Limitação registrada:** o modo LocalOnlyHotspot não pode receber upstream posteriormente. Para evitar que B tenha de trocar de rede, deve-se escolher o hotspot do sistema desde o início da sessão.

## 10. Fora do escopo desta branch

- Uso em aparelhos, redes ou pessoas fora dos dois dispositivos do laboratório.
- Captura de credenciais de contas ou imitação de páginas de provedores.
- Acesso oculto ou não solicitado a câmera, microfone, localização ou arquivos.
- Extração de conteúdo pessoal do aparelho participante.
- Interceptação/MITM, proxy transparente ou captura de tráfego de outros aplicativos.
- Alegar visibilidade total de clientes quando o Android não oferece essa informação.
- Declarar sucesso de operações sem callback, resposta ou evidência observável.
- Alterar `main` antes de revisão e decisão explícita.

---

## 11. Primeiro trabalho após este roadmap

A implementação integral do roadmap está concluída. Próxima fase: executar os testes e validações acumulados, corrigir falhas encontradas, validar no aparelho físico A/B e fechar os artefatos de release. Não antecipar testes físicos antes dos portões automatizados/estáticos. Antes de modificar código:
1. confirmar o HEAD atual da branch;
2. recuperar e comparar os arquivos oficiais e exportados;
3. listar incompatibilidades reais;
4. escolher uma arquitetura única para hotspot, servidor e ViewModel;
5. registrar o plano de integração arquivo a arquivo.

**Situação desta atualização:** decisão HTTPS local obrigatório/offline-first incorporada ao roadmap. Esta alteração é documental; não implementa TLS, não executa build/testes e não altera o código do servidor. Nenhum teste físico foi realizado ou deve ser antecipado.
