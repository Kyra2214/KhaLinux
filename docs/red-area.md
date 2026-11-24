# Área RED — laboratório de Wi-Fi e portal local

**Estado:** RED-07 permanece parcial; o portal HTTPS local e preparação da CA foram implementados inicialmente, mas confiança em navegadores e acabamento aguardam validação. RED-14 está parcialmente preparado; APK/hash/PR aguardam validação final. Builds, auditorias e testes ficam para a fase final.  
**Branch:** `recovery/red-portal-integration`  
**Roadmap ativo:** [roadmap-red-blue-laboratorio.md](roadmap-red-blue-laboratorio.md)  
**Operação 01:** [red-operation-01.md](red-operation-01.md)

## Objetivo

O RED é a área de preparação e execução de demonstrações práticas com dois aparelhos Android dedicados e controlados pelo instrutor. Os vídeos mostram o comportamento e os resultados, não o código-fonte ou scripts internos.

- **Aparelho A:** operador, hotspot criado pelas APIs públicas do Android, portal HTTPS local e painel de observação; HTTP/8080 é restrito à preparação da CA pública.
- **Aparelho B:** participante passivo, conecta-se à rede, abre o portal no navegador, faz login de teste e responde às solicitações web legítimas, quando suportadas. Não instala KhaLinux nem executa ferramentas.

A interface do participante deve ser clara e semelhante a um portal Wi-Fi convencional. A identificação da sessão e a telemetria completa ficam no painel do instrutor. Solicitações web, quando suportadas, usam os avisos reais do navegador.

## Operação 01 — fluxo implementado

1. Criar uma sessão RED e selecionar login local ou acesso direto.
2. Solicitar ao Android o `LocalOnlyHotspot`; exibir somente SSID, senha e parâmetros que o sistema disponibilizar.
3. Identificar o IPv4 da interface Wi-Fi/AP, iniciar o endpoint HTTP restrito de preparação em **8080** e o portal HTTPS em **8443**.
4. Exibir URL e QR Code.
5. Conectar manualmente o aparelho B e abrir o portal.
6. Percorrer páginas locais, links e redirecionamentos.
7. Observar no painel as requisições realmente recebidas.
8. Responder ao aviso de cookies no navegador do B e acompanhar a escolha registrada no painel A.
9. Se a aula incluir permissões web, abrir a página correspondente e usar cada solicitação separadamente.
10. Alternar para BLUE para analisar o mesmo cenário pela perspectiva defensiva.

Os dois serviços fazem bind somente no IPv4 da interface Wi-Fi/AP identificada; não usam `0.0.0.0`. HTTP/8080 publica somente a página de preparação e a CA pública; o portal participante e suas APIs usam HTTPS/8443. Não há proxy transparente, interceptação de tráfego externo ou dependência de serviços remotos.

## Modos de portal

### Login local

- Página de acesso fictícia, sem imitar marcas ou serviços reais.
- Formulário faz POST HTTPS real para o servidor local.
- O backend aceita exclusivamente os campos sintéticos `demo_id` e `demo_code`, com formatos de teste restritos.
- O painel mostra os valores aceitos somente durante a sessão ativa.
- Valores não são gravados no histórico nem no relatório; podem ser removidos do painel pelo operador.

### Acesso direto

- O participante continua sem preencher formulário.
- Páginas locais de termos, conteúdo, notícias e ofertas demonstram navegação após a conexão.
- A rota de demonstração de redirecionamento responde com HTTP 302 para outra página local.
- O painel registra método, caminho, status, bytes, origem e cabeçalhos permitidos.

## Cookies e permissões do navegador

O aparelho B participa somente pelo navegador. Não instala nem executa o KhaLinux e não abre ferramentas ou painéis.

- O portal apresenta um aviso de cookies com “Aceitar todos”, “Recusar opcionais” e “Configurar”.
- A escolha é enviada ao servidor local e aparece na linha do tempo do aparelho A, com categorias aceitas/recusadas. Valores de cookies e cabeçalhos Cookie não são armazenados.
- “Aceitar todos” refere-se somente às categorias de cookies descritas no aviso. Não concede permissões do Android ou do navegador.
- A página **Permissões do navegador** oferece ações independentes para câmera, microfone e localização; cada solicitação depende de clique próprio, origem segura e suporte do navegador, além dos avisos próprios.
- O portal participante usa HTTPS local em endereço IP. Se a CA não for confiável pelo navegador, o handshake não será concluído e as APIs sensíveis permanecem indisponíveis. A instalação da CA não garante compatibilidade universal; o painel registra download e handshake como fatos separados.
- O aparelho A centraliza toda a observabilidade e a explicação. O B mostra somente o portal e os avisos normais do navegador.


## Observabilidade RED

