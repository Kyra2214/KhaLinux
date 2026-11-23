# LAB-2 — Inventário e conectividade Android

**Estado:** implementação concluída; validação final pendente.  
**Branch:** `plan/laboratorio-operacional`  
**Próximo módulo:** LAB-3 — RED Mobile.

## Arquitetura implementada

### Inventário local e persistência

- `AndroidDeviceInventory` coleta informações do aparelho operador com APIs Android: fabricante, modelo, build, Android/SDK, ABI, interface ativa, endereços, rota padrão, DNS e capacidades.
- Não coleta IMEI, Android ID, endereço MAC ou SSID.
- `LabAssetStore` persiste ativos em arquivos Properties com escrita atômica.
- O ativo local usa o identificador lógico `local-android`; a primeira observação é preservada e a última observação é atualizada.

### Descoberta Wireless debugging

`AdbMdnsDiscovery` consulta os serviços DNS-SD:

- `_adb-tls-pairing._tcp.` — endpoint e porta temporária de pareamento.
- `_adb-tls-connect._tcp.` — endpoint e porta de conexão após parear.

A descoberta adquire um MulticastLock e libera listeners/lock ao encerrar. Há deduplicação de resolução e callbacks para serviço encontrado, perdido ou falha. Se o hotspot/roteador bloquear mDNS, o RED também aceita endpoint manual IP/porta.

### Cliente ADB

- Pacote Debian ARM64 `adb` 1:34.0.5-12~bpo12+1, compatível com o comando `adb pair`.
- O pacote está no catálogo e na lista base do RootFS novo.
- O RootFS builder fixa `bookworm-backports` no snapshot Debian `20251122T000000Z`, instala a versão exata e gera o lock de versões na próxima construção.
- Para RootFS já instalado, o manifesto do pacote declara suite/URI; `PackageInstaller` cria a fonte APT fixada antes de atualizar o índice e instalar a versão exata.
- O pacote permanece `unverified` até a validação final ARM64/PRoot.
- O transporte integrado é Wireless debugging por TCP. Não presume USB nem root no aparelho.

O `AdbClient` mantém `adb nodaemon server` em uma sessão PRoot de longa duração na porta 5038. As chamadas seguintes reutilizam o servidor. O home persistente é `/home/khalinux`, onde ficam `~/.android/adbkey` e `adbkey.pub`. Ao encerrar o Service, o servidor é cancelado antes do runtime.

O probe de capacidades informa se o ADB está instalado, a versão retornada, se `adb help` anuncia `pair HOST[:PORT] [PAIRING CODE]` e se a chave pública/privada do host existe.

### Operações reais registradas no catálogo

| Operação | Comando ADB | Resultado |
|---|---|---|
| Parear | `adb pair endpoint código` | Saída real de pareamento |
| Conectar | `adb connect endpoint` | Estado da conexão |
| Desconectar | `adb disconnect endpoint` | Resultado de desconexão |
| Dispositivos | `adb devices -l` | Serial, estado e descritores |
| Propriedades | `adb -s serial shell getprop` | Build, modelo, ABI e propriedades selecionadas |
| Interfaces | `adb -s serial shell ip -j address show` | Interfaces/endereço |
| Rotas | `adb -s serial shell ip -j route show` | Rotas |
| Pacotes | `adb -s serial shell pm list packages -3` | Pacotes de terceiros |
| Logs | `adb -s serial logcat -d -t 500` | Até 500 linhas recentes |
| Bateria | `adb -s serial shell dumpsys battery` | Estado reportado pelo Android |

O catálogo não registra código de pareamento nos metadados da execução. Os parâmetros são validados antes de formar os argumentos; cada argumento é escapado individualmente.

### Ativos remotos

`AdbDeviceInventory` interpreta a saída real de `adb devices -l` e `getprop`. O ID persistente do ativo é um hash SHA-256 truncado do serial; o serial necessário ao ADB fica nos atributos locais do ativo. Estado `device`, `offline` e `unauthorized` é preservado. Um ativo que deixa de aparecer na listagem passa a `not-listed`, sem apagar seu histórico ou alterar sua última observação.

### Integração RED/BLUE

- `RedViewModel`: inventário local/remoto, capacidades ADB, descoberta mDNS, fallback manual e ações de parear/conectar/desconectar.
- `LabOperationEngine`: executa operações com executor ADB pelo mesmo pipeline RootFS, com sessão, fila, timeout, cancelamento, stdout/stderr, exit code, duração e evidências.
- `BlueViewModel`: recebe eventos operacionais persistidos e atualizações ao vivo, ordenados por ocorrência.
- O layout final para controlar essas ações e visualizar evidências está reservado ao LAB-9.

## Dependências e pendências

- ADB precisa estar instalado. Em RootFS existente, o instalador injeta a fonte backports fixada ao instalar o pacote; em RootFS novo, ADB entra na base.
- O artefato RootFS atual não foi reconstruído nesta etapa. O lock de versões e manifesto do artefato atual permanecem ligados ao artefato anterior; o próximo build final vai gerar novo pacote, lock, manifesto e release tag.
- mDNS pode ser filtrado pelo hotspot ou roteador; o endpoint manual cobre esse caso.
- O suporte a IPv6 com escopo depende do comportamento do ADB e da rede local.
- O componente auxiliar Android permanece condicional: só será criado se uma operação futura exigir API não exposta por ADB.
- Nenhum teste, build, compilação, auditoria ou teste físico foi executado. A validação do pacote, do servidor persistente, mDNS, pareamento, conexão e inventário com dois aparelhos foi transferida para LAB-10.

## Critério de implementação

O código está conectado de ponta a ponta: descoberta/entrada de endpoint → pareamento → conexão → consulta real → ativo persistido → evento/evidência no motor compartilhado. O estado de validação permanece separado e pendente.
