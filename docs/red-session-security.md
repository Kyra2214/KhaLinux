# RED-08 — Segurança da sessão e ciclo de vida do portal

**Estado:** implementação em código/documentação concluída; não validada. Testes automatizados, build e validação nos dois aparelhos permanecem para a etapa final.

## Token temporário da sessão

- Ao iniciar o servidor, é gerado um token aleatório de 256 bits com `SecureRandom`.
- O token permanece somente em memória e é invalidado ao parar o servidor.
- O primeiro acesso GET à raiz do portal vincula a sessão ao endereço remoto observado do participante e recebe um cookie `HttpOnly`, `SameSite=Strict`, limitado ao path `/`.
- O token não aparece em URL, QR Code, HTML, JavaScript, eventos, relatórios ou cabeçalhos permitidos.
- O navegador envia o cookie automaticamente nas operações same-origin. O JavaScript não consegue ler o token por causa de `HttpOnly`.

## Autorização das operações mutáveis

Os endpoints POST de login, consentimento de cookies e estado de permissões web exigem:
- sessão ainda válida;
- endereço remoto igual ao participante vinculado no primeiro acesso;
- origem HTTP correspondente ao endpoint do portal, obtida de `Origin` ou, como alternativa, de `Referer`;
- cookie de sessão com token correspondente, comparado por `MessageDigest.isEqual`.

Falhas são diferenciadas: token/endereço inválido, origem inválida, sessão expirada e excesso de tentativas. O servidor aplica bloqueio temporário após cinco falhas de autenticação por endereço remoto. A observação HTTP registra apenas status e motivo; Cookie e Authorization continuam fora da allowlist.

## Tempo de vida e encerramento

- Inatividade máxima: 30 minutos.
- Duração máxima absoluta: 2 horas.
- Cookie de sessão: 30 minutos; respostas bem-sucedidas ao participante renovam a validade enquanto a sessão estiver ativa.
- O loop do servidor verifica expiração e encerra o portal quando o prazo é atingido.
- `stop()` fecha o socket de escuta e os sockets de clientes, limpa a referência do worker, endpoint, interface, token, horários, participante vinculado e tentativas falhas.
- Encerramento repetido não recria nem mantém recursos. Recuperação de processo marca sessões antigas como interrompidas e não reativa a rede.

## Limitação de segurança do transporte

O portal continua usando HTTP em IPv4 privado, sem TLS. O cookie é HttpOnly e SameSite, mas **não oferece confidencialidade contra alguém capaz de observar o tráfego local**. Esta proteção limita submissões cross-origin e operações sem token no escopo do laboratório; não substitui HTTPS nem deve ser apresentada como autenticação forte contra um atacante com acesso à rede.

## Validação acumulada para a fase final

- Token novo por sessão, ausência em URLs/logs/relatórios e invalidação no stop.
- POST sem cookie, token incorreto, IP diferente e origem externa rejeitados.
- Limite de tentativas e bloqueio temporário.
- Expiração por inatividade e por tempo absoluto.
- Renovação do cookie em resposta válida.
- Stop repetido, perda de rede, processo interrompido e recuperação sem reativação automática.
- Confirmar que Cookie e token não aparecem nas evidências persistidas.

Nenhum teste, build ou compilação foi executado durante esta implementação.
