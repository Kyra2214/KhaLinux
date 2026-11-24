# Permissões web no navegador do aparelho B

**Submódulo:** RED-07  
**Estado:** parcial. Endpoint, validação, eventos e fluxo de navegador existentes foram ligados ao portal HTTPS local em `8443`; a preparação da CA pública ocorre pelo endpoint HTTP restrito em `8080/ca.cer`. A confiança efetiva do navegador e a validação continuam pendentes.

## Fluxo implementado

- B permanece somente no navegador; não instala nem executa KhaLinux.
- Após o login, o portal apresenta um link para a página de permissões. Nela há um botão separado para câmera, outro para microfone e outro para localização. Cada chamada depende de clique próprio e o navegador controla o diálogo correspondente.
- Câmera e microfone usam `getUserMedia`. Quando concedidos, os tracks são interrompidos imediatamente.
- Localização usa `getCurrentPosition`; as coordenadas não são lidas, exibidas, armazenadas ou enviadas.
- A chamada à API do navegador é iniciada imediatamente no gesto do botão, antes de aguardar o registro HTTP, preservando a ativação exigida pelo navegador. O navegador envia ao A somente recurso e estado: requested, granted, denied, cancelled, unavailable ou revoked.
- O endpoint `POST /browser-permission` valida estritamente os dois campos. Não aceita payload de mídia, coordenadas, identificadores de dispositivo ou texto livre.
- O painel RED e a área BLUE exibem o resultado tipado.
- A escolha de cookies permanece independente e não concede permissões.

## Proteção da sessão

O portal entrega um token aleatório em cookie HttpOnly/SameSite Strict. O token não aparece na URL, QR code ou painel. POSTs de login, consentimento e permissão web exigem token, origem e IP do participante. No modo LOCAL_LOGIN, a página e o POST de resultado também exigem login de teste aceito; o consentimento de cookies pode ocorrer antes do login. A sessão expira por inatividade ou duração máxima; tentativas inválidas são limitadas.

## Contexto seguro e permissões

Câmera, microfone e geolocalização exigem contexto seguro nos navegadores modernos. O portal participante é servido em HTTPS local na porta `8443`; se o navegador não aceitar a cadeia do certificado ou `window.isSecureContext` for falso, cada botão registra `UNAVAILABLE` sem chamar a API.

Quando a origem é segura e a API está disponível, cada botão inicia sua chamada real. A página não tenta ocultar os avisos nativos do navegador nem automatizar a aceitação. As solicitações não são agrupadas em um botão único e não são disparadas pelo banner de cookies.

## HTTPS local implementado parcialmente

- O portal participante passa a iniciar em HTTPS local na porta `8443`, com certificado emitido para o IPv4 atual do hotspot.
- O endpoint HTTP `8080` é separado e serve somente o certificado público da CA em `/ca.cer` e `/health`.
- A interface A mostra a URL de preparação e a impressão digital SHA-256 da CA.
- A página de preparação HTTP apresenta instruções de instalação, link para o certificado público e fingerprint para conferência independente.
- O painel registra quando B solicita o certificado e quando uma conexão HTTPS conclui o handshake TLS; somente o segundo evento indica que aquela conexão chegou ao servidor após a validação TLS do cliente.
- O cookie de sessão recebe a flag `Secure` e a validação de `Origin` espera o esquema HTTPS quando o portal é iniciado com TLS.
- O servidor falha ao iniciar o portal se a preparação TLS ou a emissão do certificado não puder ser concluída.

A instalação manual da CA e a confiança real do navegador B ainda precisam ser confirmadas. Não declarar permissões disponíveis apenas porque o servidor HTTPS iniciou.

## Confiança TLS ainda pendente de validação

A preparação HTTPS está implementada parcialmente: a página HTTP `8080/` oferece instruções e download do certificado público em `/ca.cer`; a chave privada da CA permanece no A; o portal participante inicia em HTTPS `8443` com certificado SAN IP. A tela A mostra a impressão digital SHA-256.

O A registra o download da CA, mas isso não prova instalação nem confiança. O primeiro handshake TLS concluído gera `TLS_HANDSHAKE_OBSERVED`, evidência de que aquela conexão foi aceita pelo cliente. A aceitação pode variar por Android/navegador; mesmo um handshake bem-sucedido não garante que câmera, microfone ou localização serão concedidos. A validação física permanece adiada para RED-13. Ver [red-local-tls.md](red-local-tls.md).

## Limites de coleta

- Nenhuma foto, áudio ou coordenada é capturada para o laboratório.
- Tracks de mídia são encerrados imediatamente após a confirmação.
- Coordenadas são descartadas no callback.
- Não existe permissão web genérica de armazenamento que libere a galeria inteira ou o sistema de arquivos. Um seletor de arquivos, se futuramente implementado, deverá ser aberto por ação explícita e permanecer sob controle do navegador. Nenhum seletor é aberto nesta etapa.
- O navegador pode não distinguir “fechar o prompt” de “negar”; nesses casos, o estado reportado segue o resultado real da API.

## Validação pendente

Testes com origem segura e insegura, permissões concedidas/negadas, revogação, navegadores distintos e ausência de APIs ficam para a fase final. Nenhum teste/build foi executado.
