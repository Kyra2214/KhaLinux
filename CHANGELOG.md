## [Não lançado] — RED-15: modo hotspot do sistema, upstream e heartbeat (implementado, não validado)

- Primeira compilação Android (workflow `45405105200`) encontrou dois erros Kotlin: import `weight` inacessível no `BlueScreen` e literal `\\t` inválido no parser HTTP. Corrigidos nos commits `36945e3` e `fd82386`; nova execução pendente.
- Workflow `45405903861` revelou testes legados chamando APIs removidas (`validateParticipantEvent`/`RedParticipantEvent`); `RedLabInputValidatorTest` foi reescrito para validar formulário sintético, cookies e permissões atuais. Retorno nullable de IPv4 foi corrigido. Workflow `45406501672` executa a suíte atualizada, build e lint.

- Adicionado `RedSystemHotspotController`: abre as Configurações de tethering do Android sem APIs ocultas, captura baseline de IPv4 Wi-Fi e identifica somente interface/endereço inequívoco.
- Adicionada observação do upstream padrão do aparelho A por `ConnectivityManager.registerDefaultNetworkCallback`, distinguindo capacidade INTERNET de acesso validado.
- ViewModel passou a compartilhar a inicialização do portal HTTPS entre `LocalOnlyHotspot` e o hotspot do sistema.
- Tela RED ganhou modo alternativo para ligar o hotspot do sistema sem upstream, conectar B e ativar dados móveis depois sem desligar o hotspot; o modo local-only permanece isolado.
- Painel e relatório mostram upstream validado em A sem afirmar que B navegou externamente.
- Páginas HTML do portal enviam heartbeat same-origin autenticado a cada 20 segundos enquanto visíveis; A mostra último contato local e registra evento no máximo uma vez por minuto para limitar volume.
- Seleção de interface do hotspot do sistema extraída para função pura com testes JVM para endereço novo, endereço do upstream, AP-named, ambiguidade e ausência de candidato.
- Validação do cookie do heartbeat extraída para função pura com cinco casos JVM, incluindo rejeição de token incorreto, ausência de cookie e duplicidade ambígua.
- Encerrar o portal não desliga o hotspot do sistema; essa ação permanece sob controle do Android.
- **Validação pendente:** descoberta de interface depende do padrão OEM; permanência da conexão B e encaminhamento externo exigem validação física na fase final.

## [Não lançado] — RED-07: primeira integração HTTPS local offline (parcial)

- Adicionado `RedLocalTlsManager`: CA e chave de servidor RSA geradas pelo Android Keystore; chaves privadas não são exportadas.
- Certificado do servidor emitido para o IPv4 atual com SAN IP, validade limitada e uso `serverAuth`.
- Adicionado endpoint HTTP de preparação isolado em `8080`, com página `/`, download `/ca.cer` (certificado público DER) e `/health`; não publica rotas do portal.
- `RedPortalServer` tornou-se HTTPS-only por `SSLServerSocket`, restrito à interface/IPv4 do laboratório, com TLS 1.2/1.3 e conjuntos modernos; não há fallback HTTP para o participante.
- ViewModel passou a preparar CA, iniciar a entrega pública e abrir o portal participante HTTPS em `8443`.
- Cookie de sessão recebe `Secure`; validação de origem acompanha o esquema HTTPS.
- Tela RED apresenta URL de preparação e impressão digital SHA-256 da CA.
- Página HTTP de preparação orienta a instalação manual da CA, oferece download do certificado público e repete o fingerprint para conferência independente.
- A registra separadamente o download da CA e o primeiro handshake TLS concluído pelo navegador; não presume confiança apenas pela instalação do certificado.
- Chave privada efêmera do certificado de servidor removida do Android Keystore nos caminhos de encerramento e falha; a chave privada da CA permanece protegida para sessões futuras.
- Relatórios distinguem download da CA pública e handshake TLS concluído; ausência de handshake não é apresentada como prova de ausência de instalação.
- Dependência Bouncy Castle adicionada para geração X.509.
- Implementação de código/documentação concluída; confiança da CA em navegadores reais e permissões web aguardam validação final.
- Escritas de evidência e observações de requisição serializadas no ViewModel para preservar sequência em callbacks concorrentes.
- Primeira validação final: host checks, auditoria estática, JNI PTY e smoke PRoot passaram no workflow `45405105200`; testes Android e APK Debug em execução.

## [Não lançado] — RED-14: estabilização documental atualizada

- Parcela documental de RED-14 concluída: notas preliminares, matriz de compatibilidade, checklist e registro de estabilização alinhados.
- Comparação registrada no snapshot `72f612deb08c425fdb1e1418bb9be7a98d1511ab`: branch 520 commits à frente de `main`, 0 atrás, 48 arquivos alterados (snapshot após 100% da implementação RED/BLUE). A contagem deve ser refeita antes do PR.
- Revisão direcionada documentada; revisão integral do diff permanece pendente.
- Release continua bloqueada: RED-07 depende de HTTPS confiável; build, testes, auditoria final e testes físicos não foram executados; APK, assinatura, SHA-256 e PR não foram produzidos.

## [Não lançado] — RED-14: preparação da entrega

- Notas preliminares de release e matriz de compatibilidade adicionadas.
- Checklist de estabilização registra portões de auditoria, testes, validação física, versionamento, assinatura, SHA-256 e PR.
- Nenhum APK, assinatura, hash ou PR foi criado; geração depende da validação final.
- Sem ZIP/checkpoints, conforme instrução do usuário.
- Documentação: docs/red-blue-release-notes.md, docs/red-blue-compatibility.md, docs/red-release-checklist.md e docs/red-branch-stabilization.md.

## [Não lançado] — RED-11: hardening de segurança e privacidade

- CSP por resposta com nonce aleatório; removidos handlers inline dos botões de cookies e removido unsafe-inline de script-src. A página de permissões web exige login de teste aceito no modo local.
- Mantidos headers de defesa: nosniff, no-store, Referrer-Policy, X-Frame-Options, Permissions-Policy, CORP e X-Permitted-Cross-Domain-Policies.
- Allowlist de evidência reduzida a Host, Accept, Content-Type e Referer path; User-Agent/Accept-Language não persistidos.
- Limitado o número de IPs acompanhados por rate limit de autenticação e login; headers com caracteres de controle são rejeitados.
- Manifest revisado: allowBackup=false, sem permissões de câmera/microfone/armazenamento para RED, serviços não exportados e cleartext de saída desabilitado.
- Riscos HTTP sem TLS e dependência de origem HTTPS confiável documentados. Auditoria/testes finais não executados.

## [Não lançado] — RED-12/RED-13: preparação da validação final

- Plano de build/lint/testes host/Android documentado sem executar comandos de validação.
- Matriz física revisada para a topologia atual: B somente navegador, login de teste, cookies e solicitações web separadas.
- Removidos da matriz física do B os cenários antigos de app participante, permissões nativas, SAF, prévia de câmera e medidor de microfone.
- Execução de testes, build, auditoria e validação física permanece adiada até a conclusão integral do roadmap.
- Documentação: docs/red-validation-plan.md e docs/red-physical-validation.md.

## [Não lançado] — RED-10: interface do operador e acessibilidade

- RED exibe estados reais de hotspot/servidor/interface/IPv4/participante e só mostra URL/QR após bind confirmado.
- Permissão do operador distingue solicitação pendente, concessão, recusa, revogação e recuperação.
- Removido aviso antigo que rotulava as páginas como simulação; roteiro B atualizado para cookies e permissões web individuais.
- Diagnóstico HTTP sem TLS, visibilidade limitada, requisições recentes e ações de recuperação permanecem no A.
- Estado de permissão usa `rememberSaveable`; ciclo de vida interrompe sessão em segundo plano; QR tem descrição acessível.
- RED/BLUE compartilham eventos sem duplicar evidências. Documentação: `docs/red-operator-ui.md`.
- Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-10: interface do operador e estados operacionais

- Painel A apresenta estados separados para permissão pendente, solicitação de hotspot, confirmação Android, descoberta de interface, inicialização do servidor, espera de participante, atividade HTTP, encerramento e falha.
- Botão de início é desabilitado durante solicitações pendentes; a confirmação só aparece após callback real.
- Adicionado aviso visível de HTTP sem TLS e limites do que o painel observa.
- Estados de permissão transitórios são preservados em recriação e resolvidos no retorno à Activity.
- Roteiro e textos atualizados para B navegador-only e solicitações web independentes.
- QR Code possui descrição acessível; erros mantêm instruções de recuperação.
- Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-09: histórico, relatório e exportação

- Schema de sessão versionado (v2) e migração automática de registros legados v1.
- Persistência limitada a 100 sessões e 300 eventos por sessão, com metadados e campos tipados.
- Mantidos fora da persistência corpos HTTP, valores de formulário, códigos, cookies, tokens, mídia, coordenadas e arquivos.
- Relatório Markdown inclui versão do app/Android, estado da sessão, contagens, linha do tempo, limitações e status de validação pendente. Valores de rota, origem e headers recebidos são escapados para não alterar a estrutura Markdown.
- Adicionada exclusão individual de sessão com confirmação; sessão ativa exige encerramento antes de excluir.
- Exportação continua pelo seletor de documentos; cópias exportadas permanecem sob controle do usuário.
- Nenhum teste/build/compilação foi executado.

