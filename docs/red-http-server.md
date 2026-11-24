# Servidor HTTP local RED

> **Atualização RED-07:** o servidor participante usado em produção pelo ViewModel agora inicia por `RedPortalServer.startHttps` na porta `8443`. A porta HTTP `8080` é fornecida por `RedTlsPreparationServer` e publica somente a página de preparação, o certificado público da CA e health check. A descrição abaixo registra a base HTTP e seus limites, não significa que o portal participante permaneça em HTTP.

**Submódulo:** RED-04  
**Estado:** implementação em código/documentação concluída; testes host e integração ficam para a fase final.

## Bind e ciclo de vida

- O servidor exige o IPv4 privado/compartilhado literal fornecido pelo controlador do hotspot.
- Resolve o endereço em uma interface Wi-Fi/AP ativa com nome reconhecido e confirma a posse exata do IPv4.
- Nunca usa `0.0.0.0`, endereço curinga, hostname, loopback, link-local ou interface arbitrária.
- O socket é criado diretamente no endereço selecionado. Porta ocupada ou bind inválido resulta em erro e o ViewModel libera a reserva do hotspot.
- A cada timeout de accept, o worker confirma que a mesma interface ainda está ativa e mantém o mesmo IPv4. Perda/alteração encerra o servidor e limpa o endpoint no painel A.
- Ao encerrar sessão ou hotspot, sockets e clientes são fechados e o endereço é limpo.

## Limites

| Recurso | Limite |
|---|---:|
| Cabeçalho HTTP | 8 KiB |
| Corpo de requisição | 4 KiB |
| Resposta | 128 KiB |
| Clientes simultâneos | 4 |
| Timeout de leitura por operação de socket | 5 s |
| Timeout de accept para verificar interface | 1 s |
| Backlog do socket | 16 |
| Comprimento do path | 512 caracteres |
| Query | 1 KiB, rejeitada quando não vazia |

## Parsing e validação

- Apenas HTTP/1.0 e HTTP/1.1; HTTP/1.1 exige Host.
- Host deve corresponder exatamente ao IPv4 e porta vinculados.
- Cabeçalhos duplicados, nomes inválidos, header folding e valores excessivos são rejeitados.
- Transfer-Encoding/chunked não é aceito; Content-Length precisa ser decimal, único e dentro do limite.
- Formulários `/login` e `/consent` exigem `application/x-www-form-urlencoded`.
- `GET /heartbeat` exige o IP já vinculado e o cookie `kl_lab_session`, não recebe corpo e responde `204`; a página visível envia o sinal periodicamente para observar alcance local do participante.
- Paths absolutos, traversal, barra invertida, path excessivo e query string são rejeitados.
- Métodos aceitos: GET, POST e HEAD nas rotas previstas. Demais métodos recebem 405.
- Respostas têm tamanho limitado, Cache-Control no-store, nosniff, Referrer-Policy, X-Frame-Options e CSP local.

## Observabilidade e privacidade

- Toda requisição cujo cabeçalho foi recebido gera uma observação única, inclusive as rejeitadas.
- O A recebe método, path, status, Content-Type, bytes, IP remoto e allowlist de cabeçalhos.
- Cookie, Authorization, corpo de formulário e valores de cookies não são registrados.
- Referer é reduzido ao path; Host e metadados não sensíveis podem aparecer no painel.
- O servidor serve somente páginas/recursos locais embarcados; não busca páginas externas.

## Proteção da sessão do navegador

- A cada start, o servidor gera token aleatório de 256 bits com `SecureRandom`; o token existe somente em memória.
- O token é entregue por cookie `HttpOnly; SameSite=Strict; Path=/`, nunca em URL, QR code, HTML ou log.
- A primeira página de entrada (`/`, `/login` ou `/open`) vincula a sessão ao IP remoto do primeiro navegador participante.
- POSTs de login, consentimento e permissão web exigem cookie válido, origem correspondente e IP participante vinculado.
- Comparação do token usa `MessageDigest.isEqual`; cinco falhas por IP acionam bloqueio de 60 segundos.
- Cinco falhas de login de demonstração também acionam bloqueio de 60 segundos.
- Sessão expira após 30 minutos sem atividade ou duas horas de duração absoluta; o servidor encerra e invalida o token. O ViewModel libera também o hotspot quando o portal termina inesperadamente ou expira.
- `stop()` limpa token, IP participante, contadores e sockets. O cookie é renovado nas respostas bem-sucedidas do participante. Na implementação-base HTTP anterior ao RED-07, o cookie não recebia `Secure` e o token trafegava sem TLS. No fluxo atual, o portal participante usa HTTPS/8443, o cookie recebe `Secure` e os POSTs validam a origem HTTPS. O HTTP/8080 permanece restrito à preparação e ao certificado público da CA.

## Histórico da base HTTP

A implementação-base descrita neste documento era HTTP local e não oferecia contexto seguro para as APIs sensíveis do navegador. Esse comportamento não representa mais o transporte do portal participante: o ViewModel inicia `RedPortalServer.startHttps` em `8443`. O endpoint HTTP `8080` publica somente preparação e CA pública; não hospeda login, consentimento ou permissões web.

## Limitações

- A descoberta de interface depende de nomes reconhecidos pelo controlador; fabricantes com nomes não reconhecidos falham fechados.
- Timeout de leitura é aplicado por operação de socket; não representa deadline absoluto de toda a requisição.
- Não há suporte a HTTP/2, TLS, chunked transfer, proxy ou query parameters.
- Testes de porta ocupada, host mismatch, framing, limite, interface perdida e concorrência permanecem para a fase final. Nenhum teste/build foi executado nesta implementação.
