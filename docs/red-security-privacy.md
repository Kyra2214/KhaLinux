# RED-11 — Segurança e privacidade: hardening implementado e auditoria pendente

**Estado:** hardening de implementação concluído; auditoria estática, revisão manual e testes negativos transferidos para a fase final, conforme a ordem de execução definida pelo usuário. Não declarar o submódulo validado.

## Superfície Android observada no código

O Manifest atual declara foreground service e notificações para o runtime; INTERNET, estado/alteração de Wi-Fi, estado/alteração de rede; e ACCESS_FINE_LOCATION, solicitada no fluxo de hotspot do aparelho A.

Não declara CAMERA, RECORD_AUDIO, armazenamento ou acesso amplo a arquivos para o laboratório. A Activity principal é exportada por ser o launcher; o serviço do runtime é exported=false. Não há provider declarado neste Manifest. O app usa allowBackup=false.

O app mantém usesCleartextTraffic=false para tráfego HTTP de saída do próprio app. Isso não transforma o servidor local do laboratório em HTTPS: o portal RED continua explicitamente HTTP sem TLS e o navegador B aplica suas próprias regras de origem segura.

## Hardening implementado

- Bind do servidor somente ao IPv4 privado exato da interface Wi-Fi/AP identificada.
- Limites de header/body/resposta/clientes e timeout; rejeição de Host divergente, query string, Transfer-Encoding, Content-Length inválido, headers duplicados e Content-Type não permitido.
- Rejeição de caracteres de controle em valores de headers.
- Allowlist persistida reduzida a Host, Accept, Content-Type e caminho do Referer; User-Agent e Accept-Language deixaram de ser armazenados.
- Cookie, Authorization e corpo HTTP permanecem fora das evidências.
- CSP reforçada com object-src none e base-uri none; adicionados Cross-Origin-Resource-Policy e X-Permitted-Cross-Domain-Policies.
- Sessão com token aleatório efêmero, cookie HttpOnly/SameSite, validação de IP/origem, comparação em tempo constante, expiração e bloqueio temporário com limite de endereços de falha.
- Consentimento, login e permissões web aceitam apenas campos fechados e validados. Nenhuma mídia, coordenada ou arquivo é recebido.
- Histórico limitado a 100 sessões/300 eventos; relatório e exclusão individual/global documentados.

## Limitações e riscos residuais

1. HTTP sem TLS não protege o token de sessão contra observação do tráfego local. O mecanismo reduz submissões cross-origin e operações sem token, mas não substitui HTTPS.
2. O portal por IP privado não habilita APIs web sensíveis em navegadores que exigem contexto seguro. O estado deve ser UNAVAILABLE, não uma concessão presumida.
3. O hotspot depende de callbacks e limitações do fabricante/versão Android.
4. O endereço remoto e alguns metadados HTTP são persistidos para correlação da sessão; cabeçalhos de fingerprinting foram removidos da allowlist.
5. A auditoria final ainda deve revisar componentes, intents, dependências, assets, logs, retenção, sanitização e cenários negativos.

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