## [Não lançado] — BLUE-01: análise defensiva das evidências RED

- Painel BLUE mantém a mesma sessão e IDs de eventos da perspectiva RED, usando o store ao vivo sem alterar a evidência original.
- Adicionado resumo de fatos observados com contagens derivadas de eventos recebidos, rejeitados, respostas, consentimentos e estados de permissões.
- Cada evento agora separa fato observado de interpretação defensiva.
- Interpretações específicas cobrem rejeição por token/origem, limite de tentativas e expiração; orientações não são apresentadas como detecções.
- Documentação BLUE alinhada à topologia A operador/B navegador-only.
- Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-08: segurança da sessão e ciclo de vida

- Token aleatório de 256 bits gerado por sessão e mantido somente em memória.
- Cookie HttpOnly/SameSite vinculado ao primeiro acesso do participante; token não é exposto em URL, HTML ou logs.
- POST de login, consentimento e permissões web exige cookie válido, IP vinculado e origem same-origin.
- Comparação de token em tempo constante, distinção de falhas e bloqueio temporário após cinco tentativas.
- Expiração por 30 minutos de inatividade e duração máxima de duas horas. Encerramento inesperado do portal libera também a reserva do hotspot.
- Respostas válidas renovam cookie; stop limpa token, participante vinculado, sockets e estado de tentativas.
- HTTP local permanece sem TLS; a proteção de sessão não fornece confidencialidade contra observação do tráfego.
- Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-07: permissões web do navegador (parcial)

- Endpoint `POST /browser-permission` valida apenas recurso e estado, sem aceitar mídia ou coordenadas.
- Após login, o portal oferece link para página com ações separadas de câmera, microfone e localização; cada solicitação exige clique próprio. Em contexto seguro, tracks são encerrados e coordenadas descartadas.
- Em origem HTTP insegura, APIs não são chamadas e A registra `UNAVAILABLE`; aceitar cookies ou concluir login não dispara solicitações.
- Eventos tipados são persistidos e exibidos em RED/BLUE.
- Bloqueio: o endereço atual é HTTP por IPv4 privado; prompts nativos exigem HTTPS confiável. Domínio/certificado ainda não definido.
- Documentação: `docs/red-browser-permissions.md`. Nenhum teste/build foi executado.

## [Não lançado] — RED-06: eventos, persistência e BLUE

- Requisições agora distinguem recebimento, rejeição, resposta enviada e falha interna, com timestamp de recebimento.
- Eventos persistem método, rota, status, bytes, IP remoto, resultado e allowlist de cabeçalhos.
- Cookie/Authorization, corpos HTTP e valores de formulário não são gravados; valores sintéticos permanecem efêmeros.
- SessionStore mantém até 100 sessões/300 eventos por sessão, com campos opcionais compatíveis com histórico anterior.
- RED e BLUE exibem respostas, rejeições, falhas, consentimento por categoria e permissões observadas.
- Evidências efêmeras são preservadas para revisão após parar a rede e limpas ao encerrar a sessão.
- Documentação: `docs/red-event-evidence.md`. Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-05: portal de navegador e aviso de cookies

- Banner adicionado a todas as respostas HTML do portal, com aceitar todos, recusar opcionais e configuração por categoria.
- Cookie de consentimento e flags opcionais só são gravados no navegador depois que o A confirma o POST de consentimento.
- Consentimento é validado e registrado no A em evento tipado, com categorias e sem valores dos cookies.
- Texto do banner descreve que as categorias opcionais armazenam somente preferências de demonstração, sem rastreadores de terceiros.
- Login permanece restrito a identificador/código do laboratório; páginas e redirecionamentos são locais.
- Aviso explícito de HTTP sem TLS adicionado ao portal e ao login.
- Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-04: servidor HTTP local vinculado à interface

- Bind exige IPv4 privado/compartilhado literal da interface Wi-Fi/AP identificada; sem fallback para wildcard ou interface arbitrária.
- Servidor monitora a interface e o IPv4 durante a execução e encerra quando a associação deixa de existir.
- Host, Content-Length, Content-Type, path, query, métodos e cabeçalhos duplicados/malformados são validados.
- Limites: header 8 KiB, body 4 KiB, resposta 128 KiB, 4 clientes, timeout de socket 5 s, path 512 caracteres.
- Requisições recebidas são observadas uma vez, inclusive rejeições; Cookie, Authorization e corpo não são registrados.
- CSP, nosniff, Referrer-Policy, X-Frame-Options e Permissions-Policy adicionados; portal avisa que HTTP local não tem TLS.
- Documentação: `docs/red-http-server.md`. Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-03: LocalOnlyHotspot e correlação da interface

- Controlador ganhou geração por solicitação, timeout de 30 s, cancelamento, tratamento de callback tardio e encerramento idempotente.
- Após `onStarted`, a interface AP é identificada por diferença de interfaces/IPv4 antes/depois da solicitação; sem candidato único, a reserva é encerrada e o portal não inicia.
- Android 11+ lê `SoftApConfiguration`; Android 9/10 mantêm fallback legado `WifiConfiguration`.
- SSID, passphrase e segurança vêm da configuração retornada; banda permanece desconhecida quando não há valor utilizável.
- Estado `DISCOVERING_INTERFACE` separado de hotspot pronto e portal ativo.
- Documentação: `docs/red-hotspot-lifecycle.md`. Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-02: permissões e capacidades do Android

- O aparelho A solicita ACCESS_FINE_LOCATION e ACCESS_COARSE_LOCATION somente ao iniciar o hotspot, com explicação de finalidade.
- O estado diferencia concessão precisa/aproximada, recusa, recusa permanente e revogação ao retornar à Activity.
- Recusa permanente/revogação direciona o operador às Configurações do aplicativo.
- O KhaLinux não consulta coordenadas nesse fluxo; o resultado do hotspot continua dependente do callback real do Android.
- B permanece navegador-only e não recebe permissões do Manifest do KhaLinux.
- Matriz registrada em `docs/red-permissions.md`. Nenhum teste/build/compilação foi executado.

## [Não lançado] — RED-01: modelo de domínio e estados reais

- Introduzidos modelos tipados para configuração do portal, endpoint iniciado e origem HTTP observada.
- Consentimento de cookies agora usa modelo tipado com escolha e categorias opcionais; os campos são persistidos no evento da sessão.
- Introduzidos tipos explícitos para recurso/estado de permissões web, sem gerar eventos até existir resultado observado do navegador.
- Persistência de eventos ampliada com campos opcionais, mantendo leitura de sessões antigas.
- Painel e relatório usam endpoint real, origem HTTP observada e consentimento por categoria.
- Documentação: `docs/red-domain-model.md`. Nenhum teste, build ou compilação foi executado.

## [Não lançado] — RED-00: baseline e arquitetura de dois aparelhos

- Inventário de código e documentação registrado em `docs/red-baseline-inventory.md`.
- RED-00 concluído em documentação/inspeção; próximo submódulo é RED-01.
- Progresso do roadmap RED/BLUE: 1/16 submódulos (6,25%); implementações parciais não contam como concluídas.
- Nenhum teste, build, compilação ou validação executado; atividades acumuladas para a fase final.
- Execução diretamente na branch `recovery/red-portal-integration`; sem ZIP/checkpoints.

## [Não lançado] — RED/BLUE: participante somente pelo navegador e consentimento de cookies

- Definida a topologia: A opera o laboratório e concentra o painel; B conecta ao Wi-Fi, abre o portal, faz login de teste e usa somente o navegador.
- Removido do fluxo do app o modo participante nativo, seu endpoint de eventos, permissões de câmera/microfone e dependências CameraX.
- Adicionado aviso de cookies com aceitar todos, recusar opcionais e configuração por categoria.
- Adicionado endpoint local `POST /consent`, validação estrita das categorias e registro da escolha na linha do tempo do aparelho A.
- O botão de cookies não concede permissões de recursos. Permissões web dependem de origem segura e de solicitação explícita do navegador.
- Nenhum teste, build ou compilação foi executado; validação permanece para a etapa final.

## [1.0.0-rc.7] — 2026-11-23

- Candidata pública baseada na `main`, com build Android `release` ARM64 assinado e minificado; o CI verifica a assinatura com `apksigner`.
- Inclui o laboratório RED de Wi-Fi/portal local, modo participante com permissões nativas e a análise defensiva BLUE de eventos observados, além de histórico e relatórios locais.
- `versionCode=9`; o código `8` segue reservado ao APK automático da `main`.
- Testes unitários e verificações de host passaram no CI; validação em aparelhos físicos e compatibilidade com fabricantes/versões Android continuam pendentes. Esta candidata não deve ser tratada como versão estável.

