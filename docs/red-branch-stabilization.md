# RED-14 — Estabilização da branch

**Estado:** estabilização documental e revisão direcionada concluídas no snapshot registrado abaixo. A entrega de release permanece bloqueada pelos portões de validação final, incluindo a confirmação de confiança da CA no navegador B.

## Referências Git

- Repositório: `Kyra2214/KhaLinux`.
- Branch de trabalho: `recovery/red-portal-integration`.
- Snapshot desta atualização: `72f612deb08c425fdb1e1418bb9be7a98d1511ab`.
- Base `main`: `a4de5eb7ef3d72404ea1171af5eaa135a48bde0c`.
- Comparação GitHub no snapshot: **520 commits à frente, 0 atrás; 48 arquivos alterados**.
- A branch foi preservada; `main` não foi alterada. Nenhum merge, force push ou PR foi executado.

> A comparação acima é um retrato do snapshot indicado, não uma revisão integral de todos os commits/arquivos. A branch pode receber novos commits; repetir a comparação antes de abrir PR.

## Revisão direcionada registrada

- Manifest: `allowBackup=false`; sem permissões CAMERA, RECORD_AUDIO ou armazenamento para RED; Activity principal exportada apenas como launcher; serviço runtime não exportado; sem provider/receiver declarado.
- Rede: `usesCleartextTraffic=false` para tráfego de saída do app. O portal participante usa HTTPS local/8443; HTTP/8080 serve somente à preparação da CA pública.
- Servidor RED: bind ao IPv4/interface identificados; validações de Host, origem, método, Content-Type, framing e limites; timeout, rate limit, token efêmero e expiração; cabeçalhos defensivos e CSP por nonce aleatório por resposta, sem `unsafe-inline` em `script-src`.
- Evidências: Cookie, Authorization e corpos HTTP não entram no histórico; allowlist persistida limitada a Host, Accept, Content-Type e Referer path; User-Agent e Accept-Language excluídos.
- Sessões: schema versionado, migração, retenção limitada, relatório e exclusão; metadados ficam em storage app-private não criptografado, sem credenciais ou tokens persistidos. Expiração/falha inesperada do portal libera também a reserva do hotspot.
- Ciclo de vida: segundo plano/tela bloqueada encerra rede/portal e marca sessão interrompida; reinício recupera sessões sem finalização como interrompidas.
- Navegador B: somente portal, cookies e solicitações web separadas; nenhuma permissão nativa, SAF, leitura de arquivos ou captura de mídia/coordenadas.
- Busca textual direcionada nos arquivos Kotlin do pacote RED/BLUE não encontrou chamadas `Log.*`, `println`, `System.out` ou `printStackTrace`.

Esta revisão é de código/documentação e não equivale a auditoria aprovada, compilação, teste de runtime ou revisão integral do diff.

## Bloqueios e portões restantes

1. **RED-07:** código HTTPS local offline implementado. Falta validar no B se a CA instalada manualmente é aceita pelo navegador, se SAN IP corresponde e se câmera/microfone/localização exibem prompts reais. O handshake TLS observado é a evidência de confiança por conexão.
2. **RED-11/12:** auditoria final, build, lint, testes unitários e integração host/Android ainda não executados, conforme a ordem de execução definida.
3. **RED-13:** testes físicos A+B não executados; permanecem para depois da aprovação do portão automatizado.
4. **Revisão integral:** revisar o diff completo dos 520 commits e 48 arquivos antes do PR, verificando também novos commits posteriores ao snapshot.
5. **Release:** APK, assinatura, SHA-256, versionCode/versionName e PR não foram produzidos. Não gerar nem declarar artefato antes dos portões anteriores.

## Estado de entrega

A parcela documental do RED-14 está concluída neste snapshot: notas, compatibilidade, checklist e registro da estabilização estão alinhados. A entrega publicável permanece pendente. Não declarar release pronta, 100% concluído ou validado enquanto os bloqueios e portões acima permanecerem abertos.
