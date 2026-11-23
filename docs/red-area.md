# Área RED

**Estado:** fundação inicial existente; roadmap de laboratório completo revisado em 2026-11-23.  
**Branch:** `plan/area-red-wifi-publico`  
**Documento mestre:** `docs/roadmap-area-red-completa.md`  
**Operação 01:** `docs/roadmap-area-red-estagio-01-wifi-publico.md`

## Direção

RED é a área de preparação e execução prática. BLUE é a área defensiva correspondente. As duas áreas devem compartilhar identificador de cenário e evidências, mas manter interfaces e roteiros pedagógicos próprios.

O KhaLinux está sendo desenvolvido como protótipo pessoal de laboratório com dois aparelhos Android dedicados/controlados pelo instrutor. Nas aulas, mostrar comportamento e resultados: criação da rede, conexão do segundo aparelho, portal, navegação, permissões e o que efetivamente chegou ao servidor. Código-fonte e scripts internos não fazem parte dos vídeos.

## Fundação já presente no código

- Entrada RED na navegação principal.
- Catálogo versionado em `distribution/red/operations.json`.
- Parser, ficha de operação, tutorial e filtros.
- Sessões locais, eventos, progresso e recuperação de sessões interrompidas.
- Histórico limitado e relatório Markdown copiável/exportável.
- Adaptador Android para solicitar `LocalOnlyHotspot`.
- Servidor HTTP local inicial, endereço e QR Code.
- Painel inicial de eventos.

## Lacunas reconhecidas

A primeira versão ainda não representa o laboratório completo decidido em 2026-11-23:
- Formulário atual envia somente um evento fixo; não mostra os campos recebidos.
- O portal ainda não oferece os dois cenários completos (login local e acesso direto).
- A navegação/linkagem e os redirecionamentos ainda não formam uma linha do tempo detalhada.
- As permissões estão representadas por botões simulados, não por fluxo real no aparelho participante.
- O servidor deve evoluir para porta padrão 8080, observabilidade de requisições e vínculo estrito à interface do laboratório.
- A integração didática RED ↔ BLUE ainda precisa ser especificada e implementada.

## Organização existente

`android/app/src/main/kotlin/com/khalinux/android/red/`

- `RedModels.kt`: catálogo, operação, etapas, sessão e eventos.
- `RedCatalogJson.kt`: parser do catálogo.
- `RedSessionStore.kt`: persistência local.
- `RedViewModel.kt`: estado, transições e coordenação.
- `RedHotspotController.kt`: adaptador de hotspot Android.
- `RedPortalServer.kt`: servidor HTTP local inicial.
- `RedScreen.kt`: catálogo, ficha, tutorial, execução e relatório.

## Diretrizes de experiência

- Portal do participante com apresentação natural de portal Wi-Fi convencional.
- Evitar avisos grotescos e banners artificiais durante a demonstração.
- Identificação do laboratório e observabilidade completa no painel do instrutor.
- Não falsificar diálogos de sistema.
- O painel diferencia solicitado, autorizado, executado e recebido.
- Não alegar recebimento de dados que não chegaram ao servidor.
- Login é opcional no cenário; também haverá acesso direto e navegação sem autenticação.

## Arquitetura da Operação 01

- Aparelho A: operador, hotspot, servidor local e painel RED.
- Aparelho B: participante, conexão Wi-Fi, navegador e modo participante Android para demonstração de permissões nativas.
- Portal HTTP local na porta TCP 8080.
- Cenários: autenticação local fictícia e acesso direto sem login.
- Formulários recebem somente valores sintéticos de teste, exibidos temporariamente no painel durante a sessão.
- Links e navegação são registrados como eventos locais.
- Permissões nativas são solicitadas no aparelho B; a conexão Wi-Fi ou o login não concedem acesso a câmera, microfone, localização ou arquivos.
- O navegador em HTTP/IP de LAN tem restrições de contexto seguro para APIs como câmera, microfone e geolocalização; essas demonstrações exigem modo participante Android ou outra solução tecnicamente compatível.
- Arquivos são selecionados explicitamente pelo seletor do sistema; conteúdo não é transmitido por padrão.
- Nenhum tráfego de terceiros é interceptado e nenhuma informação é enviada automaticamente à internet.

## Validação final pendente

Conforme a regra do projeto, nenhum teste, build, validação ou teste físico será executado durante a implementação do roadmap. Ao final, validar:
1. integração Compose e navegação;
2. parser, catálogo e estados;
3. persistência, retenção e recuperação;
4. hotspot e ciclo de vida;
5. servidor 8080, interface de bind, rotas e limites;
6. comunicação entre dois aparelhos;
7. portal com login local e acesso direto;
8. formulários sintéticos e visualização de requisições recebidas;
9. navegação, links e redirecionamentos;
10. permissões reais e modo participante;
11. relatório e limpeza;
12. integração RED/BLUE e regressões gerais.

## Limitações conhecidas

- `LocalOnlyHotspot`, configuração e endereço variam por Android/fabricante.
- A abertura automática do portal não é garantida.
- A interface Wi-Fi pode não ser exposta ao app em todos os dispositivos.
- HTTP em IP local não é HTTPS e não deve ser apresentado como conexão protegida.
- O portal web não obtém permissões nativas só porque o participante está conectado.
- Nenhuma capacidade nova descrita no roadmap está validada até a fase final.
