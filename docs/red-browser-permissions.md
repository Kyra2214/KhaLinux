# Permissões web no navegador do aparelho B

**Submódulo:** RED-07  
**Estado:** parcial. Endpoint, validação, eventos e fluxo de navegador implementados. A solicitação nativa real depende de uma origem HTTPS confiável no aparelho B.

## Fluxo implementado

- B permanece somente no navegador; não instala nem executa KhaLinux.
- Após o login, o portal apresenta um link para a página de permissões. Nela há um botão separado para câmera, outro para microfone e outro para localização. Cada chamada depende de clique próprio e o navegador controla o diálogo correspondente.
- Câmera e microfone usam `getUserMedia`. Quando concedidos, os tracks são interrompidos imediatamente.
- Localização usa `getCurrentPosition`; as coordenadas não são lidas, exibidas, armazenadas ou enviadas.
- O navegador envia ao A somente recurso e estado: requested, granted, denied, cancelled, unavailable ou revoked.
- O endpoint `POST /browser-permission` valida estritamente os dois campos. Não aceita payload de mídia, coordenadas, identificadores de dispositivo ou texto livre.
- O painel RED e a área BLUE exibem o resultado tipado.
- A escolha de cookies permanece independente e não concede permissões.

## Limitação atual: origem HTTP local

O servidor do laboratório publica `http://IPv4-privado:porta`. Câmera, microfone e geolocalização exigem contexto seguro nos navegadores modernos. Quando `window.isSecureContext` é falso, o portal não chama essas APIs e registra `UNAVAILABLE` para cada recurso.

Em uma origem segura, o botão inicia as chamadas reais. A página não tenta ocultar os avisos nativos do navegador nem automatizar a aceitação. As solicitações não são agrupadas em um botão único e não são disparadas pelo banner de cookies.

## Dependência pendente

Para obter prompts nativos reais no B sem instalar aplicativo participante, é necessário publicar o portal em HTTPS com certificado confiável pelo navegador do B. Certificado autoassinado não satisfaz essa condição sem uma etapa adicional de confiança no dispositivo. O usuário ainda não definiu domínio/certificado confiável; não foi inventado um.

## Limites de coleta

- Nenhuma foto, áudio ou coordenada é capturada para o laboratório.
- Tracks de mídia são encerrados imediatamente após a confirmação.
- Coordenadas são descartadas no callback.
- Não existe permissão web que libere a galeria inteira ou o sistema de arquivos. Nenhum seletor de arquivo é aberto.
- O navegador pode não distinguir “fechar o prompt” de “negar”; nesses casos, o estado reportado segue o resultado real da API.

## Validação pendente

Testes com origem segura e insegura, permissões concedidas/negadas, revogação, navegadores distintos e ausência de APIs ficam para a fase final. Nenhum teste/build foi executado.
