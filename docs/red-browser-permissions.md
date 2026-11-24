# Permissões web no navegador do aparelho B

**Submódulo:** RED-07  
**Estado:** parcial. Endpoint, validação, eventos e fluxo de navegador implementados. A arquitetura de HTTPS local com CA própria foi definida em [red-local-tls.md](red-local-tls.md); implementação TLS e validação ainda pendentes.

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

## Limitação atual: origem HTTP local

O servidor do laboratório publica `http://IPv4-privado:porta`. Câmera, microfone e geolocalização exigem contexto seguro nos navegadores modernos. Quando `window.isSecureContext` é falso, ao clicar em cada botão o portal não chama a API e registra `UNAVAILABLE` para aquele recurso.

Em uma origem segura, cada botão inicia sua chamada real. A página não tenta ocultar os avisos nativos do navegador nem automatizar a aceitação. As solicitações não são agrupadas em um botão único e não são disparadas pelo banner de cookies.

## Próxima evolução: HTTPS local

A decisão do laboratório é permitir uma configuração inicial de confiança no aparelho B, instalando a CA pública criada no aparelho A. A chave privada da CA permanece no A. A arquitetura prevê HTTP na porta 8080 apenas para preparação/entrega do certificado público e HTTPS em porta própria (planejada: 8443) para o portal e as APIs sensíveis. O certificado do servidor deverá conter SAN IP correspondente ao IPv4 atual do hotspot. Ver [red-local-tls.md](red-local-tls.md).

A instalação da CA não garante automaticamente que todo navegador/versão Android confiará nela. O estado só será confirmado quando o navegador abrir a origem HTTPS sem erro de certificado. A validação física permanece adiada para RED-13.

## Limites de coleta

- Nenhuma foto, áudio ou coordenada é capturada para o laboratório.
- Tracks de mídia são encerrados imediatamente após a confirmação.
- Coordenadas são descartadas no callback.
- Não existe permissão web genérica de armazenamento que libere a galeria inteira ou o sistema de arquivos. Um seletor de arquivos, se futuramente implementado, deverá ser aberto por ação explícita e permanecer sob controle do navegador. Nenhum seletor é aberto nesta etapa.
- O navegador pode não distinguir “fechar o prompt” de “negar”; nesses casos, o estado reportado segue o resultado real da API.

## Validação pendente

Testes com origem segura e insegura, permissões concedidas/negadas, revogação, navegadores distintos e ausência de APIs ficam para a fase final. Nenhum teste/build foi executado.