- Estado do hotspot e do servidor, interface, endereço e porta.
- Atividade do participante confirmada por requisição HTTP recebida. Android não fornece uma lista universal de clientes do `LocalOnlyHotspot`.
- Requisições com método, rota, status, horário, bytes, tipo, endereço de origem e cabeçalhos permitidos.
- Campos sintéticos recebidos durante a sessão.
- Linha do tempo de navegação, formulários, redirecionamentos, consentimento e estados de permissões web.
- Resumo das escolhas de cookies e dos estados de permissões web que o navegador efetivamente disponibilizar.
- Histórico limitado a 100 sessões e 300 eventos por sessão.
- Relatório Markdown com metadados e eventos, sem valores de formulário, códigos, cookies, tokens, arquivos, áudio, vídeo ou coordenadas.

O painel diferencia o que foi solicitado, autorizado, executado e recebido. Não afirma sucesso quando o Android ou navegador bloqueia uma ação.

## Integração BLUE

BLUE consome as sessões e evidências do RED, inclusive evidências temporárias da sessão ativa. Apresenta requisições, cabeçalhos permitidos, campos sintéticos, estados de recursos e linha do tempo com interpretação defensiva. A análise distingue fatos observados de recomendações e não inventa detecções.

Consulte [blue-area.md](blue-area.md).

## Documentação de aula

- [Guia do instrutor](red-instructor-guide.md): preparação dos dois aparelhos, cenários, permissões, roteiro de vídeo e troubleshooting.
- [Guia do usuário](user-guide.md): navegação no app e visão geral das áreas RED/BLUE.
- [Roadmap ativo](roadmap-red-blue-laboratorio.md): requisitos, dependências e ordem de implementação.
- [Segurança e privacidade](red-security-privacy.md): hardening implementado e auditoria final pendente.
- [Sessões e relatórios](red-session-reports.md): schema, retenção, exportação e exclusão.
- [Interface do operador](red-operator-interface.md): estados e limites apresentados no A.
- [Preparação de release](red-release-checklist.md): portões e artefatos finais pendentes.

Os vídeos devem mostrar a criação da rede, conexão do segundo aparelho, portal, aviso de cookies, login de teste, navegação e o que chegou ao servidor; permissões web somente quando suportadas pelo contexto seguro. Não mostrar código-fonte, scripts ou detalhes internos.

## Permissões web do navegador B

Consulte [red-browser-permissions.md](red-browser-permissions.md) para estados reais, ausência de coleta de mídia/coordenadas e dependência de HTTPS confiável.

## Eventos e evidências

Consulte [red-event-evidence.md](red-event-evidence.md) para eventos HTTP, persistência, allowlist de cabeçalhos, limites, relatório e integração BLUE.

## Servidores locais

Consulte [red-http-server.md](red-http-server.md) para a base do servidor, bind, validação de Host e limites. O portal participante usa HTTPS/8443; a preparação HTTP/8080 é isolada e entrega apenas a CA pública.

## Modelo de domínio

Consulte [red-domain-model.md](red-domain-model.md) para configuração solicitada, observações Android, endpoint real, participante observado, consentimento de cookies e estados tipados de permissões web.

## Estrutura de código principal

`android/app/src/main/kotlin/com/khalinux/android/red/`

- `RedModels.kt`: estados, sessões, eventos e observações HTTP.
- `RedCatalogJson.kt`: parser do catálogo.
- `RedSessionStore.kt`: persistência de metadados.
- `RedViewModel.kt`: coordenação de sessão, hotspot, portal e evidências.
- `RedHotspotController.kt`: integração com `LocalOnlyHotspot`.
- `RedPortalServer.kt`: servidor do portal local com HTTP/HTTPS, rotas, formulários e redirecionamentos; o ViewModel inicia o transporte HTTPS.
- `RedLabInputValidator.kt`: allowlist e validação de dados sintéticos/eventos do participante.
- O banner de cookies e o endpoint `/consent` ficam no `RedPortalServer.kt`; o B usa somente o navegador.
- `RedLiveEvidenceStore.kt`: evidências efêmeras compartilhadas com BLUE.
- `RedScreen.kt`: painel do operador, histórico e relatórios.

## Regras de execução e validação

- Trabalho aplicado diretamente na branch `recovery/red-portal-integration`; não gerar ZIP/checkpoints.
- Não fazer merge para `main` sem autorização explícita.
- Nenhum teste, build, compilação, auditoria ou teste físico deve ser executado durante a implementação.
- RED-12 acumula build/testes host/Android; RED-13 acumula testes físicos A+B. Ambos só serão executados após a conclusão integral do roadmap.
- A existência de código e a inspeção estática não equivalem a funcionamento validado.

## Limitações conhecidas

- O Android controla os parâmetros e o ciclo de vida do hotspot; fabricantes/versões podem variar.
- A abertura automática do portal não é garantida; endereço e QR Code são alternativas.
- A interface AP/IPv4 pode não ser exposta ao aplicativo.
- O portal participante usa HTTPS local; o endpoint HTTP de preparação entrega somente a CA pública e não oferece permissões web.
- O navegador não recebe permissões nativas só por estar conectado ou fazer login.
- O servidor observa apenas as requisições destinadas a ele, não o tráfego geral do aparelho participante.
