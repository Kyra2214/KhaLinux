# Matriz de permissões RED/BLUE

**Submódulo:** RED-02  
**Estado:** implementação em código/documentação concluída; validação acumulada para a etapa final.

## Aparelho A — aplicativo operador

| Permissão | Classe/uso | Momento | Observação |
|---|---|---|---|
| `INTERNET` | Normal | Manifesto | Servidor local e recursos de rede do aplicativo |
| `ACCESS_WIFI_STATE` | Normal | Manifesto | Consultar estado Wi-Fi |
| `CHANGE_WIFI_STATE` | Normal | Manifesto | Solicitar LocalOnlyHotspot |
| `ACCESS_NETWORK_STATE` | Normal | Manifesto | Estado de rede |
| `CHANGE_NETWORK_STATE` | Normal | Manifesto | Mantida para compatibilidade com componentes de rede do app; não implica privilégio de tethering |
| `ACCESS_FINE_LOCATION` | Perigosa/runtime | Somente ao iniciar hotspot, se o Android exigir | Solicitada junto com coarse; não é usada para consultar coordenadas |
| `ACCESS_COARSE_LOCATION` | Perigosa/runtime | Somente ao iniciar hotspot, se o Android exigir | Permite registrar concessão aproximada; a API de hotspot ainda pode impor requisitos adicionais |

O app solicita fine e coarse juntas no aparelho A, no momento em que o operador inicia o hotspot. A interface explica que o Android pode exigir a permissão para disponibilizar a rede e que o KhaLinux não consulta coordenadas. A resposta é observada novamente ao retornar à Activity; concessão, recusa, recusa permanente e revogação são distinguidas. Quando a solicitação deixa de ser apresentada pelo Android, o A oferece atalho para as Configurações do aplicativo.

O resultado de permissão não é confundido com sucesso do hotspot. A confirmação da rede depende do callback real de `LocalOnlyHotspot`. Se o Android negar ou a API falhar, o painel mostra o erro recebido.

## Permissões gerais do KhaLinux

As permissões de foreground service e notificações pertencem ao ciclo de vida do runtime Linux e não são solicitadas pelo fluxo RED. Elas não devem ser descritas como permissões do aparelho B.

## Aparelho B — navegador

B não instala nem executa KhaLinux, portanto não recebe permissões do Manifest do app. O navegador pode apresentar solicitações próprias para câmera, microfone ou localização apenas quando a origem for segura, a API estiver disponível e houver ação explícita. O portal HTTP em IP privado não deve presumir que essas APIs estarão disponíveis.

O aviso de cookies não concede nenhuma permissão do navegador ou do Android. O banner registra somente a escolha das categorias de cookies.

## Recursos removidos do fluxo RED

- CAMERA e RECORD_AUDIO removidas do Manifest do app operador.
- Dependências CameraX removidas.
- Seletor SAF removido do fluxo de B; nenhum arquivo é solicitado.
- Nenhum pedido de câmera, microfone, localização ou arquivos é feito ao abrir RED.

## Compatibilidade

O app mantém `targetSdk=28` por decisão arquitetural do runtime PRoot. Se o target SDK for alterado, revisar novamente as exigências de Wi-Fi/nearby devices por versão Android. Não declarar compatibilidade com permissões futuras sem conferir a API usada.

## Validação pendente

Testes de concessão, recusa, revogação, retorno de Configurações, comportamento por versão Android e os testes físicos ficam para a etapa final. Nenhum teste ou build foi executado nesta implementação.
