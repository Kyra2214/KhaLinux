# RED-14 — Estabilização da branch

**Estado:** estabilização documental e revisão direcionada do escopo RED/BLUE concluídas; entrega de release permanece bloqueada por RED-07 e pela validação final.

## Referências Git

- Branch: `recovery/red-portal-integration`.
- Base `main`: `a4de5eb7ef3d72404ea1171af5eaa135a48bde0c`.
- Snapshot revisado antes deste documento: `d21a96cc7f089ee9e071ffabbc45159fc631157d`.
- Comparação nesse snapshot: 258 commits à frente, 0 atrás; 39 arquivos alterados.
- `main` não foi alterada; nenhum merge, force push ou PR foi executado.

## Revisão direcionada concluída

- Manifest: `allowBackup=false`; sem permissões CAMERA, RECORD_AUDIO ou armazenamento para RED; Activity principal exportada apenas como launcher; serviço runtime não exportado; sem provider/receiver declarado.
- Rede: `usesCleartextTraffic=false` para tráfego de saída do app. O portal de entrada continua HTTP local, sem TLS; isso não deve ser descrito como HTTPS.
- Servidor RED: bind ao IPv4/interface identificados; Host, origem, método, Content-Type, framing e limites validados; timeout, rate limit, token efêmero e expiração; cabeçalhos defensivos e CSP por nonce.
- Evidências: Cookie, Authorization e corpos HTTP não entram no histórico; allowlist persistida limitada a Host, Accept, Content-Type e Referer path; User-Agent e Accept-Language excluídos.
- Sessões: schema versionado, migração, retenção limitada, relatório e exclusão; metadados ficam em storage app-private não criptografado, sem credenciais ou tokens persistidos.
- Ciclo de vida: segundo plano/tela bloqueada encerra rede/portal e marca sessão interrompida; reinício recupera sessões sem finalização como interrompidas.
- Navegador B: somente portal, cookies e solicitações web separadas; nenhuma permissão nativa, SAF, leitura de arquivos ou captura de mídia/coordenadas.
- Busca textual direcionada nos arquivos Kotlin do pacote RED/BLUE não encontrou chamadas `Log.*`, `println`, `System.out` ou `printStackTrace`.

Esta revisão é de código e documentação. Não é auditoria aprovada, compilação ou evidência de funcionamento.

## Bloqueios e pendências

1. **RED-07:** a origem atual é HTTP por IPv4 privado. APIs de câmera, microfone e geolocalização do navegador exigem contexto seguro. Para prompts nativos reais sem configuração manual no B, falta um nome de origem e certificado HTTPS confiável que também resolvam no hotspot local. Não foi inventado domínio/certificado.
2. **Validação final:** build, lint, testes unitários, testes host/Android, auditoria estática/manual e testes físicos A+B não foram executados, conforme a ordem definida.
3. **Release:** APK, assinatura, SHA-256, versionCode/versionName e PR não foram produzidos. Dependem dos portões finais.
4. **Revisão completa do diff:** a revisão direcionada cobriu os arquivos RED/BLUE, Manifest e configuração Android; a revisão integral do diff de 258 commits permanece no checklist final.

## Estado de entrega

A branch está preservada e pronta para continuar a estabilização documental. Não declarar release pronta, 100% concluído ou validado enquanto RED-07 e os portões finais estiverem pendentes.
