# Área RED

**Status:** implementação em andamento na branch `plan/area-red-wifi-publico`.

## Escopo entregue nesta etapa

- Entrada RED na navegação inferior principal.
- Catálogo de operações carregado do asset versionado `distribution/red/operations.json`.
- Parser com validação de versão, ids e etapas duplicadas.
- Ficha da operação, objetivos, pré-requisitos, ambiente, tutorial e critérios de conclusão.
- Filtros por categoria e dificuldade.
- Sessões locais com estados, eventos cronológicos, progresso e recuperação de sessões interrompidas.
- Histórico local limitado a 100 sessões e 300 eventos por sessão.
- Relatório Markdown copiável e exportável manualmente pelo seletor de documentos do Android.
- Exclusão manual do histórico.
- Adaptador Android para solicitar `LocalOnlyHotspot`.
- Portal HTTP local vinculado a uma interface Wi-Fi identificada, endereço copiável e QR Code.
- Portal de demonstração explicitamente identificado como laboratório, com formulário sintético, fluxo genérico de rede social e botões de permissão simulada (localização/câmera/arquivos), sem solicitar permissões reais.

## Organização

`android/app/src/main/kotlin/com/khalinux/android/red/`

- `RedModels.kt`: catálogo, operação, etapas, sessão e eventos.
- `RedCatalogJson.kt`: parser do catálogo versionado.
- `RedSessionStore.kt`: persistência local mínima.
- `RedViewModel.kt`: estados, transições, histórico e coordenação.
- `RedHotspotController.kt`: adaptador para o hotspot local do Android.
- `RedPortalServer.kt`: servidor HTTP local de escopo restrito.
- `RedScreen.kt`: catálogo, ficha, tutorial, execução e relatório.

## Limites de segurança

- O hotspot é solicitado por ação explícita e gerenciado pelo Android.
- O app não clona SSIDs de estabelecimentos, não varre redes e não intercepta tráfego.
- O portal só aceita `GET /` e `POST /submit`.
- O formulário do portal transmite somente o literal fixo `demo=completed`; os campos não têm nomes e seus valores nunca são serializados para a requisição.
- O servidor descarta o corpo recebido e registra apenas a conclusão sintética.
- Nenhuma senha, token, cookie ou dado de outro aplicativo é persistido.
- O servidor vincula o socket ao IPv4 de interface Wi-Fi identificada (`wlan*` ou `ap*`), não a todas as interfaces. Se o Android não expuser essa interface, a operação informa a falha em vez de abrir o serviço em todas as redes.
- O portal usa HTTP local porque não depende de infraestrutura externa; não deve ser exposto por encaminhamento de portas.
- O Android pode impedir hotspot local, exigir serviços/permissões ou não expor a interface de rede. O app deve mostrar o erro real, sem simular sucesso.

## Pendências para a validação final

Nenhuma validação foi executada durante a implementação, conforme o processo do projeto. A lista a seguir fica reservada para o fechamento:

1. Compilar o módulo Android e corrigir erros de integração.
2. Testar parsing e validação do catálogo.
3. Testar serialização, limite de histórico e recuperação de sessões.
4. Testar máquina de estados e eventos concorrentes.
5. Testar servidor HTTP: rotas permitidas, descarte do corpo, encerramento e vinculação à interface Wi-Fi.
6. Testar permissões e ciclo de vida de `LocalOnlyHotspot` em versões/fabricantes suportados.
7. Testar QR Code, endereço local e conexão do segundo aparelho.
8. Testar exportação e exclusão de relatórios/histórico.
9. Testes físicos com dois aparelhos Android somente nesta fase final.
10. Verificar regressões na navegação, Terminal, Toolkit, Labs e Tutor.

## Limitações conhecidas

- A abertura automática do portal cativo não é garantida. O QR Code e o endereço manual são o caminho de contingência.
- A atribuição de endereço IPv4 à interface de hotspot varia por fabricante. A implementação só publica o portal quando consegue identificar e vincular a interface Wi-Fi.
- O catálogo inicial contém somente a Operação 01. Operações futuras não são cadastradas como disponíveis sem especificação própria.
- A persistência RED usa SharedPreferences isolado, sem alterar o esquema Room atual. Os dados são locais e mínimos.
