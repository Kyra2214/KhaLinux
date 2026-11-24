# RED-14 — Estabilização da branch

**Estado:** estabilização documental e revisão direcionada concluídas no snapshot registrado abaixo. A entrega de release permanece bloqueada pelos portões de validação e pela dependência externa do RED-07.

## Referências Git

- Repositório: `Kyra2214/KhaLinux`.
- Branch de trabalho: `recovery/red-portal-integration`.
- Snapshot desta atualização: `d445ebcc8f7fb15d5ecac1c9493da5fea72409a2`.
- Base `main`: `a4de5eb7ef3d72404ea1171af5eaa135a48bde0c`.
- Comparação GitHub no snapshot: **296 commits à frente, 0 atrás; 41 arquivos alterados**.
- A branch foi preservada; `main` não foi alterada. Nenhum merge, force push ou PR foi executado.

> A comparação acima é um retrato do snapshot indicado, não uma revisão integral de todos os commits/arquivos. A branch pode receber novos commits; repetir a comparação antes de abrir PR.

## Revisão direcionada registrada

- Manifest: `allowBackup=false`; sem permissões CAMERA, RECORD_AUDIO ou armazenamento para RED; Activity principal exportada apenas como launcher; serviço runtime não exportado; sem provider/receiver declarado.
- Rede: `usesCleartextTraffic=false` para tráfego de saída do app. O portal de entrada continua HTTP local, sem TLS; isso não deve ser descrito como HTTPS.
- Servidor RED: bind ao IPv4/interface identificados; validações de Host, origem, método, Content-Type, framing e limites; timeout, rate limit, token efêmero e expiração; cabeçalhos defensivos e CSP por nonce aleatório por resposta, sem `unsafe-inline` em `script-src`.
- Evidências: Cookie, Authorization e corpos HTTP não entram no histórico; allowlist persistida limitada a Host, Accept, Content-Type e Referer path; User-Agent e Accept-Language excluídos.
- Sessões: schema versionado, migração, retenção limitada, relatório e exclusão; metadados ficam em storage app-private não criptografado, sem credenciais ou tokens persistidos. Expiração/falha inesperada do portal libera também a reserva do hotspot.
- Ciclo de vida: segundo plano/tela bloqueada encerra rede/portal e marca sessão interrompida; reinício recupera sessões sem finalização como interrompidas.
- Navegador B: somente portal, cookies e solicitações web separadas; nenhuma permissão nativa, SAF, leitura de arquivos ou captura de mídia/coordenadas.
- Busca textual direcionada nos arquivos Kotlin do pacote RED/BLUE não encontrou chamadas `Log.*`, `println`, `System.out` ou `printStackTrace`.

Esta revisão é de código/documentação e não equivale a auditoria aprovada, compilação, teste de runtime ou revisão integral do diff.

## Bloqueios e portões restantes

1. **RED-07:** a origem atual é HTTP por IPv4 privado. APIs de câmera, microfone e geolocalização do navegador exigem contexto seguro. Para prompts reais sem configuração manual no B, falta nome de origem e certificado HTTPS confiável que também resolvam no hotspot local. Não foi inventado domínio/certificado.
2. **RED-11/12:** auditoria final, build, lint, testes unitários e integração host/Android ainda não executados, conforme a ordem de execução definida.
3. **RED-13:** testes físicos A+B não executados; permanecem para depois da aprovação do portão automatizado.
4. **Revisão integral:** revisar o diff completo dos 296 commits e 41 arquivos antes do PR, verificando também novos commits posteriores ao snapshot.
5. **Release:** APK, assinatura, SHA-256, versionCode/versionName e PR não foram produzidos. Não gerar nem declarar artefato antes dos portões anteriores.

## Estado de entrega

A parcela documental do RED-14 está concluída neste snapshot: notas, compatibilidade, checklist e registro da estabilização estão alinhados. A entrega publicável permanece pendente. Não declarar release pronta, 100% concluído ou validado enquanto os bloqueios e portões acima permanecerem abertos.
