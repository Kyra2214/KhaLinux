# Modelo de domínio RED/BLUE

**Submódulo:** RED-01  
**Estado:** implementação em código concluída; testes e compilação acumulados para a fase final.

## Configuração solicitada e observações reais

- `RedPortalConfiguration` guarda o modo solicitado e a porta solicitada pelo operador; a porta padrão do portal participante é HTTPS/8443.
- `RedHotspotSnapshot` guarda o estado e os dados efetivamente disponibilizados pelo callback Android. SSID, senha, interface e IPv4 permanecem anuláveis quando não informados.
- `RedPortalEndpoint` representa o endpoint realmente iniciado, com URL, interface identificada quando disponível e porta vinculada.
- A configuração solicitada não substitui nem preenche os valores observados pelo Android.

## Participante observado

`RedParticipantObservation` representa atividade HTTP(S) observada pelo servidor: origem remota quando disponível, primeira/última requisição e contagem. Não significa que o Android forneceu uma lista de clientes Wi-Fi nem prova, isoladamente, estado de conexão de rede.

## Consentimento de cookies

- `RedCookieConsent` representa a escolha tipada: aceitar todos, recusar opcionais ou configuração personalizada.
- Categorias opcionais: análise, preferências e personalização.
- `RedEvent` persiste escolha e estados booleanos por categoria; valores de cookies e cabeçalhos Cookie não são persistidos.
- O evento só é criado depois que o endpoint local valida o POST recebido.

## Permissões web

- `RedWebPermissionResource`: câmera, microfone ou localização.
- `RedWebPermissionState`: solicitado, concedido, negado, cancelado, indisponível ou revogado.
- `RedEvent` mantém recurso e estado em campos tipados. Nenhum evento de permissão web é criado até que o navegador retorne um resultado observável.
- Permissões nativas do modo participante foram removidas. O B permanece navegador-only.

## Eventos e persistência

- `RedEvent` conserva ID, sessão, sequência, timestamp, origem, rota/status HTTP e campos tipados de consentimento/permissão; os eventos `TLS_CA_CERTIFICATE_DOWNLOADED` e `TLS_HANDSHAKE_OBSERVED` registram separadamente preparação e confiança observada.
- `RedSessionStore` serializa os novos campos como opcionais. Sessões antigas sem esses campos continuam sendo lidas com valores nulos.
- `RedLiveEvidenceStore` compartilha os mesmos eventos com a perspectiva BLUE.
- O relatório inclui escolha de cookies e categorias, sem valores dos cookies.

## Limites

- O portal participante usa HTTPS local por IP privado; o endpoint HTTP de preparação publica somente a CA pública. APIs web sensíveis dependem de contexto seguro e confiança efetiva do navegador.
- O modelo de participante representa a última origem HTTP observada, não uma lista universal de clientes do hotspot.
- Testes unitários, integração, build e testes físicos não foram executados nesta etapa e permanecem para a validação final.