## [Não lançado] — RED: implementação concluída, validação pendente

- RED-0 a RED-12: 100% dos submódulos de implementação concluídos em código/documentação.
- RED-13: validação final ainda pendente; o projeto não deve ser declarado concluído até a execução e resolução dos testes acumulados.
- Testes físicos com os dois aparelhos permanecem como última etapa da validação.
- Nenhum ZIP/checkpoint gerado; alterações aplicadas diretamente na branch `plan/area-red-wifi-publico`.

## [Não lançado] — RED-12: fechamento do ciclo de implementação

### Confirmado na implementação existente
- Encerramento do servidor HTTP e do hotspot pode ser chamado repetidamente sem exigir recursos ativos.
- Parar a rede preserva o token temporário dentro da sessão para permitir reinício; encerrar/interromper a sessão limpa o token e os dados efêmeros.
- Documentação de operação, instrutor, guia do usuário, roadmap e retomada foi alinhada ao escopo atual.
- Nenhum ZIP/checkpoint é gerado; alterações são aplicadas diretamente na branch indicada.

### Estado
- Implementação: 93% (RED-0 a RED-12 em código/documentação; 13/14 submódulos).
- RED-13 é a validação final e permanece pendente.
- Nenhum build, teste ou validação foi executado nesta retomada.

## [Não lançado] — RED-11: documentação e roteiro de aula

### Atualizado
- Guia do usuário corrigido para descrever o POST HTTP local com campos sintéticos e sua visualização temporária no painel.
- Guia do instrutor e roteiro de vídeo cobrem hotspot, conexão do aparelho B, login local, acesso direto, links, permissões reais e resultados observados.
- Documentação mantém limitações reais do Android e do navegador e não promete abertura automática do portal ou lista universal de clientes.

### Validação
- Revisão documental; nenhum teste/build executado.

## [Não lançado] — RED-10: integração de evidências na Área BLUE

### Implementado / complementado
- BLUE apresenta requisições HTTP com horário, bytes, tipo de conteúdo, origem e cabeçalhos permitidos.
- BLUE resume o evento mais recente de câmera, microfone, localização e seletor de arquivos.
- Mantida a interpretação defensiva por evento, separando fato observado e orientação.
- Evidências ao vivo e histórico continuam compartilhados a partir da sessão RED.

### Validação
- Nenhum build/teste executado; alternância e atualização ao vivo serão verificadas em RED-13.

## [Não lançado] — RED-9: histórico e relatórios locais

### Confirmado na implementação existente
- Histórico persiste sessões e eventos tipados, com limite de 100 sessões e 300 eventos por sessão.
- Recuperação marca sessões abertas como interrompidas e não reativa a rede.
- Relatório Markdown contém cenário, modo, contagens, metadados HTTP e linha do tempo.
- Valores dos formulários, códigos, tokens, cookies, arquivos, áudio, vídeo e coordenadas não são incluídos no relatório.
- Exportação manual e exclusão do histórico estão disponíveis na interface.

### Validação
- Confirmação por inspeção estática; nenhum teste/build executado.
- Validação funcional reservada a RED-13.

## [Não lançado] — RED-8: observabilidade de permissões e recursos

### Implementado
- Painel RED ganhou seção dedicada com o evento mais recente por câmera, microfone, localização e seletor de arquivos.
- Estados são derivados da linha do tempo real da sessão; sem evento, a interface informa que nenhum recurso foi solicitado.
- Requisições HTTP e valores sintéticos continuam exibidos em áreas próprias, com limpeza de dados temporários.

### Validação
- Nenhum build ou teste executado; validação funcional fica para RED-13.

## [Não lançado] — RED-7: modo participante e permissões Android

### Implementado / complementado
- Modo participante usa permissões reais no aparelho B.
- Câmera apresenta prévia local; microfone calcula nível e descarta amostras; localização permanece exibida no próprio aparelho; arquivos usam seletor SAF sem leitura/envio.
- O operador recebe somente o tipo e o estado da ação por endpoint local autenticado com token temporário.
- Ao retornar de Configurações, o estado das permissões é atualizado; prévia/medição são interrompidas quando o acesso foi revogado.

### Validação
- Inspeção estática e implementação de tratamento de revogação; nenhum build/teste executado.
- Teste real de permissões nos dois aparelhos fica reservado a RED-13.

## [Não lançado] — RED-6: navegação local e redirecionamentos

### Confirmado na implementação existente
- Modo de acesso direto sem login e páginas locais interligadas.
- Destinos locais de notícias/ofertas e página de aviso.
- Rota de redirecionamento HTTP 302 com destino restrito ao próprio portal.
- Painel recebe a sequência de requisições com método, rota, status, bytes e cabeçalhos permitidos; Referer é limitado ao caminho.

### Validação
- Confirmação por inspeção estática; nenhum teste ou build foi executado.
- Validação funcional fica reservada para RED-13.

## [Não lançado] — RED-5: formulário local e dados sintéticos recebidos

### Confirmado na implementação existente
- O portal envia formulário por POST HTTP local para `/login`.
- O backend permite somente os campos `demo_id` e `demo_code` e valida seus formatos de demonstração.
- O painel exibe os valores aceitos apenas durante a sessão ativa e oferece limpeza manual.
- O SessionStore e o relatório persistem metadados/eventos, não os valores do formulário.

### Validação
- Confirmação feita por inspeção estática dos arquivos; nenhum teste ou build foi executado.
- Exercício funcional fica reservado para RED-13.

## [Não lançado] — RED-3: servidor HTTP local configurável

### Implementado
- Porta do servidor configurável por parâmetro, mantendo 8080 como padrão.
- Validação da porta configurada no intervalo 1024–65535.
- Bind permanece limitado ao IPv4 da interface Wi-Fi/AP do laboratório, sem exposição por `0.0.0.0`.
- Mantidos os limites de requisição, rotas locais permitidas, timeouts e encerramento do servidor.

### Validação
- Nenhum build, teste ou validação foi executado nesta etapa, conforme o Prompt Mestre 3.0.
- Compilação e comportamento real ficam acumulados para a fase final RED-13.

## [Não lançado] — RED-13: validação automatizada

### Aprovado no GitHub Actions
- Verificação de configuração RootFS e sintaxe shell.
- Auditoria estática de segurança, após aprovação documentada das permissões necessárias.
- Teste JNI PTY no host.
- Smoke test PRoot/ARM64.
- Testes unitários dos módulos Android runtime e app.
- Compilação do APK debug e upload do artefato de inspeção.

Workflow: https://github.com/Kyra2214/KhaLinux/actions/runs/45099843828  
Commit validado: cf1c16d68d6706f912ee856d3753efd935077a26.

### Pendente
- Teste físico com os dois aparelhos Android.
- Verificação de LocalOnlyHotspot, portal 8080, formulários, navegação, permissões reais, relay, alternância RED/BLUE e compatibilidade por fabricante/versão.
- Projeto permanece sem declaração de 100% até concluir os testes físicos.

## [Não lançado] — RED-12: fechamento da implementação

### Implementado
- Ciclo de encerramento do hotspot e servidor HTTP tornado idempotente.
- Cancelamento de solicitação de hotspot em andamento tratado.
- Token temporário preservado ao parar/reiniciar rede dentro da mesma sessão e limpo ao encerrar/interromper.
- Eventos duplicados de solicitação/inicialização removidos.
- Integração RED/BLUE, documentação e retomada consolidadas.
- Lista final de validação congelada em docs/red-implementation-freeze.md.
- ZIP/checkpoints omitidos conforme instrução do usuário.

### Estado
- Implementação: 93% (13/14 submódulos).
- Única fase restante: RED-13 — validação final.
- Nenhum teste/build executado durante a implementação.

## [Não lançado] — RED-11: tutorial e documentação de aula

### Implementado
- Roteiro rápido de dois aparelhos incorporado à tela RED.
- Guia completo do instrutor em docs/red-instructor-guide.md.
- Preparação do aparelho A e B, cenários de login/acesso direto, permissões, pareamento e envio de estado documentados.
- Roteiro de vídeo focado em comportamento e resultados, sem código-fonte.
- Troubleshooting de hotspot, endereço, porta 8080, navegador e permissões.
- Documentação de RED, BLUE e guia do usuário atualizada com referências cruzadas.

### Limites
- Nenhum teste/build foi executado.

## [Não lançado] — RED-10: integração pedagógica RED ↔ BLUE

### Implementado
- Área BLUE criada como módulo Compose próprio e adicionada ao menu Mais.
- BlueViewModel lê as mesmas sessões/eventos persistidos do RED.
- RedLiveEvidenceStore compartilha em memória requisições e campos sintéticos da sessão ativa.
- Tela BLUE mostra evidências e interpretação defensiva por evento para hotspot, portal, login, acesso direto, links, redirecionamentos, HTTP e permissões.
- Alternância RED → BLUE preserva a sessão ativa; retorno BLUE → RED restaura a operação.
- Documentação específica em docs/blue-area.md e guia do usuário atualizado.

