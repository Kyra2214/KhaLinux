# Evidências, eventos e retenção RED/BLUE

**Submódulo:** RED-06  
**Estado:** implementação em código/documentação concluída; validação e testes ficam para a fase final.

## Eventos HTTP

Cada requisição cujo cabeçalho foi recebido gera uma observação única com:
- timestamp de recebimento;
- método, path normalizado e status HTTP;
- Content-Type e quantidade de bytes do corpo;
- IP remoto observado pelo socket;
- allowlist de cabeçalhos seguros;
- confirmação de resposta enviada;
- motivo de rejeição, quando aplicável;
- indicador separado de falha interna do servidor.

A linha do tempo distingue `HTTP_REQUEST_RECEIVED`, `HTTP_REQUEST_REJECTED`, `HTTP_RESPONSE_SENT` e `HTTP_SERVER_FAILURE`. Eventos de navegação, redirecionamento, formulário e consentimento são registrados separadamente.

## Evidência de preparação e confiança TLS

- `TLS_CA_CERTIFICATE_DOWNLOADED` é emitido depois que o endpoint HTTP restrito envia o certificado público da CA. Esse evento não prova instalação ou confiança.
- `TLS_HANDSHAKE_OBSERVED` é emitido somente após uma resposta HTTPS válida para o participante vinculado à sessão e confirma que aquela conexão concluiu o handshake TLS.
- O evento de handshake não prova concessão de câmera, microfone ou localização; esses resultados continuam em eventos de permissão web separados.
- O relatório distingue download observado e handshake observado. Ausência de handshake não é interpretada como prova de que a CA não foi instalada.

## Dados que não são armazenados

- Corpo de requisição ou resposta.
- Valores de cookies.
- Cabeçalho Cookie.
- Authorization e credenciais.
- Valores dos formulários de login.
- Fluxos de câmera, microfone ou arquivos.

O formulário usa dados de demonstração. Os valores podem existir temporariamente no estado de UI para a demonstração, mas não são serializados no SessionStore. O relatório guarda apenas resultado e metadados.

## Cabeçalhos permitidos

A allowlist é limitada a Host, Accept, Content-Type e Referer reduzido ao path. User-Agent e Accept-Language não são persistidos para reduzir fingerprinting. Valores são limitados; Cookie e Authorization ficam excluídos.

## Persistência e limites

- `RedSessionStore` usa SharedPreferences `MODE_PRIVATE`, dentro do sandbox do aplicativo.
- Máximo de 100 sessões e 300 eventos por sessão.
- Detalhes limitados a 240 caracteres e rotas a 160.
- Campos estruturados de consentimento, permissão web, resposta, rejeição, falha, IP remoto e cabeçalhos permitidos são serializados.
- Campos novos são opcionais na leitura para compatibilidade com sessões antigas.
- A persistência é app-private, não criptografada em repouso; por isso, nenhum corpo, senha, token de sessão ou valor de cookie é gravado.

## Evidência ao vivo

- `RedLiveEvidenceStore` compartilha eventos, observações HTTP e o resultado sintético efêmero com BLUE no processo atual.
- Observações HTTP ao vivo são limitadas às 100 mais recentes.
- Ao encerrar a sessão, os dados efêmeros e o store compartilhado são limpos; o histórico de eventos minimizados permanece para relatório.
- A ação de limpar dados sintéticos remove imediatamente os valores efêmeros do painel.

## Relatório e BLUE

O relatório RED e a linha do tempo BLUE exibem método, rota, status, bytes, origem, cabeçalhos permitidos, resposta enviada, motivo de rejeição, falha interna, categorias de cookies e estados de permissão. BLUE não gera alertas sem evento correspondente.

## Interrupção e recuperação

- Ao ir para segundo plano ou bloquear a tela, o processo encerra hotspot/portal, grava `SESSION_INTERRUPTED` e limpa evidências efêmeras.
- Ao recriar o ViewModel, sessões sem `endedAt` e sem estado final são marcadas como interrompidas; nenhuma sessão é retomada automaticamente.
- O encerramento normal usa `SESSION_ENDED`; interrupção nunca é apresentada como conclusão.

## Validação pendente

Testes de serialização/migração, limites, ordenação, limpeza, falha e renderização BLUE ficam para a fase final. Nenhum teste/build/compilação foi executado nesta implementação.
