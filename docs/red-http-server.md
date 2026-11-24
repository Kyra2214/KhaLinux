# Servidor HTTP local RED

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
- Paths absolutos, traversal, barra invertida, path excessivo e query string são rejeitados.
- Métodos aceitos: GET, POST e HEAD nas rotas previstas. Demais métodos recebem 405.
- Respostas têm tamanho limitado, Cache-Control no-store, nosniff, Referrer-Policy, X-Frame-Options e CSP local.

## Observabilidade e privacidade

- Toda requisição cujo cabeçalho foi recebido gera uma observação única, inclusive as rejeitadas.
- O A recebe método, path, status, Content-Type, bytes, IP remoto e allowlist de cabeçalhos.
- Cookie, Authorization, corpo de formulário e valores de cookies não são registrados.
- Referer é reduzido ao path; Host e metadados não sensíveis podem aparecer no painel.
- O servidor serve somente páginas/recursos locais embarcados; não busca páginas externas.

## HTTP sem TLS

O portal é HTTP local, não HTTPS. O portal avisa o participante para usar somente dados de teste. Não inserir credenciais reais. As APIs web sensíveis do navegador podem ficar indisponíveis por não ser uma origem segura.

## Limitações

- A descoberta de interface depende de nomes reconhecidos pelo controlador; fabricantes com nomes não reconhecidos falham fechados.
- Timeout de leitura é aplicado por operação de socket; não representa deadline absoluto de toda a requisição.
- Não há suporte a HTTP/2, TLS, chunked transfer, proxy ou query parameters.
- Testes de porta ocupada, host mismatch, framing, limite, interface perdida e concorrência permanecem para a fase final. Nenhum teste/build foi executado nesta implementação.