### Limites
- BLUE não realiza captura geral de rede nem inspeção HTTPS; mostra somente eventos observados no laboratório.
- Nenhum teste/build foi executado.

## [Não lançado] — RED-9: histórico e relatórios

### Implementado
- Modo do portal gravado em cada sessão, com leitura retrocompatível para sessões antigas.
- Relatório ampliado com cenário, modo, etapas, contagem de requisições/eventos de permissão e linha do tempo sequenciada.
- Eventos HTTP incluem método, rota, status, bytes e origem.
- Histórico permanece limitado a 100 sessões e 300 eventos por sessão.
- Recuperação marca sessões incompletas como interrompidas sem reativar hotspot ou servidor.
- Exportação e exclusão manual continuam disponíveis.
- Valores sintéticos, tokens, cookies, arquivos, áudio, vídeo e coordenadas não são incluídos no relatório.

### Limites
- Nenhum teste/build foi executado.

## [Não lançado] — RED-8: painel de observabilidade

### Implementado
- Painel apresenta estado do hotspot, servidor e atividade HTTP do participante.
- Exibição da interface de bind e porta efetiva.
- Lista de requisições com timestamp, método, rota, status, bytes, content-type e endereço remoto.
- Cabeçalhos de baixo risco em allowlist; Cookie e Authorization não são capturados.
- Linha do tempo sequenciada com eventos HTTP, navegação, redirecionamento, formulários e permissões.
- Valores sintéticos temporários exibidos em memória e botão para removê-los do painel.
- Respostas rejeitadas e erros permanecem diferenciados de sucesso.

### Limites
- Observações de requisição e cabeçalhos são mantidos apenas em memória; relatório persiste metadados de eventos.
- Nenhum teste/build foi executado.

## [Não lançado] — RED-7: modo participante Android

### Implementado
- Modo participante acessível na própria Área RED, para uso no segundo aparelho Android.
- Solicitações reais de permissões de câmera, microfone e localização no aparelho participante.
- Prévia de câmera local via CameraX.
- Medidor de nível de microfone via AudioRecord, sem gravação ou transmissão de áudio.
- Consulta de localização apresentada somente no aparelho participante.
- Seletor de arquivos do Android (SAF), sem ler nome/conteúdo.
- Token temporário por sessão e endpoint local que recebe somente tipo/estado da demonstração; nenhum conteúdo de recurso é enviado.
- Painel RED registra pedido e resultado dos recursos.

### Limites
- Dependências CameraX adicionadas ao módulo Android.
- A compatibilidade real por fabricante/versão e o ciclo de revogação permanecem para validação final.
- Nenhum teste/build foi executado.

## [Não lançado] — RED-2 a RED-6: hotspot, servidor, portal e navegação

### Implementado
- Hotspot Android com estado tipado e exibição dos parâmetros que o sistema realmente disponibiliza.
- Identificação indicativa de interface Wi-Fi/AP e IPv4; atividade do participante é confirmada por requisição HTTP, sem varredura.
- Servidor HTTP local fixo na porta 8080, bind a IPv4 Wi-Fi/AP identificado, limites de cabeçalho/corpo/conexões e timeouts.
- Painel com método, rota, status, tipo de conteúdo, bytes e endereço remoto da requisição.
- Portal fictício natural com seletor entre login local e acesso direto.
- Páginas locais, termos, conteúdo, destinos e redirecionamento 302.
- Formulário POST recebe somente `demo_id` (padrão `aluno-...`) e `demo_code` (padrão `LAB-0000`); demais campos/formato são rejeitados.
- Valores sintéticos válidos aparecem apenas em memória no painel da sessão; não são persistidos no histórico nem no relatório.

### Limitações e pendências
- Android não oferece lista universal de clientes do LocalOnlyHotspot; a conexão do participante é confirmada pelo primeiro acesso HTTP.
- Modo participante Android e permissões nativas reais continuam pendentes.
- Nenhum teste/build/validação foi executado; tudo fica para RED-13.

## [Não lançado] — RED-1: fundação e catálogo

### Implementado
- Modelo de estados de rede separado do estado da sessão.
- Tipos de eventos ampliados para hotspot, portal, requisições HTTP, navegação, redirecionamentos, formulários e permissões.
- Modelo de observação HTTP com método, rota, status, tipo, tamanho e endereço remoto opcional.
- Linha do tempo com sequência e metadados HTTP; armazenamento retrocompatível com sessões anteriores.
- Estado de UI preparado para modo do portal, observações de requisição e ciclo de rede.
- Catálogo da Operação 01 revisado com autenticação local fictícia, acesso direto sem login, navegação e permissões no aparelho participante.

### Limites
- Valores de formulários não fazem parte do modelo persistente.
- Esta entrega não altera ainda o servidor HTTP nem implementa os fluxos de portal; esses requisitos pertencem aos próximos submódulos.
- Nenhum teste, build ou validação foi executado. A validação permanece reservada à fase final.

# Changelog

## [Não lançado] — Área RED (roadmap revisado 2026-11-23)

### Planejado
- Revisão completa da Operação 01 para laboratório pessoal de dois aparelhos Android: aparelho A como operador/hotspot/servidor/painel e aparelho B como participante.
- Portal HTTP local na porta 8080 com dois cenários: autenticação local fictícia e acesso direto sem login.
- Navegação por links locais, redirecionamentos e registro de requisições; visualização temporária no painel dos campos sintéticos efetivamente recebidos.
- Modo participante Android para permissões reais solicitadas no próprio aparelho B, respeitando limitações de contexto seguro do navegador.
- Integração pedagógica RED/BLUE com o mesmo cenário e evidências, perspectivas separadas.
- Diretriz visual: experiência natural no aparelho participante; observabilidade e identificação do laboratório no painel do instrutor; vídeos mostram comportamento e resultados, não código.

### Estado honesto
- Este commit atualiza o planejamento e a documentação, não implementa os novos fluxos. O formulário atual continua enviando apenas um evento fixo e as permissões continuam demonstrativas. Nenhum teste/build foi executado; validação permanece reservada ao final.

## [Não lançado] — Área RED (implementação inicial)

### Adicionado
- Entrada **RED** na navegação principal, com catálogo versionado, filtros, ficha de operação, tutorial disponível antes da execução, progresso e histórico.
- Motor local de sessões RED com eventos tipados, persistência isolada, limite de 300 eventos por sessão/100 sessões e recuperação de sessões interrompidas.
- Operação 01 **Nunca Conecte à Wi-Fi Pública**: solicitação de hotspot local pelo Android, portal HTTP vinculado à interface Wi-Fi do laboratório, endereço manual, QR Code, formulário sintético, fluxo genérico de rede social, demonstrações de permissão (localização/câmera/arquivos) sem acesso real, painel de eventos e relatório Markdown copiável/exportável.
- Catálogo inicial em `distribution/red/operations.json`; documentação em `docs/red-area.md` e `docs/red-operation-01.md`.

### Segurança e limitações
- Portal identificado como simulação; campos de demonstração não são serializados. A página envia somente `demo=completed`; o servidor descarta o corpo e registra apenas o evento.
- Sem login real, coleta de credenciais, tokens, cookies, interceptação ou varredura de dispositivos.
- Hotspot, permissões e endereço de rede dependem do Android/fabricante. A abertura automática do portal não é garantida.
- Nenhum teste, build ou teste físico executado durante esta implementação. Toda validação da Área RED está reservada para a fase final.

## [Não lançado] — CHECKPOINT 047

### Corrigido
- **`/proc` agora é montado no PRoot** (ADR-0005 revisada): `AndroidBindMountPolicy` liga `/proc` do Android; `/sys` continua fora. Remove a limitação de `/proc` de chkrootkit, lynis, rkhunter e iproute2 (catálogo, toolkit, docs, tutor) e permite `ps`/`top`/`ss` e scripts de pós-instalação do dpkg. As limitações de privilégio (sem root) e "não verificado em aparelho" permanecem, pois só validação real as remove. Testes atualizados; não executados.
- **`dpkg: error creating new backup file '/var/lib/dpkg/status-old': Permission denied`** ao instalar pacotes `debian-apt` (ex.: binwalk; `apt-get install` saía com código 100 e o dpkg com 2). Causa: o dpkg cria o backup com `link()` (hard link), que o SELinux do Android nega em `filesDir` (EACCES). `ProotConfig.commandFor` agora passa `--link2symlink` ao PRoot, que emula hard links com symlinks. Não validado em aparelho ainda; após atualizar, reinstale o pacote que falhou (um `dpkg --configure -a` pode ser necessário se o dpkg ficou em estado parcial).
- **`Permission denied` (exit 126) ao executar o PRoot** (confirmado em aparelho: Terminal, Pacotes e Ferramentas falhavam por isso): com `targetSdk` ≥ 29 o SELinux bloqueia `execve` de arquivos em `filesDir`. `targetSdk`/`minSdk` passam a 28 (mesmo caminho do Termux; distribuição por APK fora da Play Store). `enableEdgeToEdge()` + `adjustResize` mantêm os insets corretos.
- Ferramentas: botões "Favoritar" etc. em duas linhas de largura igual (não quebram mais o texto).

