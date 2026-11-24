# KhaLinux 1.0.0-rc.8 (candidata pública)

> Candidata pública, não versão estável. O APK release será assinado e verificado pelo workflow antes da publicação. Os testes funcionais em aparelhos Android físicos ainda estão pendentes; comportamento de hotspot, TLS e permissões pode variar por fabricante, versão do sistema e navegador.

## Destaques

- **Laboratório RED/BLUE de dois aparelhos:** o aparelho A opera o laboratório e observa eventos; o B participa pelo navegador. BLUE distingue fatos observados de interpretação defensiva.
- **Portal local HTTPS:** portal participante em HTTPS na porta 8443, com CA/chaves protegidas pelo Android Keystore. O endpoint HTTP restrito à preparação da CA (porta 8080) serve apenas a página de instruções e o certificado público; o painel informa o fingerprint SHA-256 e registra o handshake TLS concluído.
- **Hotspot e conectividade:** mantém a opção LocalOnlyHotspot e inclui modo alternativo de hotspot do sistema, observação do upstream no aparelho A e heartbeat local autenticado do participante. Isso não garante nem afirma acesso externo à Internet pelo aparelho B.
- **Home Security demonstrativo:** formulário local de e-mail/senha para o cenário de aula; o painel recebe apenas e-mail mascarado e o método demonstrativo. A senha não é incluída em modelos, callbacks, painel, logs, histórico ou relatórios. Facebook/Instagram são exemplos visuais, sem OAuth ou autenticação externa. Não use credenciais reais.
- **Sessões e privacidade:** escolhas de cookies e eventos são apresentados no contexto local; histórico limitado e relatórios não devem ser usados para armazenar dados sensíveis.

## Build e integridade

- `versionName`: `1.0.0-rc.8`
- `versionCode`: `10` (o `8` permanece reservado ao APK automático da `main`; `9` foi usado na rc.7).
- Build `release` ARM64 (`arm64-v8a`), com R8/minificação e redução de recursos (`minify=true`), assinado; o workflow verifica com `apksigner` e gera SHA-256.
- O APK e o arquivo `.sha256` são publicados como assets da release pública.

## Validação e limitações

- A validação automatizada da `main` no commit `a33833f` passou no workflow [45431915888](https://github.com/Kyra2214/KhaLinux/actions/runs/45431915888). O workflow da release executa auditoria estática, build, verificação da assinatura e geração do checksum antes de publicar.
- **Pendente:** teste físico A/B de instalação, confiança manual da CA, prompts de permissões do navegador, compatibilidade de hotspot e encaminhamento externo. Não há matriz de aparelhos/fabricantes certificada.
- A confiança na CA local deve ser configurada manualmente no aparelho B e varia entre versões Android e navegadores. HTTP/8080 é somente para preparar/baixar a CA pública; o portal e a sessão usam HTTPS/8443.
- O formulário é uma simulação local de aula; não use e-mail ou senha reais. Hotspot e acesso externo dependem do Android, fabricante e operadora.

## Verificar o APK

```bash
sha256sum -c KhaLinux-1.0.0-rc.8.apk.sha256
apksigner verify --print-certs KhaLinux-1.0.0-rc.8.apk
```
