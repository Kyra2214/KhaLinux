# Interface do operador RED

**Submódulo:** RED-10  
**Estado:** implementação em código/documentação concluída; testes de estado, rotação, navegação e acessibilidade ficam para a fase final.

## Divisão A/B

- A concentra configuração, início/parada do hotspot, endereço/QR, estado do servidor, interface/IP, observações HTTP, consentimento, permissões e relatórios.
- B permanece no navegador: conexão Wi-Fi, login de teste, escolha de cookies e página com ações individuais para permissões web quando o navegador oferecer suporte.

## Estados apresentados

A interface usa `RedUiState.networkState` como fonte para:
- solicitação de hotspot;
- hotspot confirmado;
- descoberta da interface;
- inicialização do servidor;
- portal aguardando participante;
- atividade HTTP observada;
- encerramento;
- erro.

SSID, segurança, banda, interface e IPv4 são exibidos somente quando fornecidos pelo callback/API. O endereço e QR só aparecem depois do bind do servidor ser confirmado. A interface não apresenta o portal como ativo antes desse resultado.

## Permissão do operador

O diálogo Android é solicitado somente após a ação explícita de iniciar hotspot. O botão distingue pedido pendente, concessão, recusa e necessidade de abrir Configurações. Revogação é observada ao retornar ao app.

## Diagnóstico e recuperação

- Erro de hotspot/servidor apresenta mensagem e ação de parar/liberar recursos ou tentar novamente.
- O aviso HTTP sem TLS permanece visível junto ao endereço.
- O painel informa que observa somente requisições dirigidas ao portal, não todos os clientes Wi-Fi nem tráfego externo.
- A tela exibe as 100 observações HTTP recentes, status de resposta, rejeição, falha e cabeçalhos permitidos.
- Relatório e exportação permanecem acessíveis na sessão.

## Rotação, segundo plano e BLUE

- Estado pendente do diálogo de permissão usa `rememberSaveable`; estado operacional reside no ViewModel.
- Ao ir para segundo plano ou bloquear a tela, hotspot/portal são encerrados e a sessão fica `INTERRUPTED`.
- RED e BLUE compartilham evidência por `RedLiveEvidenceStore`; alternar a perspectiva não cria eventos novos nem altera os originais.
- O QR possui descrição acessível; botões e ações usam rótulos textuais.

## Validação pendente

Testes de estados, callback atrasado, erro/cancelamento/retry, rotação, navegação RED/BLUE, leitor de tela e ausência de sucesso prematuro ficam para a fase final. Nenhum teste/build/compilação foi executado.