## CHECKPOINT 046

### Corrigido
- **RootFS sem `/bin/bash` após instalar** ("Falha na instalação do PRoot: RootFS não contém /bin/bash"): no
  commons-compress 1.27.1 `TarArchiveEntry.isFile()` também é `true` para links simbólicos, então o extrator gravava
  `/bin`, `/lib`, `/sbin` (usrmerge do Debian) como arquivos vazios. A ordem do `when` agora trata links/diretórios
  antes de `isFile`.
- `RootfsValidator`: a instalação só é considerada pronta se `/bin/bash` resolver de fato. Um RootFS quebrado de
  versões anteriores é detectado e reinstalado automaticamente.

### Alterado
- Interface redesenhada: navegação inferior (Terminal, Pacotes, Ferramentas, Labs, Mais), terminal em tela cheia,
  barra de comando com Enter para enviar, abas em chips, folhas de Ajustes e Atalhos (favoritos, histórico,
  scripts), cartão de instalação com etapas e erros legíveis.

## [Não lançado]

### Validação final (parcial)
- Compilado todo o Kotlin do runtime e executada a suíte: 166 testes, 0 falhas (compilador do Gradle 2.0.21 em modo 1.9 + shim de `kotlin.test`; ver `docs/final-validation.md`). `audit-security.py`: 0 erros (controle negativo detectou violações). PTY nativo OK.
- Corrigido: `TutorTopic.DOC` recusava `ADR-*.md` (maiúsculas) e aceitava `..`; agora aceita só caminhos sob `docs/` sem `..` (+ teste de regressão). Corrigido `LabsDataTest.docsAndFixturesExist` (serviço sem fixture). Auditoria exige `--bind` loopback em `http.server`.
- Pendentes: `:android:app`, Room, build release/R8, workflows, aparelho físico, medições. Nenhum item marcado como aprovado sem execução.

### 9.4 — Notas de versão
- `RELEASE_NOTES.md` da **1.0.0-rc.1** (candidata, não estável): recursos, requisitos, estado de validação honesto, limitações, verificação do APK.
- `versionName` padrão `1.0.0-rc.1` e `versionCode` 2 (sobrescritíveis por `-P`). A 1.0.0 estável fica condicionada à validação final e a testes em aparelho.
- Implementação do Roadmap: 47/47 em código. Validação registrada separadamente (`docs/final-validation.md`).

### 9.3 — APK release e assinatura
- `android/app/build.gradle.kts`: build type `release` (R8 + shrinkResources, `-PkhalinuxMinify=false` desliga), `abiFilters arm64-v8a`, `versionName/versionCode` por propriedade Gradle (padrão inalterado: `0.1.0-phase3`/1) e `signingConfig` por variáveis `KHALINUX_KEYSTORE_PATH/_PASSWORD/KEY_ALIAS/KEY_PASSWORD` (sem segredo no repositório; sem variáveis → APK não assinado).
- `android/app/proguard-rules.pro` (NativePty, commons-compress, dontwarn de dependências opcionais, números de linha).
- `.github/workflows/release-apk.yml`: manual, entradas via env, tag imutável, exige secrets, auditoria estática, `apksigner verify`, SHA-256, publica release.
- `docs/release.md`: geração de chave, secrets, processo e verificação. **Nada foi compilado/executado**; regras do R8 não exercitadas. O workflow lê `RELEASE_NOTES.md` (criado em 9.4).

### 9.2 — Documentação de instalação e uso
- `docs/install-guide.md` (requisitos, APK, instalação, primeira abertura, conferência, atualização, desinstalação, problemas comuns, limitações) e `docs/user-guide.md` (todas as telas e fluxos; 12 laboratórios/51 exercícios conferidos no `labs.json`).
- README: status, instalação e estado atualizados (estavam desatualizados na Fase 3/7).
- Conteúdo descreve o código; nada validado em aparelho. `install-guide` referencia `docs/release.md` (criado em 9.3).

### 9.1 — Auditoria de segurança estática
- ROADMAP: Fase 9 subdividida em 9.1–9.4 (total 47 submódulos; percentual 91% antes do 9.1, 94% depois).
- `distribution/scripts/audit-security.py` (stdlib, sem rede/escrita): manifesto, segredos, APIs proibidas, `ProcessBuilder` centralizado, downloads HTTPS+SHA-256, escopo loopback dos laboratórios, shell e workflows. Novo passo no job `host-validation` do CI.
- Manifesto: `android:usesCleartextTraffic="false"` explícito.
- `docs/security-audit.md`: regras, revisão manual (download, extração, PRoot, binds, comandos apt, arquivos, toolkit, laboratórios), riscos aceitos e pendências.
- **Script não executado** (política: verificações só na validação final); revisão de `native_pty.c` linha a linha transferida para a validação final.

### 6.8 — UI, asset e validação da Fase 6
- App: asset `labs.json` + `fixtures/` (`assets.srcDir("../../distribution/labs")`), `LabsJson`, `KhaLinuxRuntimeService.labs()` (progresso em `runtime/labs/progress.tsv`; copia as fixtures para `/opt/khalinux-labs` no RootFS), `TerminalRuntimeGateway.labs()`, `LabsViewModel`, `LabsScreen` (trilhas, bloqueio por pré-requisito, enunciado, dicas com custo, resposta, pontos, reiniciar laboratório/tudo) e botão "Laboratórios". `LabEngine.catalog` e `LabProgressService.engine` agora são públicos.
- Testes escritos e **não executados**: `LabsDataTest`, `LabProgressTest`. `docs/phase6-validation.md` registra o que foi e o que não foi verificado. Nada de Kotlin compilado (sem `kotlinc`/rede); `:android:app` só no CI.
- Serviços dos laboratórios continuam sendo iniciados manualmente no terminal.

### 6.7 — Laboratórios de forense (conteúdo)
- `labs.json` (`2026.11.22-5`): `forense-hashes` (iniciante, 4 exercícios), `forense-assinaturas` (intermediário, 4) e `forense-logs` (avançado, 4). Total: 12 laboratórios, 51 exercícios, nas 4 trilhas.
- `distribution/labs/fixtures/forense/gerar_evidencias.py`: gera evidências determinísticas (cópias de texto, PNG com comentário disfarçado de .pdf, log de acesso com IPs RFC 5737). Execução repetida produz arquivos idênticos (conferido).
- Respostas conferidas no host com `sha256sum`, `od`, `wc`, `grep` e `awk`; CRCs do PNG validados. Os laboratórios usam só coreutils/grep/python do RootFS base (sem `file`, `strings` ou `xxd`, que não estão na base); ferramentas forenses do toolkit (exiftool, binwalk, sleuthkit, hashdeep) não são exigidas porque não pude executá-las aqui.
- Parser Kotlin não executado (sem `kotlinc`). Sem testes (política por fase).

### 6.6 — Laboratórios Web
- `labs.json` (`2026.11.22-4`): `web-http-basico` (iniciante, 4 exercícios), `web-reflexao` (intermediário, 3) e `web-sqli-local` (avançado, 5). Total: 9 laboratórios, 39 exercícios.
- Fixtures em `distribution/labs/fixtures/web/` (Python stdlib; `HTTPServer` com endereço fixo em 127.0.0.1; porta 1024–65535): servidor didático, busca que reflete entrada sem escape e login de brinquedo com SQL concatenado (SQLite em memória, sem disco). Vulnerabilidades propositais, só locais e marcadas no código.
- Serviços apontam para `/opt/khalinux-labs/web/*.py` no RootFS. A cópia das fixtures para o RootFS fica para 6.8 (asset/instalação); até lá é manual. Suposição registrada.
- Conferido no host: os três servidores foram executados e todas as respostas de exercício foram obtidas com `curl`. Parser Kotlin não executado (sem `kotlinc`). Sem testes (política por fase). `sqlmap`/`wfuzz`/`nmap` não são usados.

### 6.5 — Laboratórios de rede (conteúdo)
- `labs.json` (`2026.11.22-3`): `rede-loopback` (iniciante, 4 exercícios), `rede-sockets` (intermediário, 5; serviço `http-local` = `python3 -m http.server 8080 --bind 127.0.0.1`, python3 já está no RootFS base) e `rede-dns-portas` (avançado, 5; conceitual, sem rede). Total do catálogo: 6 laboratórios, 27 exercícios.
- Só loopback; `nmap` não é usado (exigiria confirmação de escopo). Ferramentas exigidas existem no `tools.json` (conferido em Python).
- Conferido no host: 404 do `http.server`. Não conferidos aqui: `ss`/`ping` (ausentes no ambiente; respostas vêm de conhecimento do manual) e a leitura pelo parser Kotlin (sem `kotlinc`). Sem testes (política por fase).

