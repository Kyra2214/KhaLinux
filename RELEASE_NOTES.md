# KhaLinux 1.0.0-rc.7 (candidata a release)

> Esta é uma candidata pública, não uma versão estável. O APK é compilado como `release`, assinado e verificado no CI. A validação funcional em aparelhos físicos ainda está pendente; fabricantes e versões Android podem variar.

## Destaques

- **Área RED — laboratório Wi-Fi local:** demonstração controlada com dois aparelhos, LocalOnlyHotspot, portal HTTP local, QR Code e observação das requisições realmente recebidas pelo servidor.
- **Modo participante:** demonstrações de permissões nativas de câmera, microfone, localização e seletor de arquivos no aparelho participante. O painel recebe somente o tipo e o estado das ações; amostras de microfone são descartadas e conteúdo de arquivos não é lido nem transmitido.
- **Área BLUE — análise defensiva:** visualiza a sessão RED, linha do tempo, requisições permitidas e estados de permissões, distinguindo eventos observados de recomendações.
- **Histórico e relatórios locais:** sessões e eventos limitados, com exportação Markdown sem conteúdo sensível de formulários, tokens, cookies, arquivos, áudio, vídeo ou coordenadas.

## Build e integridade

- `versionName`: `1.0.0-rc.7`
- `versionCode`: `9` (o `8` permanece reservado ao APK automático da `main`).
- Build `release` ARM64 com R8/minificação; assinatura validada com `apksigner`.
- O APK e o arquivo `.sha256` são publicados como assets desta release.

## Validação e limitações

- Os testes unitários e as verificações de host do CI passaram para a `main`; o workflow de release compila e verifica a assinatura antes da publicação.
- A instalação, atualização, hotspot e experiência ainda precisam ser testados em aparelhos Android físicos. Trate esta versão como candidata e reporte incompatibilidades antes da versão estável.
- O portal de demonstração usa HTTP sem TLS e fica restrito ao endereço da interface Wi-Fi/AP; não captura tráfego geral nem deve ser usado com credenciais reais ou em redes/aparelhos sem autorização.

## Verificar o APK

```bash
sha256sum -c KhaLinux-1.0.0-rc.7.apk.sha256
apksigner verify --print-certs KhaLinux-1.0.0-rc.7.apk
```
