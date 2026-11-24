# Área RED — laboratório de Wi-Fi e portal local

**Estado da implementação:** RED-0 a RED-12 implementados em código/documentação (100% dos 13 submódulos de implementação). RED-13 é a validação final pendente; o projeto ainda não está concluído.  
**Branch:** `plan/area-red-wifi-publico`  
**Roadmap mestre:** [roadmap-area-red-completa.md](roadmap-area-red-completa.md)  
**Operação 01:** [red-operation-01.md](red-operation-01.md)

## Objetivo

O RED é a área de preparação e execução de demonstrações práticas com dois aparelhos Android dedicados e controlados pelo instrutor. Os vídeos mostram o comportamento e os resultados, não o código-fonte ou scripts internos.

- **Aparelho A:** operador, hotspot criado pelas APIs públicas do Android, servidor HTTP local e painel de observação.
- **Aparelho B:** participante, conecta-se à rede, navega pelo portal e executa demonstrações nativas de permissões no próprio aparelho.

A interface do participante deve ser natural e semelhante a um portal Wi-Fi convencional. A identificação da sessão e a telemetria completa ficam no painel do instrutor, sem avisos artificiais excessivos. Diálogos de permissão são sempre os diálogos reais do sistema.

## Operação 01 — fluxo implementado

1. Criar uma sessão RED e selecionar login local ou acesso direto.
2. Solicitar ao Android o `LocalOnlyHotspot`; exibir somente SSID, senha e parâmetros que o sistema disponibilizar.
3. Identificar o IPv4 da interface Wi-Fi/AP e iniciar o servidor HTTP local, porta padrão **8080** (configurável explicitamente).
4. Exibir URL e QR Code.
5. Conectar manualmente o aparelho B e abrir o portal.
6. Percorrer páginas locais, links e redirecionamentos.
7. Observar no painel as requisições realmente recebidas.
8. Responder ao aviso de cookies no navegador do B e acompanhar a escolha registrada no painel A.
9. Alternar para BLUE para analisar o mesmo cenário pela perspectiva defensiva.

O servidor faz bind somente no IPv4 da interface Wi-Fi/AP identificada; não usa `0.0.0.0`. HTTP na porta 8080 é HTTP sem TLS, não HTTPS. Não há proxy transparente, interceptação de tráfego externo ou dependência de serviços remotos para a operação básica.

## Modos de portal

### Login local

- Página de acesso fictícia, sem imitar marcas ou serviços reais.
- Formulário faz POST HTTP real para o servidor local.
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
- Solicitações web de câmera, microfone ou localização dependem de origem segura e de suporte do navegador, além de ação explícita e avisos próprios do navegador.
- O portal atual é HTTP em endereço IP local. Se o navegador não considerar essa origem segura, a API sensível fica indisponível. Não há modo participante nativo como alternativa.
- O aparelho A centraliza toda a observabilidade e a explicação. O B mostra somente o portal e os avisos normais do navegador.


## Observabilidade RED

- Estado do hotspot e do servidor, interface, endereço e porta.
- Atividade do participante confirmada por requisição HTTP recebida. Android não fornece uma lista universal de clientes do `LocalOnlyHotspot`.
- Requisições com método, rota, status, horário, bytes, tipo, endereço de origem e cabeçalhos permitidos.
- Campos sintéticos recebidos durante a sessão.
- Linha do tempo de navegação, formulários, redirecionamentos, permissões e recursos.
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
- [Roadmap mestre](roadmap-area-red-completa.md): requisitos, dependências e ordem de implementação.

Os vídeos devem mostrar a criação da rede, conexão do segundo aparelho, portal, aviso de cookies, login de teste, navegação e o que chegou ao servidor; permissões web somente quando suportadas pelo contexto seguro. Não mostrar código-fonte, scripts ou detalhes internos.

## Modelo de domínio

Consulte [red-domain-model.md](red-domain-model.md) para configuração solicitada, observações Android, endpoint real, participante observado, consentimento de cookies e estados tipados de permissões web.

## Estrutura de código principal

`android/app/src/main/kotlin/com/khalinux/android/red/`

- `RedModels.kt`: estados, sessões, eventos e observações HTTP.
- `RedCatalogJson.kt`: parser do catálogo.
- `RedSessionStore.kt`: persistência de metadados.
- `RedViewModel.kt`: coordenação de sessão, hotspot, portal e evidências.
- `RedHotspotController.kt`: integração com `LocalOnlyHotspot`.
- `RedPortalServer.kt`: servidor HTTP local, rotas, formulários e redirecionamentos.
- `RedLabInputValidator.kt`: allowlist e validação de dados sintéticos/eventos do participante.
- O banner de cookies e o endpoint `/consent` ficam no `RedPortalServer.kt`; o B usa somente o navegador.
- `RedLiveEvidenceStore.kt`: evidências efêmeras compartilhadas com BLUE.
- `RedScreen.kt`: painel do operador, histórico e relatórios.

## Regras de execução e validação

- Trabalho aplicado diretamente na branch `plan/area-red-wifi-publico`; não gerar ZIP/checkpoints.
- Não fazer merge para `main` sem autorização explícita.
- Nenhum teste, build, compilação, auditoria ou teste físico deve ser executado durante a implementação.
- RED-13 acumula a validação final, inclusive integração, permissões, compatibilidade e teste físico com os dois aparelhos.
- A existência de código e a inspeção estática não equivalem a funcionamento validado.

## Limitações conhecidas

- O Android controla os parâmetros e o ciclo de vida do hotspot; fabricantes/versões podem variar.
- A abertura automática do portal não é garantida; endereço e QR Code são alternativas.
- A interface AP/IPv4 pode não ser exposta ao aplicativo.
- O portal HTTP não oferece TLS e não deve ser apresentado como HTTPS.
- O navegador não recebe permissões nativas só por estar conectado ou fazer login.
- O servidor observa apenas as requisições destinadas a ele, não o tráfego geral do aparelho participante.