### 6.4 — Laboratórios Linux (conteúdo)
- `distribution/labs/labs.json` (`2026.11.22-2`): `linux-basico` (iniciante, 4 exercícios), `linux-permissoes` (intermediário, 4; exige o anterior) e `linux-texto-processos` (avançado, 5; exige o anterior). Sem serviços e sem rede; sem ferramentas do toolkit (só coreutils do RootFS).
- Respostas só como SHA-256 da resposta normalizada; as respostas foram conferidas executando os comandos no host (wc, aritmética, sort/uniq, tr/rev, cut, awk, umask).
- Docs em `docs/labs/<id>.md`. O asset `labs.json` no app entra em 6.8.
- Sem `kotlinc` nesta sessão: o parser Kotlin não leu o arquivo (validei os limites do modelo em Python). Nenhum teste executado ou criado (política por fase).

### 6.3 — Pontuação e progresso persistido
- `LabProgressStore`: arquivo TAB com gravação atômica (mesmo padrão do `AuthorizationStore`) guardando `ExerciseState` por laboratório/exercício e laboratórios concluídos; linhas inválidas ignoradas; `resetLab` e `resetAll`.
- `LabProgress` (total de pontos geral e por laboratório) e `LabProgressService` (abre sessões com `LabEngine.open(..., restored)`, grava após cada `submit` e dica revelada, lista laboratórios disponíveis).
- Não compilado nesta sessão (sem `kotlinc` e sem rede); nenhum teste executado ou criado (política por fase).

### 6.2 — Motor de exercícios
- `LabEngine` (abre sessões respeitando pré-requisitos), `LabSession` (verificação por SHA-256 em tempo constante, dicas em ordem, estado e snapshot), `LabScoring` (base por dificuldade − dicas − tentativas além de 3, piso de 25%), `SubmitResult`, `ExerciseState`, `LabLockedException`.
- Estado em memória; restauração prevista para 6.3. Código compilado no host; nenhum teste executado ou criado (política por fase).

### 6.1 — Modelo de laboratório/exercício
- ROADMAP: Fase 6 subdividida em 6.1–6.8 (total passa a 33 submódulos; percentual recalculado: 70%).
- `LabDescriptor`/`LabExercise`/`LabService`/`LabHint`, `AnswerHasher` (SHA-256 da resposta normalizada), `LabCatalog` (pré-requisitos sem ciclos, ferramentas existentes no toolkit, `available(completed)`) e `LabCatalogParser` (campos desconhecidos rejeitados).
- Serviços de laboratório só escutam em loopback e portas 1024–65535; o modelo não tem alvo externo.
- `distribution/labs/labs.json` (vazio; conteúdo em 6.4–6.7) e `docs/labs.md`.
- Código compilado no host; nenhum teste executado ou criado (política por fase).

### 5.6 — Wiring do toolkit e validação da Fase 5
- Runtime: `ToolkitService` (status, verificação no RootFS com registro persistido, execução protegida pelo `ToolkitGuard`) e `VerificationStore`.
- App: asset `tools.json`, `ToolkitJson`, `KhaLinuxRuntimeService.toolkit()`, gateway, `ToolkitViewModel`, `ToolkitScreen` e botão "Ferramentas" (confirmação de escopo, verificar, revogar). **Não compilado aqui** (CI).
- Testes: `ToolkitServiceTest` e `ToolkitDataTest` (valida `catalog.json`/`tools.json` reais). Suíte do runtime executada pela primeira vez neste ambiente: 88 testes aprovados (após corrigir os dados de `DependencyResolverTest`, que usavam nomes de pacote de 1 letra inválidos).
- `docs/phase5-validation.md` registra o que foi e o que não foi validado. Versões do catálogo seguem sem conferência no snapshot APT fixado.

### 5.5 — Auditoria
- Catálogo (`2026.11.22-5`): `lynis` 3.0.8-1.1, `chkrootkit` 0.57-2 e `rkhunter` 1.4.6-11 (categoria auditoria, `known-limited`: sem `/proc` e sem acesso ao kernel, ADR-0005).
- Toolkit: descritores e docs para as três ferramentas (auditam só o RootFS, sem confirmação de escopo).
- Ferramentas de ataque a credenciais (john, hydra) não foram adicionadas: fora do escopo de auditoria definido e não pesquisadas.
- Versões lidas em páginas do Debian; conferir no snapshot APT fixado em 5.6. Nenhum teste executado ou criado; nada compilado.

### 5.4 — Forense
- Catálogo (`2026.11.22-4`): `sleuthkit` 4.11.1+dfsg-1, `binwalk` 2.3.4+dfsg1-1, `hashdeep` 4.4-7.1 e `libimage-exiftool-perl` 12.57+dfsg-1 (categoria forense, `unverified`, limitações registradas). Sem tamanhos informados (não confirmados).
- Toolkit: descritores e docs para sleuthkit, binwalk, hashdeep e exiftool (análise local; sem confirmação de escopo, com aviso de uso apenas em evidências próprias/autorizadas).
- `foremost` não foi adicionado: disponibilidade no bookworm não confirmada nesta pesquisa.
- binwalk: o rastreador de segurança do Debian lista CVE de extração sem correção para o bookworm; a doc recomenda evitar `-e` em arquivos não confiáveis.
- Versões lidas em páginas do Debian; conferir no snapshot APT fixado em 5.6. Nenhum teste executado ou criado; nada compilado.

### 5.3 — Web
- Catálogo (`2026.11.22-3`): `whatweb` 0.5.5-1, `sqlmap` 1.7.2-1 e `wfuzz` 3.1.0-2 (categoria web, `unverified`, com limitações registradas; dependências Ruby/Python resolvidas pelo APT). Tamanho informado apenas para o whatweb.
- Toolkit: descritores (`requires_authorization`) e docs para whatweb, sqlmap e wfuzz; `curl` (5.1) continua sendo a ferramenta Web sem confirmação de escopo.
- `nikto` não foi adicionado: no bookworm consta em `non-free` (1:2.1.5-3.1) e o RootFS base não habilita esse componente (suposição a confirmar em 5.6).
- Versões lidas em páginas de pacotes do Debian (bookworm); devem ser conferidas no snapshot APT fixado (`20251122T000000Z`) em 5.6.
- Nenhum teste executado ou criado; nada compilado.

### 5.2 — Reconhecimento e rede
- Catálogo (`2026.11.22-2`): `nmap` 7.93+dfsg1-1 (known-limited sem root), `whois` 5.5.17 e `traceroute` 1:2.1.2-1, com limitações sem root; tamanhos aproximados do pacote principal.
- Toolkit: descritores e docs para nmap (exige confirmação de escopo), whois e traceroute.
- Versões lidas em packages.debian.org (bookworm); a de `traceroute` veio da página "stable" e deve ser reconferida. Todas precisam ser conferidas no snapshot APT fixado (`20251122T000000Z`) em 5.6.
- Nenhum teste executado ou criado; nada compilado.

### 5.1 — Infra do toolkit
- `ToolDescriptor`/`ToolkitRegistry`/`ToolkitRegistryParser`: ferramentas ligadas a pacotes do catálogo, com grupos (reconhecimento, rede, web, forense, auditoria), comandos, comando de verificação restrito (sem metacaracteres de shell), limitações sem root e documentação obrigatória; `verified` só com evidência.
- `ToolkitGuard`/`AuthorizationStore`: política de uso autorizado (escopo informado pelo usuário, validade de 24 h) para ferramentas que interagem com terceiros.
- `distribution/toolkit/tools.json` com 5 ferramentas já presentes no catálogo (curl, dnsutils, iputils-ping, iproute2, openssh-client) e docs em `docs/toolkit/`.
- ROADMAP: Fase 5 subdividida em 5.1–5.6 (total passa a 26 submódulos).
- Nenhum teste executado ou criado; nada compilado.

### 4.8 — UI do gerenciador e validação da Fase 4
- App: `PackagesViewModel` e `PackagesScreen` (busca, categorias, instalar/remover/atualizar com progresso e cancelamento, histórico, avisos de validação/limitações); botão "Pacotes" na tela principal.
- Service: `packageManager()` liga catálogo (asset `catalog.json`, a partir de `distribution/packages/catalog`), registro, histórico e runner ao runtime atual; `TerminalRuntimeGateway.packageManager()` (padrão nulo).
- Testes escritos para 4.1–4.7 (`android/runtime/src/test/.../packages/`): manifesto, catálogo/pesquisa, versões, dependências, instalação APT com runner falso, remoção, atualização, registro e histórico. **Não executados nesta sessão** (sem `kotlinc` nem rede); o CI executa via Gradle. O caminho de download direto (HTTPS) não tem teste automatizado ainda.
- Nada compilado (módulo `:android:app` só no CI).

