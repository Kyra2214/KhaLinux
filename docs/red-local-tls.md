# HTTPS local e confiança da autoridade do laboratório

**Submódulo:** RED-07  
**Estado:** requisito obrigatório do RED-07; arquitetura offline definida; implementação TLS e validação ainda pendentes.  
**Topologia:** A hospeda o portal e mantém a chave privada da autoridade; B participa exclusivamente pelo navegador e pela tela de configurações do Android para a instalação inicial da CA.

## Objetivo

Permitir que o portal continue totalmente local e funcional sem internet, sem domínio público, mas seja servido por HTTPS com um certificado cuja cadeia seja confiável pelo navegador do aparelho B. HTTPS local é obrigatório para concluir o fluxo de permissões web.

A porta não determina o protocolo. O endpoint HTTPS será separado do endpoint HTTP de preparação, porque o servidor atual é HTTP puro e não pode ser tratado como TLS apenas por mudar a porta.

## Fluxo planejado

1. A inicia a sessão RED e mantém o servidor HTTP local de preparação na porta 8080.
2. A gera ou recupera uma autoridade certificadora (CA) exclusiva do laboratório. A chave privada da CA permanece protegida no aparelho A e nunca é entregue ao B.
3. A emite um certificado de servidor com SAN correspondente ao IPv4 atual do hotspot. O certificado deve ser renovado quando o endereço mudar ou expirar.
4. A disponibiliza somente o certificado público da CA para instalação no B. A tela do operador mostra a impressão digital SHA-256, que deve ser conferida por canal independente antes da instalação; o download pelo mesmo Wi-Fi não prova autenticidade.
5. O operador instala a CA no B pelas configurações de segurança do Android, usando o fluxo nativo do sistema. O B não instala o KhaLinux, não executa scripts e não importa chave privada.
6. O servidor HTTPS local inicia em porta própria (planejada: 8443), vinculado ao mesmo IPv4 específico do hotspot. O endereço HTTPS é exibido no A.
7. B abre o endereço HTTPS no navegador. O fluxo só é considerado pronto quando o navegador aceita a cadeia sem alerta e o certificado corresponde ao IP acessado. A instalação da CA no Android, isoladamente, não comprova confiança efetiva do navegador.
8. A página solicita câmera, microfone e localização separadamente, cada uma após clique explícito. O navegador decide se exibe o prompt e o participante decide permitir ou negar.
9. O relatório registra somente o recurso e o resultado efetivamente observado. Nenhuma mídia ou coordenada é transmitida ao A.

## Separação dos serviços

| Serviço | Porta planejada | Função |
|---|---:|---|
| HTTP local | 8080 | Preparação, instruções e entrega do certificado público da CA; sem solicitação de permissões sensíveis. |
| HTTPS local | 8443 | Portal participante e APIs de permissões web, depois que B confiar na CA. |

O servidor HTTP não deve redirecionar automaticamente para HTTPS antes da instalação da CA. Depois da preparação, o endereço HTTPS deve ser mostrado claramente no painel do operador e no portal de preparação.

## Segurança da CA

- A chave privada da CA nunca é exportada, exibida, enviada ao B ou incluída em relatórios.
- O certificado público da CA é o único arquivo destinado ao B.
- A impressão digital SHA-256 deve ser mostrada no A e conferida durante a instalação no B.
- A CA deve ser identificada como autoridade local de laboratório, com validade limitada e instruções claras para removê-la ao terminar.
- A instalação de uma CA raiz amplia a confiança do dispositivo e não deve ser descrita como uma simples permissão do site.
- A instalação é uma configuração explícita do aparelho B, realizada pelo operador nas configurações do Android; não será automatizada pela página.
- A CA deve ter validade limitada e uso exclusivo do laboratório. A rotação/revogação deve ser documentada quando houver suspeita de exposição.
- O operador deve remover a CA do B ao encerrar o ciclo de laboratório, se ela não for mais necessária.
- O servidor deve falhar fechado se não conseguir emitir/validar certificado para o IP atual. Não voltar silenciosamente a HTTP para APIs sensíveis.

## Consentimentos e permissões

- “Aceitar todos” no banner registra somente as categorias de cookies apresentadas pelo próprio portal.
- Câmera, microfone e localização continuam sendo solicitações independentes do navegador, cada uma iniciada por ação explícita.
- Não abrir os três prompts em sequência automática nem como efeito colateral do aceite de cookies.
- “Armazenamento” não é uma permissão web genérica equivalente a câmera/localização. Para selecionar um arquivo, o site pode abrir um seletor após ação explícita; o navegador controla a seleção e o portal só recebe o arquivo escolhido, se esse recurso for implementado. Nenhum arquivo será enviado ou lido nesta etapa.
- Não declarar permissão concedida com base apenas no clique do botão. O resultado vem da API do navegador e pode ser negado, bloqueado por política, indisponível ou não suportado.

## Limitações e condições de aceite

- A instalação de CA de usuário pode variar por versão Android, fabricante e navegador. A existência do certificado nas configurações do sistema não prova, por si só, que o navegador o aceita para a origem usada.
- O certificado precisa conter SAN IP exato; CN isolado não basta.
- A confiança TLS, o contexto seguro e a disponibilidade das APIs são condições diferentes. Mesmo com HTTPS confiável, o Android, o navegador, as permissões do app Chrome e as políticas do usuário podem bloquear o recurso.
- A etapa só será considerada validada após teste físico no B, conforme RED-13. Não executar esse teste antes da conclusão da implementação e do portão RED-12.
- A primeira etapa do laboratório permanece offline: `LocalOnlyHotspot` e o portal devem operar sem conectividade externa. O compartilhamento de internet será avaliado somente em etapa posterior e não é pré-requisito para HTTPS.
- A comunicação HTTP de preparação na porta `8080` não será usada para transmitir mídia, coordenadas ou resultados de permissões. O portal participante e seus endpoints sensíveis funcionarão exclusivamente em HTTPS na porta `8443`.
- Enquanto não validado, o painel deve distinguir “CA preparada”, “CA instalada pelo operador”, “HTTPS iniciado” e “navegador confirmou acesso seguro”; não presumir uma etapa a partir da anterior.

## Critérios técnicos adicionais de implementação

- Usar TLS moderno suportado pela plataforma e configuração de servidor compatível com Android; não implementar criptografia própria.
- A chave privada da CA deve ser gerada e armazenada em área privada do app, com acesso restrito; nunca gravar no repositório, logs, exportações ou backup compartilhado.
- O certificado de servidor deve incluir `subjectAltName` do tipo IP para o IPv4 exato do hotspot; não depender apenas de CN ou de hostname inventado.
- Se o IPv4 mudar, emitir novo certificado de servidor antes de anunciar o novo endereço HTTPS.
- Validar expiração, cadeia, SAN e bind antes de marcar HTTPS como pronto.
- Não assumir que todos os navegadores aceitam CA de usuário. A compatibilidade deve ser documentada por navegador/versão após validação física.
- O estado de operação deve distinguir: CA gerada, CA pública exportada, CA instalada pelo operador, certificado do servidor emitido, HTTPS escutando e origem confirmada pelo navegador.

## Referências internas

- [Permissões web no navegador B](red-browser-permissions.md)
- [Servidor HTTP local RED](red-http-server.md)
- [Matriz de validação física](red-physical-validation.md)
