# RED-10 — Interface do operador e acessibilidade operacional

**Estado:** implementação em código/documentação concluída; não validada. Testes de ViewModel, estados, rotação, navegação e acessibilidade ficam para a etapa final.

## Fonte de verdade

- `RedViewModel` publica `RedUiState` como StateFlow; RED observa esse estado sem manter uma cópia independente de hotspot, servidor, endpoint ou participante.
- `RedHotspotController` informa estados reais por callback Android.
- `RedPortalServer` informa endereço e requisições recebidas; atividade do participante só é confirmada após uma requisição HTTP.
- `RedLiveEvidenceStore` compartilha os mesmos eventos da sessão com BLUE; alternar de área não recria nem duplica a evidência.
- O aparelho B permanece navegador-only. Toda a observabilidade, estado e explicação ficam no A.

## Estados visíveis

A tela diferencia:
- solicitação de permissão Android pendente;
- solicitação de hotspot enviada, aguardando callback;
- hotspot confirmado;
- interface/IPv4 em descoberta;
- servidor em inicialização;
- portal ativo aguardando primeiro acesso;
- atividade HTTP observada;
- encerramento de recursos;
- falha com instrução de recuperação.

O botão de início fica desabilitado enquanto a solicitação de permissão ou hotspot está pendente. A tela não mostra hotspot/portal ativo antes do callback real. A solicitação de permissão preserva seu estado transitório em recriação e o resolve ao retornar à Activity.

## Informações e limites

- Quando disponíveis, mostra SSID, parâmetros retornados pelo Android, interface, IPv4, porta e URL/QR Code.
- Mostra endereço remoto e contagem de requisições somente depois de tráfego HTTP efetivamente recebido.
- Exibe portal **HTTPS local**, URL HTTP restrita para baixar a CA pública, fingerprint SHA-256 e estado de handshake observado; a confiança do navegador não é presumida antes da primeira conexão TLS.
- Oferece modo alternativo de hotspot do sistema iniciado nas Configurações do Android; exibe upstream validado no A sem afirmar que B recebeu internet.
- Exibe horário do último heartbeat autenticado de B; o indicador confirma alcance local do portal, não acesso externo, e não transforma ausência de heartbeat em desconexão confirmada.
- Explica que o painel observa somente requisições destinadas ao portal; não captura tráfego externo nem oferece lista universal de clientes Wi-Fi.
- Erros de inicialização/encerramento permanecem visíveis e orientam o operador a parar recursos restantes ou tentar novamente.
- QR Code tem descrição acessível; controles usam rótulos textuais e estados anunciados.

## Ciclo de vida e navegação

- O ViewModel preserva o estado durante recriação de Activity/rotação.
- Ao ir para segundo plano ou bloquear a tela, a sessão ativa é encerrada como interrompida e os recursos de rede são parados.
- Alternar RED → BLUE mantém a sessão ativa e usa o mesmo store de evidências.
- Encerrar ou sair da operação para o catálogo encerra o laboratório conforme o fluxo de sessão.

## Validação acumulada

- Estados de permissão, hotspot, descoberta, servidor, participante, erro e encerramento.
- Nenhum estado de sucesso antes do callback.
- Rotação/recriação, retorno de diálogo de permissão, segundo plano e navegação RED/BLUE.
- Mensagens e ações de recuperação em falhas.
- Leitura de QR Code e navegação por leitor de tela.
- Nenhum teste, build ou compilação foi executado durante a implementação.