### 4.7 — Histórico auditável, fachada e adaptadores
- `PackageHistoryStore`: log append-only (TAB) de instalações, remoções e atualizações, inclusive falhas; compactação atômica ao exceder o limite.
- `PackageManager`: fachada única (pesquisa, categorias, instalados, atualizações, install/update/remove) que audita cada operação.
- `LinuxRuntimePackageCommandRunner`: executa comandos do gerenciador no RootFS via `LinuxRuntime`.
- App: `PackageCatalogJson` (org.json → `PackageCatalogParser`).
- Nenhum teste executado ou criado; nada compilado.

### 4.6 — Atualização de pacotes
- `PackageUpdater`: `available()` (versão do catálogo > instalada, via `PackageVersion`) e `update(nomes?)`; reutiliza `PackageInstaller` (SHA-256/tamanho, troca atômica, registro após sucesso); sem rebaixamento.
- `PackageInstaller.install` ganhou `forceReinstall` (compatível com chamadas existentes).
- ROADMAP: antigo 4.7 separado em 4.7 (histórico/fachada/adaptadores) e 4.8 (UI e validação da fase); total passa a 21 submódulos.
- Nenhum teste executado ou criado; nada compilado.

### 4.5 — Remoção de pacotes
- `PackageRemover`: só remove pacotes registrados; recusa pacotes protegidos (apt, bash, coreutils, ca-certificates, dpkg) e pacotes com dependentes instalados; `apt-get remove` (sem purge/autoremove) ou apagamento seguro de `/opt/khalinux/<nome>`; registro alterado só após sucesso.
- Nenhum teste executado ou criado; nada compilado.

### 4.4 — Instalação de pacotes
- `PackageInstaller`: usa o plano do `DependencyResolver`; `debian-apt` via `apt-get install nome=versão` (índice atualizado uma vez); origens diretas com download retomável, conferência de tamanho e SHA-256 e extração protegida em `/opt/khalinux/<nome>` com troca atômica e versão anterior preservada até o sucesso.
- `InstalledPackageStore`: registro persistido com gravação atômica (inclui `remove`, usado em 4.5); registro só após conclusão do pacote.
- `PackageCommandRunner`: porta para executar comandos no RootFS (adaptador sobre `LinuxRuntime` no app, 4.7).
- Eventos de progresso por pacote/etapa; cancelamento cooperativo.
- Nenhum teste executado ou criado; nada compilado.

### 4.3 — Resolução de dependências
- `DependencyResolver`: plano em ordem topológica, detecção de ciclos, versão mínima, pacotes já instalados, checagem de arquitetura; falha sem plano parcial (`DependencyResolutionException`). `dependents()` para a remoção.
- `PackageVersion`: comparação de versões estilo Debian (epoch, `~`, revisão).
- Nenhum teste executado ou criado; nada compilado.

### 4.2 — Catálogo, pesquisa e categorias
- `PackageCatalog`: catálogo imutável (sem duplicatas; dependências devem existir), `find`, `categories()` com contagem e `search(PackageQuery)` por texto (sem caixa/acentos, com relevância), categoria, tag, arquitetura e estado de validação.
- `PackageCatalogParser` para `catalog.json`.
- `distribution/packages/catalog/catalog.json`: 8 pacotes já presentes no RootFS base, versões do lock APT; todos `unverified` (nenhuma compatibilidade em PRoot presumida).
- Nenhum teste executado ou criado; nada compilado.

### 4.1 — Modelo de manifesto de pacote
- `PackageManifest` com arquitetura, origem (APT fixado, release GitHub, repositório KhaLinux), versão, tamanhos, verificação SHA-256/evidência, estado de validação e limitações; invariantes validadas na construção (HTTPS, SHA-256 obrigatório em artefato direto, `verified` exige evidência, `known-limited` exige limitações).
- `PackageManifestParser`: converte estrutura decodificada em manifesto, rejeitando campos desconhecidos.
- Esquema JSON em `distribution/packages/schema/` e exemplo ilustrativo em `distribution/packages/examples/`.
- Fase 4 subdividida em 4.1–4.7 no ROADMAP (total passa a 20 submódulos).
- Nenhum teste executado ou criado; nada compilado (política por fase; sem `kotlinc` no ambiente).

### 3.1 — Instalação automática com porcentagem
- Instalação automática de PRoot e RootFS na primeira abertura (PRoot primeiro, depois Debian), sem ação do usuário.
- Progresso global 0–100% (`InstallProgressCalculator`) com fases: verificação, download PRoot, download RootFS, verificação de integridade, extração, finalização.
- Download retomável (`Range` + arquivo `.download`), até 3 tentativas com backoff, SHA-256 contínuo (`ResumableDownloader`).
- Progresso de extração por bytes (`extractSafely` com callback e cancelamento).
- Foreground service `dataSync` com notificação de progresso e ação "Cancelar"; permissão `POST_NOTIFICATIONS` solicitada na abertura.
- Estado persistido da instalação (`InstallStateStore`) e pós-instalação (`RootfsFinalizer`: `/etc/resolv.conf`, `/etc/hosts`, `/home/khalinux`).
- UI: painel com fase, barra, MB e botões "Cancelar instalação"/"Tentar novamente".
- Removidos: `installLatestProot`, `installLatestRootfs`, `installRootfs(url, sha)` e observadores antigos.
- Nenhum teste foi executado nesta etapa (política de execução por fase); build e testes ficam para o fechamento da Fase 3.

### 3.2 — Correções sem aparelho
- Rotação de tela: `ServiceTerminalRuntimeGateway` compara por instância do Service e o ViewModel reaproveita gateway/casos de uso; a sessão viva não é mais declarada interrompida.
- Histórico: entrada do shell interativo deixou de ser gravada (possíveis senhas); `validate-phase3.py` atualizado.
- `onCleared` cancela a sessão fora da thread principal; `runtime` do Service é `@Volatile`.
- PRoot: `--kill-on-exit` e `PROOT_TMP_DIR`.
- Home persistente: `filesDir/home` montado em `/home/khalinux`; reinstalar o RootFS não apaga os arquivos do usuário.
- UI: `systemBarsPadding`/`imePadding` e UUID da sessão oculto.
- Removido código morto: `TerminalOutputBuffer` (Java), seu teste e script.

### 3.3 — Tela de Diagnóstico
- `ProotLocator` (`FilesDirProotLocator`, `NativeLibProotLocator`, `FallbackProotLocator`): abstração para o plano B (`libproot.so` em jniLibs); o Service passa a localizar o PRoot por ela.
- `ProotConfig`: `tempDirectory` e `prootEnvironment` opcionais (comportamento padrão inalterado).
- `RuntimeDiagnostics`: `proot --version`, shell no RootFS (`uname -m`, `id`), DNS, rede, PTY (resize e Ctrl+C); rotação e segundo plano como itens manuais.
- Tela `DiagnosticsScreen` com OK/FALHA por item, relatório copiável e botão "Diagnóstico" na tela principal.
- Nenhum teste foi executado ou criado nesta etapa; `validate-phase3.py` falhou no ambiente por `test-pty-native.sh` (sem toolchain JNI), sem relação com esta mudança.

### 3.4 — Terminal utilizável
- Barra de teclas extras no shell interativo: Ctrl e Alt (armados para uma única tecla), ESC, Tab, setas, Home, End, Ctrl+C (`0x03`) e Ctrl+D (`0x04`) como bytes brutos (`TerminalKeys`/`TerminalKey`). Com Ctrl/Alt armado, "Enviar" aplica o modificador ao primeiro caractere digitado, sem Enter automático.
- Conflação: o emulador recebe os bytes na hora; o snapshot da UI é publicado no máximo a cada ~33 ms.
- Fila de saída da sessão limitada a 512 blocos pendentes (contrapressão no PTY).
- `AndroidPtyProcess`: o fd do master só é fechado depois de terminadas as operações em curso (corrida de fd corrigida).
- Nenhum teste executado ou criado; nada compilado.

### 3.5 — Release imutável, CI, documentação e testes
- Testes do runtime **executados** no host (kotlinc 1.9.24, JUnit 4.13.2): 59 testes OK e `pty_native=ok`. `android/runtime/test.sh` agora compila tudo (inclui `install/` e `diagnostics/`) e roda todas as classes `*Test`.
- Novos testes: `ProotLocatorTest`, `TerminalKeysTest`, `OutputBackpressureTest`, `RuntimeDiagnosticsTest`, `ResumableDownloaderTest` (servidor HTTP local), `InstallProgressStateTest`.
- Workflows de release: tag única por execução (`-r<n>`), recusa de tag existente, sem `overwrite_files`.
- Manifestos aprovados commitados em `distribution/manifests/release/` (hashes conferidos com os pinos do app).
- Gradle wrapper 8.7 (SHA-256 da distribuição verificado); CI usa `./gradlew`; `validate-phase3.py` removido.
- Room: `exportSchema = true` e `room.schemaLocation`. LICENSE Apache-2.0 completa e `NOTICE`.
- strings.xml: textos das telas e notificações do app movidos para recursos (rótulos de fase/diagnóstico do runtime continuam no módulo runtime).
- ADR-0005: `/proc` e `/sys` seguem sem bind (decisão registrada).
- README, ARCHITECTURE e `android/runtime/README.md` atualizados.
- Não compilado: módulo `:android:app` (depende do Android SDK; validação no CI).

