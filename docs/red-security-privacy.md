# RED-11 — Segurança e privacidade: hardening implementado e auditoria pendente

**Estado:** hardening de implementação concluído; auditoria estática, revisão manual e testes negativos transferidos para a fase final, conforme a ordem de execução definida pelo usuário. Não declarar o submódulo validado.

## Superfície Android observada no código

O Manifest atual declara foreground service e notificações para o runtime; INTERNET, estado/alteração de Wi-Fi, estado/alteração de rede; e ACCESS_FINE_LOCATION, solicitada no fluxo de hotspot do aparelho A.

Não declara CAMERA, RECORD_AUDIO, armazenamento ou acesso amplo a arquivos para o laboratório. A Activity principal é exportada por ser o launcher; o serviço do runtime é exported=false. Não há provider declarado neste Manifest. O app usa allowBackup=false.

O app mantém usesCleartextTraffic=false para tráfego HTTP de saída do próprio app. O portal participante RED usa HTTPS local em `8443`; HTTP em `8080` é um endpoint separado e restrito à entrega do certificado público da CA e health check. A confiança dessa CA no navegador B continua sendo uma condição explícita e não é inferida pela instalação.

## Hardening implementado

- Bind do servidor somente ao IPv4 privado exato da interface Wi-Fi/AP identificada.
- Limites de header/body/resposta/clientes e timeout; rejeição de Host divergente, query string, Transfer-Encoding, Content-Length inválido, headers duplicados e Content-Type não permitido.
- Rejeição de caracteres de controle em valores de headers.
- Allowlist persistida reduzida a Host, Accept, Content-Type e caminho do Referer; User-Agent e Accept-Language deixaram de ser armazenados.
- Cookie, Authorization e corpo HTTP permanecem fora das evidências.
- CSP por resposta usa nonce aleatório em script-src, sem unsafe-inline; botões de cookies usam addEventListener em vez de handlers inline. CSP inclui object-src none e base-uri none; headers Cross-Origin-Resource-Policy e X-Permitted-Cross-Domain-Policies também são enviados.
- Sessão com token aleatório efêmero, cookie HttpOnly/SameSite, validação de IP/origem, comparação em tempo constante, expiração e bloqueio temporário com limite de endereços de falha.
- Consentimento, login e permissões web aceitam apenas campos fechados e validados. Nenhuma mídia, coordenada ou arquivo é recebido.
- Histórico limitado a 100 sessões/300 eventos; relatório e exclusão individual/global documentados.

## Limitações e riscos residuais

1. O endpoint de preparação HTTP não protege a confidencialidade do certificado público, que não é secreto; sua autenticidade depende da conferência independente do fingerprint SHA-256 antes de instalar a CA.
2. A confiança em CA de usuário varia por versão Android/navegador. Um handshake TLS concluído é evidência de que aquela conexão foi aceita, mas não garante que as APIs de câmera, microfone ou localização serão concedidas.
3. HTTPS local por IP exige SAN IP correspondente ao endereço exato e renovação do certificado de servidor quando o IP mudar ou o certificado expirar.
3. O hotspot depende de callbacks e limitações do fabricante/versão Android.
4. O endereço remoto e alguns metadados HTTP são persistidos para correlação da sessão; cabeçalhos de fingerprinting foram removidos da allowlist.
5. A auditoria final ainda deve revisar componentes, intents, dependências, assets, logs, retenção, sanitização e cenários negativos.

## Hotspot do sistema e upstream

- O modo de hotspot do sistema é ativado manualmente pelo operador nas Configurações do Android; o app não usa reflexão, APIs ocultas ou permissões privilegiadas de tethering.
- O KhaLinux observa somente nomes de interface e IPv4 locais necessários para vincular o portal; SSID/senha permanecem sob controle do Android e não são presumidos.
- `ConnectivityManager` observa somente capacidades da rede padrão no aparelho A. `NET_CAPABILITY_VALIDATED` significa que A possui upstream validado, não prova que B conseguiu navegar externamente.
- Se mais de uma interface/IPv4 for candidato, a descoberta falha fechada e não inicia o portal em endereço ambíguo.
- Encerrar o portal não desliga o hotspot do sistema; o operador controla sua ativação/desativação nas Configurações.

## Checklist de auditoria final (não executado nesta fase)

- [ ] Conferir exported components, intents, providers, services e permissões do Manifest.
- [ ] Verificar dependências e assets embarcados.
- [ ] Procurar segredos, credenciais, tokens e dados pessoais em código/configuração.
- [ ] Confirmar ausência de logging/persistência de corpos, cookies, Authorization, mídia, coordenadas e arquivos.
- [ ] Executar testes negativos de validação, autenticação, origem, rate limit e expiração.
- [ ] Revisar HTML/JS/CSS, CSP, Permissions-Policy e headers.
- [ ] Confirmar que não há proxy, captura de tráfego externo ou coleta de credenciais de terceiros.
- [ ] Registrar achados, severidade, correção e risco residual aceito.

Nenhuma auditoria, varredura, compilação ou bateria de testes foi executada durante a implementação deste submódulo.