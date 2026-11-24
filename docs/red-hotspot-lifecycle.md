# Ciclo de vida do hotspot RED

**Submódulo:** RED-03  
**Estado:** implementação em código/documentação concluída; validação host/Android e testes físicos acumulados para a etapa final.

## API e configuração

- O controlador usa `WifiManager.startLocalOnlyHotspot` e fecha a reserva por `LocalOnlyHotspotReservation.close()`. O Android pode compartilhar o hotspot com outras reservas de aplicativos; fechar a reserva do KhaLinux não garante que a interface física desapareça se outro app ainda a mantiver.
- Em Android 11/API 30 ou superior, lê a configuração por `getSoftApConfiguration()`.
- Em Android 9/API 28 e Android 10/API 29, usa `getWifiConfiguration()` como compatibilidade legada; esse getter foi depreciado a partir da API 30.
- SSID, passphrase e tipo de segurança são exibidos somente quando a configuração da API os fornece.
- Banda permanece nula/indisponível nesta versão, sem inferir 2,4 GHz ou 5 GHz.

## Modo alternativo RED-15: hotspot do sistema

`LocalOnlyHotspot` continua sendo o modo isolado padrão e não oferece upstream. Para uma sessão em que B deverá receber internet posteriormente sem trocar de Wi-Fi, o operador pode escolher o modo de hotspot do sistema:

1. KhaLinux registra os IPv4 Wi-Fi atuais como baseline.
2. Abre as Configurações de tethering do Android; o operador ativa o hotspot manualmente.
3. Ao retornar, o operador solicita a descoberta. O controlador compara interfaces/IPv4 e só aceita um endereço novo e inequívoco.
4. O portal HTTPS e o endpoint de preparação são vinculados ao IPv4 identificado.
5. Um callback público de conectividade observa upstream em A. `NET_CAPABILITY_VALIDATED` indica validação de internet no A, não prova individualmente que B conseguiu navegar.
6. O operador pode ativar dados móveis/upstream sem desligar o hotspot; a associação de B deve permanecer, conforme suporte do Android/OEM/operadora.

O KhaLinux não chama APIs ocultas de tethering nem desliga o hotspot do sistema ao encerrar o portal. O operador desliga o hotspot nas Configurações. Se a interface/IPv4 for ambígua, o portal não inicia.

## Máquina de estados

1. `IDLE` → operador solicita hotspot.
2. `REQUESTING_HOTSPOT` → API Android solicitada, com timeout de 30 segundos.
3. `DISCOVERING_INTERFACE` → callback `onStarted` recebido; o controlador procura a interface e IPv4 criados/alterados pela sessão.
4. `HOTSPOT_READY` → somente quando uma interface Wi-Fi candidata e um IPv4 novo são identificados sem ambiguidade.
5. `ERROR` → falha Android, timeout, inventário inicial indisponível, nenhuma interface candidata ou mais de uma candidata.
6. `IDLE` → parada explícita, parada do Android ou cancelamento.

## Correlação da interface

- O controlador captura o conjunto de interfaces Wi-Fi/IPv4 antes de solicitar o hotspot.
- Depois de `onStarted`, observa diferenças durante até 10 segundos, em intervalos de 500 ms.
- Só aceita exatamente um par interface/IPv4 novo.
- Se não houver candidato ou houver ambiguidade, fecha a reserva e não inicia o servidor HTTP.
- A identificação usa nomes de interface Wi-Fi reconhecíveis pelo Android/fabricante; se um fabricante usar nomenclatura diferente, o comportamento é falhar de forma explícita, não escolher uma interface arbitrária.

## Concorrência e encerramento

- Cada tentativa recebe uma geração própria.
- Start duplicado é ignorado enquanto há solicitação ou reserva ativa.
- Stop durante start invalida a geração e remove o timeout; callback tardio fecha a reserva recebida sem reativar a sessão.
- Timeout invalida a tentativa; callbacks tardios são descartados.
- Stop remove tarefas de descoberta, invalida callbacks e fecha a reserva.
- Callback `onStopped` só altera estado quando pertence à reserva ainda ativa.

## Encerramento inesperado do portal

Se o servidor encerrar por expiração, perda da interface ou falha inesperada enquanto o hotspot estiver ativo, o callback de endereço nulo faz o ViewModel liberar a reserva do hotspot e apresentar estado recuperável. O encerramento solicitado pelo operador segue o fluxo normal de parada, sem classificar a ação como falha.

## Limitações conhecidas

- Alguns fabricantes não expõem a interface AP de forma identificável por nome/IP. Nesses casos, o portal não inicia.
- A API LocalOnlyHotspot cria uma rede local sem Internet.
- Banda não é inferida. O campo permanece nulo quando a API disponível não fornece um valor utilizável.
- Nenhum teste, build ou compilação foi executado nesta implementação; as verificações permanecem para a fase final.