### Documentação
- `docs/toolkit.md` e `docs/toolkit/*.md` adicionados.
- `docs/phase4-validation.md` adicionado.
- `docs/package-manager.md` adicionado.
- `docs/package-update.md` adicionado.
- `docs/package-remove.md` adicionado.
- `docs/package-install.md` adicionado.
- `docs/package-dependencies.md` adicionado.
- `docs/package-catalog.md` adicionado.
- `docs/package-manifest.md` adicionado.
- `docs/plano-de-correcao.md` adicionado.
- `docs/terminal-usability.md` adicionado.
- `docs/diagnostics.md` adicionado.

### 7.1 — Múltiplas sessões
- Fase 7 subdividida em 7.1–7.8 (ROADMAP); percentual recalculado para 78% (31/40).
- `TerminalTabs`/`TerminalTab` (`runtime/tabs`): até 4 abas, cada uma com emulador, sessão do runtime, título e rascunho próprios; sempre ao menos uma aba; fechar devolve a sessão a cancelar.
- `TerminalViewModel` refatorado: eventos roteados por aba, saída em segundo plano preservada, redimensionamento global aplicado a todas as abas/PTYs, cancelamento de todas as sessões em `onCleared`/reconexão. Corrigida a mensagem de reconexão que exibia `\n` literal.
- `KhaLinuxRuntimeService`: várias sessões interativas simultâneas (conjunto de ids + contador de inícios); o foreground só termina quando não resta nenhuma; a ação da notificação encerra todas.
- UI: barra de abas (selecionar, `+ Aba`, fechar, renomear por diálogo) e strings em `strings.xml`.
- `TerminalTabsTest` escrito; **não compilado nem executado** (sem `kotlinc` e sem rede).


### 7.2 — Personalização
- `TerminalThemes` (`runtime/settings`): 5 temas (Padrão, Claro, Alto contraste, Verde clássico, Solarized escuro); índice inválido volta ao padrão.
- Room versão 3: coluna `themeIndex` (`DEFAULT 0`) em `terminal_settings` com `MIGRATION_2_3`; o tema é salvo junto com fonte e dimensões.
- UI: seletor de tema; fundo e primeiro plano padrão do terminal (e vídeo inverso) usam o tema.
- `TerminalThemesTest` escrito; **não compilado nem executado**.

### 7.3 — Favoritos
- `FavoritesStore` (`runtime/favorites`): favoritos de comandos e ferramentas em `favorites.tsv` (gravação atômica, até 50 por tipo, sem duplicatas, recusa controle/quebra de linha/valores > 500).
- `FavoritesProvider` (instância compartilhada); terminal: botão de favoritar o comando digitado, atalhos e remoção; não favorita em sessão interativa. Toolkit: botão ★ por ferramenta, favoritas listadas primeiro.
- `FavoritesStoreTest` escrito; **não compilado nem executado**.

### 7.4 — Scripts
- `ScriptStore` (`runtime/scripts`): scripts `.sh` em `filesDir/home/scripts` (visíveis em `/home/khalinux/scripts`); nomes validados (sem traversal/injeção), até 64 KB, quebras de linha normalizadas, gravação atômica.
- UI: barra de scripts (criar, executar na aba ativa, editar, apagar) com diálogo de edição; execução usa `bash /home/khalinux/scripts/<nome>` e entra no histórico.
- `ScriptStoreTest` escrito; **não compilado nem executado**.

### 7.5 — Gerenciador de arquivos
- `HomeFileManager` (`runtime/files`): listar/ler/gravar texto/criar pasta/renomear/apagar confinados ao home persistente; recusa `..`, controle/NUL e links simbólicos que escapam da raiz; apagar não segue links; texto até 256 KB e sem NUL.
- UI `FilesScreen`/`FilesViewModel` (botão "Arquivos"), editor de texto, confirmação antes de apagar.
- `HomeFileManagerTest` escrito; **não compilado nem executado**.

### 7.6 — Relatórios
- `ReportBuilder`/`ReportWriter` (`runtime/reports`): relatório Markdown (resumo, tabela de laboratórios com estado/exercícios/pontos, tabela de ferramentas com instalação e última verificação) em `reports/relatorio-AAAAMMDD-HHMMSS.md` (UTC, sem sobrescrever). Não inclui respostas, hashes, dicas, escopos nem saídas brutas.
- UI: botão "Gerar relatório de progresso" na tela Arquivos (`FilesViewModel.attach/generateReport`).
- `ReportBuilderTest` escrito; **não compilado nem executado**.

### 7.7 — Integração entre ferramentas
- `OutputNote` (`runtime/reports`): nota Markdown com a saída da aba (cerca de código maior que qualquer sequência de crases, truncamento mantendo o final, 64 KB); `ReportWriter.write` ganhou prefixo (`nota-...`).
- Terminal: botão "Salvar saída em nota" (aba ativa + comando digitado) grava em `reports/`.
- Toolkit: botão "Usar no terminal" leva o comando da ferramenta ao campo de comando; só habilitado se instalada e, quando exige autorização, com escopo confirmado.
- `OutputNoteTest` escrito; **não compilado nem executado**.

### 7.8 — Assistente didático local
- Decisão (ADR-0006): sem modelo embarcado e sem rede; tutor offline determinístico baseado em regras e conteúdo versionado.
- Runtime: `TutorKnowledge`/`TutorKnowledgeParser` (TSV validado) e `TutorEngine` (busca por palavras-chave sem acentos, fichas de ferramentas do toolkit, recusa de gabarito e de ataque a terceiros, "não encontrei" quando não sabe). Base `distribution/tutor/knowledge.tsv` (32 tópicos, referências a `docs/` conferidas em Python).
- App: asset via `srcDir`, `TutorViewModel`, `TutorScreen`, botão "Tutor" e "Perguntar ao tutor" em cada exercício (só enunciado público). **Não compilado** (sem `kotlinc`/rede).
- `TutorEngineTest` escrito e não executado; suíte das Fases 6 e 7 transferida para a validação final. Percentual: 95%.

### 8.1 — Higiene de armazenamento
- ROADMAP: Fase 8 subdividida em 8.1–8.5 (44 submódulos; percentual recalculado: 86% antes do 8.1, 89% depois).
- `StorageHygiene` (relatório de espaço e limpeza de resíduos com regras de segurança), chamada na criação do Service e botão "Limpar resíduos de instalação" na tela Arquivos. Já existiam a checagem de espaço pré-download e a remoção do arquivo baixado; não foram refeitas. Ver `docs/storage.md`.
- `StorageHygieneTest` escrito e não executado; nada compilado (sem `kotlinc`/rede).

### 8.2 — Memória e saída do terminal
- `TerminalMemoryBudget` (histórico por aba calculado para o pior caso caber em ~24 MB), aplicado por `TerminalTabs` na criação e no `resizeAll`; `TerminalEmulator.setScrollbackLimit`.
- `TerminalStyle.DEFAULT` e `TerminalCell.BLANK` compartilhados: elimina um objeto por célula e por verificação em `plainText`/`displayRange`. Comportamento visual inalterado.
- Estimativas não medidas em aparelho; `TerminalMemoryBudgetTest` escrito e não executado; nada compilado. Ver `docs/memory.md`.

### 8.3 — Inicialização
- `AssetSyncStamp`: as fixtures dos laboratórios só são recopiadas quando o app é atualizado ou o destino sumiu (antes eram recopiadas a cada abertura).
- Aquecimento em segundo plano de catálogo, toolkit e laboratórios após a criação do Service. `createRuntimeIfInstalled()` na thread principal mantido (justificativa em `docs/startup.md`).
- `AssetSyncStampTest` escrito e não executado; nada compilado; sem medição de tempo.

### 8.4 — Bateria e processos
- Auditoria: foreground só com instalação/sessão, `onDestroy` cancela sessões e grupos de processos, sem wakelock/polling. Decisão registrada: sem encerramento automático por ociosidade (perderia trabalho do usuário).
- `MemoryPressure` + `TerminalTabs.trimBackground()` + `MainActivity.onTrimMemory`: sob pressão crítica, o histórico das abas em segundo plano cai para o mínimo; `select()` restaura o limite normal.
- `MemoryPressureTest` escrito e não executado; nada compilado; sem medição de consumo. Ver `docs/battery-processes.md`.

### 8.5 — Recuperação e validação da Fase 8
- `InstallRecovery`: restaura `.rootfs-arm64.previous`/`.proot.previous` quando uma instalação morreu entre as duas trocas e o destino não existe; registra instalação interrompida no `install-state.properties`. Chamada em `onCreate` do Service.
- `docs/recovery.md` e `docs/phase8-validation.md` (o que não foi validado). `InstallRecoveryTest` escrito e não executado; nada compilado.
- Fase 8 concluída em código. Percentual: 98% (43/44); falta a Fase 9.
